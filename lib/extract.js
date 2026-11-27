const UA = 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36';

function host(u) { return new URL(u).hostname.replace(/^www\./, '').replace(/^m\./, '').toLowerCase(); }
const is = (h, d) => h === d || h.endsWith('.' + d);

async function getJson(url, opts = {}) {
  const r = await fetch(url, { headers: { 'user-agent': UA, accept: 'application/json', ...(opts.headers || {}) }, signal: AbortSignal.timeout(15000), redirect: 'follow' });
  if (!r.ok) throw new Error('HTTP ' + r.status);
  return r.json();
}
async function getText(url) {
  const r = await fetch(url, { headers: { 'user-agent': UA, 'accept-language': 'en-US,en;q=0.9' }, signal: AbortSignal.timeout(15000), redirect: 'follow' });
  if (!r.ok) throw new Error('HTTP ' + r.status);
  return { text: await r.text(), finalUrl: r.url };
}

async function tiktok(url) {
  const j = await getJson('https://www.tikwm.com/api/?hd=1&url=' + encodeURIComponent(url));
  if (j.code !== 0 || !j.data) throw new Error(j.msg || 'tiktok failed');
  const d = j.data;
  const abs = (p) => (p && p.startsWith('http') ? p : p ? 'https://www.tikwm.com' + p : null);
  const out = [];
  if (abs(d.hdplay)) out.push({ label: 'جودة عالية', url: abs(d.hdplay), ext: 'mp4' });
  if (abs(d.play)) out.push({ label: 'جودة عادية', url: abs(d.play), ext: 'mp4' });
  if (abs(d.music)) out.push({ label: 'الصوت فقط', url: abs(d.music), ext: 'mp3' });
  if (!out.length) throw new Error('no media');
  return { platform: 'TikTok', title: d.title || 'TikTok', thumb: abs(d.cover), items: out };
}

async function twitter(url) {
  const m = new URL(url).pathname.match(/\/status(?:es)?\/(\d+)/);
  if (!m) throw new Error('bad tweet url');
  const id = m[1];
  try {
    const j = await getJson('https://api.fxtwitter.com/status/' + id);
    const t = j.tweet;
    const vids = (t && t.media && t.media.videos) || [];
    if (vids.length) return { platform: 'X', title: (t.text || 'X video').slice(0, 100), thumb: vids[0].thumbnail_url, items: vids.map((v, i) => ({ label: vids.length > 1 ? 'فيديو ' + (i + 1) : 'فيديو', url: v.url, ext: 'mp4' })) };
  } catch (e) { /* fall through to syndication */ }
  const token = ((Number(id) / 1e15) * Math.PI).toString(36).replace(/(0+|\.)/g, '');
  const j = await getJson('https://cdn.syndication.twimg.com/tweet-result?id=' + id + '&token=' + token + '&lang=en');
  const media = (j.mediaDetails || []).filter((x) => x.video_info);
  if (!media.length) throw new Error('no video in post');
  const items = media.map((x, i) => {
    const mp4 = x.video_info.variants.filter((v) => v.content_type === 'video/mp4').sort((a, b) => (b.bitrate || 0) - (a.bitrate || 0))[0];
    return { label: media.length > 1 ? 'فيديو ' + (i + 1) : 'فيديو', url: mp4.url, ext: 'mp4' };
  });
  return { platform: 'X', title: (j.text || 'X video').slice(0, 100), thumb: media[0].media_url_https, items };
}

function metaVideo(html, base) {
  const pick = (re) => { const m = html.match(re); return m ? decode(m[1]) : null; };
  const vid = pick(/<meta[^>]+property=["']og:video(?::secure_url|:url)?["'][^>]+content=["']([^"']+)["']/i)
    || pick(/<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:video(?::secure_url|:url)?["']/i)
    || pick(/"(?:hd_src|sd_src|playable_url_quality_hd|playable_url)"\s*:\s*"([^"]+)"/);
  const title = pick(/<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']*)["']/i) || 'Video';
  const thumb = pick(/<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']/i);
  if (!vid) return null;
  return { title, thumb, url: new URL(vid.replace(/\\\//g, '/'), base).href };
}
function decode(s) { return s.replace(/&amp;/g, '&').replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/\\u0025/g, '%').replace(/\\u0026/g, '&'); }

async function viaPage(url, name) {
  const { text, finalUrl } = await getText(url);
  const v = metaVideo(text, finalUrl);
  if (!v) throw new Error('no video found');
  return { platform: name, title: v.title, thumb: v.thumb, items: [{ label: 'فيديو', url: v.url, ext: 'mp4' }] };
}

async function reddit(url) {
  const u = new URL(url);
  const j = await getJson('https://www.reddit.com' + u.pathname.replace(/\/$/, '') + '.json?raw_json=1');
  const post = j[0].data.children[0].data;
  const rv = (post.secure_media && post.secure_media.reddit_video) || (post.crosspost_parent_list && post.crosspost_parent_list[0] && post.crosspost_parent_list[0].secure_media && post.crosspost_parent_list[0].secure_media.reddit_video);
  if (!rv) throw new Error('no video');
  return { platform: 'Reddit', title: post.title, thumb: post.thumbnail && post.thumbnail.startsWith('http') ? post.thumbnail : null, items: [{ label: 'فيديو (قد يكون بدون صوت)', url: rv.fallback_url, ext: 'mp4' }] };
}

async function direct(url) {
  const r = await fetch(url, { method: 'HEAD', headers: { 'user-agent': UA }, signal: AbortSignal.timeout(10000), redirect: 'follow' });
  const ct = r.headers.get('content-type') || '';
  if (/^(video|audio)\//.test(ct)) return { platform: 'رابط مباشر', title: 'Video', thumb: null, items: [{ label: 'تحميل', url, ext: ct.includes('webm') ? 'webm' : ct.startsWith('audio') ? 'mp3' : 'mp4' }] };
  throw new Error('not a media file');
}

class Unsupported extends Error {}

async function extract(url) {
  let u;
  try { u = new URL(url); } catch { throw new Unsupported('bad url'); }
  if (!/^https?:$/.test(u.protocol)) throw new Unsupported('bad url');
  const h = host(url);
  if (is(h, 'youtube.com') || is(h, 'youtu.be')) throw new Unsupported('youtube');
  if (is(h, 'tiktok.com')) return tiktok(url);
  if (is(h, 'twitter.com') || is(h, 'x.com')) return twitter(url);
  if (is(h, 'reddit.com') || is(h, 'redd.it')) return reddit(url);
  if (is(h, 'facebook.com') || is(h, 'fb.watch')) return viaPage(url.replace('://www.', '://m.'), 'Facebook');
  if (is(h, 'instagram.com')) return viaPage(url, 'Instagram');
  try { return await viaPage(url, h); } catch { return direct(url); }
}

module.exports = { extract, Unsupported, metaVideo, host };
