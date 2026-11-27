const { extract, Unsupported } = require('../lib/extract');
module.exports = async (req, res) => {
  res.setHeader('cache-control', 'no-store');
  const url = (req.query && req.query.url || '').toString().trim();
  if (!url || url.length > 2000) return res.status(400).json({ error: 'bad_url' });
  try {
    const r = await extract(url);
    res.status(200).json(r);
  } catch (e) {
    if (e instanceof Unsupported) return res.status(422).json({ error: e.message === 'youtube' ? 'youtube' : 'bad_url' });
    res.status(502).json({ error: 'failed' });
  }
};
