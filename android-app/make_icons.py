import sys, os
from PIL import Image, ImageDraw

res = sys.argv[1]
S = 1024  # supersample canvas for a 108dp adaptive layer


def gradient(size):
    img = Image.new('RGB', (size, size))
    px = img.load()
    c1, c2 = (14, 165, 233), (30, 58, 138)
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2 * size - 2)
            px[x, y] = tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))
    return img


def foreground(size=S):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = size / 2
    r = size * 0.285  # circle inside the 66/108 safe zone
    d.ellipse([c - r, c - r, c + r, c + r], fill=(255, 255, 255, 255))
    o = (249, 115, 22, 255)
    w = size * 0.055
    # arrow shaft
    d.rounded_rectangle([c - w, c - r * 0.6, c + w, c + r * 0.0], radius=w, fill=o)
    # arrow head
    h = r * 0.5
    d.polygon([(c - h, c - r * 0.06), (c + h, c - r * 0.06), (c, c + r * 0.42)], fill=o)
    # tray
    d.rounded_rectangle([c - r * 0.5, c + r * 0.6, c + r * 0.5, c + r * 0.6 + w * 1.5], radius=w * 0.75, fill=o)
    return img


def save(img, path, px):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.resize((px, px), Image.LANCZOS).save(path)


fg = foreground()
bg = gradient(S)
dens = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
for name, m in dens.items():
    d = os.path.join(res, 'mipmap-' + name)
    save(fg, os.path.join(d, 'ic_launcher_foreground.png'), int(108 * m))
    full = bg.convert('RGBA')
    full.alpha_composite(fg)
    # legacy square (rounded corners) and round icons, 48dp
    sq = full.crop((int(S * 0.083), int(S * 0.083), int(S * 0.917), int(S * 0.917)))
    px = int(48 * m)
    mask = Image.new('L', sq.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, sq.size[0] - 1, sq.size[1] - 1], radius=int(sq.size[0] * 0.22), fill=255)
    sq.putalpha(mask)
    save(sq, os.path.join(d, 'ic_launcher.png'), px)
    rm = Image.new('L', sq.size, 0)
    ImageDraw.Draw(rm).ellipse([0, 0, sq.size[0] - 1, sq.size[1] - 1], fill=255)
    rd = sq.copy()
    rd.putalpha(rm)
    save(rd, os.path.join(d, 'ic_launcher_round.png'), px)

os.makedirs(os.path.join(res, 'values'), exist_ok=True)
open(os.path.join(res, 'values', 'ic_launcher_background.xml'), 'w').write(
    '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#1E6FB8</color>\n</resources>\n')
# preview
prev = bg.convert('RGBA'); prev.alpha_composite(fg)
m = Image.new('L', (S, S), 0); ImageDraw.Draw(m).rounded_rectangle([int(S*.083), int(S*.083), int(S*.917), int(S*.917)], radius=int(S*.19), fill=255)
out = Image.new('RGBA', (S, S), (240, 249, 255, 255)); out.paste(prev, (0, 0), m)
out.resize((512, 512), Image.LANCZOS).save(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'icon_preview.png'))
