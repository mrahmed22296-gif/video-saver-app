const net = require('net');
const dns = require('dns').promises;
const { Readable } = require('stream');

async function safe(u) {
  const x = new URL(u);
  if (x.protocol !== 'https:' && x.protocol !== 'http:') return false;
  if (x.port && !['80', '443'].includes(x.port)) return false;
  const addrs = net.isIP(x.hostname) ? [{ address: x.hostname }] : await dns.lookup(x.hostname, { all: true });
  return addrs.every(({ address }) => !/^(10\.|127\.|169\.254\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.|0\.|::1|fc|fd|fe80)/i.test(address));
}

module.exports = async (req, res) => {
  const u = (req.query.u || '').toString();
  const n = (req.query.n || 'video').toString().replace(/[^\w\-]+/g, '_').slice(0, 60) || 'video';
  const ext = ['mp4', 'mp3', 'webm'].includes(req.query.e) ? req.query.e : 'mp4';
  try {
    if (!u || !(await safe(u))) return res.status(400).send('bad url');
    const up = await fetch(u, { headers: { 'user-agent': 'Mozilla/5.0', referer: new URL(u).origin + '/' }, redirect: 'follow', signal: AbortSignal.timeout(55000) });
    if (!up.ok || !up.body) return res.status(502).send('upstream error');
    const ct = up.headers.get('content-type') || '';
    if (!/^(video|audio)\/|octet-stream/.test(ct)) return res.status(415).send('not media');
    res.setHeader('content-type', ct);
    res.setHeader('content-disposition', `attachment; filename="${n}.${ext}"`);
    const len = up.headers.get('content-length');
    if (len) res.setHeader('content-length', len);
    Readable.fromWeb(up.body).pipe(res);
  } catch (e) {
    if (!res.headersSent) res.status(502).send('failed');
  }
};
