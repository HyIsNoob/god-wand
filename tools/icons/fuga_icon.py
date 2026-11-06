"""Redraws the Fuga cell (column 6) of skill_wheel.png as pixel art: an arrow made of fire, keeping each state's
frame. usage: python fuga_icon.py <skill_wheel.png> [preview.png]"""
import sys, math
from collections import Counter
from PIL import Image, ImageDraw, ImageFilter

SCALE = 8
OUTLINE = (58, 14, 20)
DEEP, RED, ORANGE, YELLOW, CORE = (170, 30, 24), (232, 70, 28), (255, 146, 38), (255, 214, 82), (255, 250, 214)
U = (1 / math.sqrt(2), -1 / math.sqrt(2))   # along the arrow, toward the top right
N = (1 / math.sqrt(2), 1 / math.sqrt(2))    # across it


def at(t, s, origin=(9.0, 31.0)):
    """Scaled icon coordinates for distance t along the arrow and s across it."""
    return ((origin[0] + U[0] * t + N[0] * s) * SCALE, (origin[1] + U[1] * t + N[1] * s) * SCALE)


def poly(d, points, colour):
    d.polygon([at(t, s) for t, s in points], fill=colour + (255,))


def zigzag(t0, t1, inner, outer, step=2.0):
    """A shaft outline whose edges flicker like licking flame."""
    top, bottom, k, t = [], [], 0, t0
    while t <= t1 + 1e-6:
        top.append((t, inner if k % 2 else outer))
        bottom.append((t, -(outer if k % 2 else inner)))
        t += step; k += 1
    return top + bottom[::-1]


def draw_arrow(size=40):
    art = Image.new('RGBA', (size * SCALE, size * SCALE), (0, 0, 0, 0))
    d = ImageDraw.Draw(art)
    # Tail: three flame tongues streaming back and out.
    for side in (-1, 1):
        poly(d, [(9, side * .6), (1, side * 5.6), (4, side * 2.2), (-1.5, side * 3.4), (3.5, side * .4)], DEEP)
        poly(d, [(10, side * .5), (3, side * 3.8), (5.5, side * 1.4), (2, side * 1.6), (5.5, side * .2)], RED)
    poly(d, [(8, 1.6), (-2.5, 0), (8, -1.6)], RED)
    poly(d, [(9, .9), (1.5, 0), (9, -.9)], ORANGE)
    # Shaft: a burning rod with a bright core, its edges licking.
    poly(d, zigzag(4, 22, 1.7, 2.5), ORANGE)
    poly(d, [(6, -.85), (22, -.85), (22, .85), (6, .85)], YELLOW)
    poly(d, [(13, -.35), (22, -.35), (22, .35), (13, .35)], CORE)
    # Head: a flame-shaped broadhead, white-hot at the point.
    poly(d, [(31, 0), (19, -5.6), (21.8, 0), (19, 5.6)], RED)
    poly(d, [(30, 0), (20.5, -3.9), (22.6, 0), (20.5, 3.9)], ORANGE)
    poly(d, [(29, 0), (22.3, -2.2), (23.6, 0), (22.3, 2.2)], YELLOW)
    poly(d, [(28.2, 0), (24.6, -.9), (25.2, 0), (24.6, .9)], CORE)
    # A dark rim round the whole silhouette so it reads on the dark tile.
    alpha = art.split()[3]
    rim = alpha.filter(ImageFilter.MaxFilter(2 * SCALE + 1))
    base = Image.new('RGBA', art.size, (0, 0, 0, 0))
    base.paste(Image.new('RGBA', art.size, OUTLINE + (255,)), mask=rim)
    base.alpha_composite(art)
    small = base.resize((size, size), Image.BOX)
    palette = [OUTLINE, DEEP, RED, ORANGE, YELLOW, CORE]
    out = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    for y in range(size):
        for x in range(size):
            r, g, b, a = small.getpixel((x, y))
            if a < 120: continue
            c = min(palette, key=lambda p: (p[0] - r) ** 2 + (p[1] - g) ** 2 + (p[2] - b) ** 2)
            out.putpixel((x, y), c + (255,))
    return out


def main():
    path = sys.argv[1]
    sheet = Image.open(path).convert('RGBA')
    arrow = draw_arrow()
    for row in range(3):
        box = (240, row * 40, 280, row * 40 + 40)
        cell = sheet.crop(box)
        inner = [cell.getpixel((x, y)) for y in range(8, 32) for x in range(8, 32)]
        bg = Counter(inner).most_common(1)[0][0]
        # Clear the old art inside the frame, then lay the new silhouette on the background.
        for y in range(5, 35):
            for x in range(5, 35):
                cell.putpixel((x, y), bg)
        cell.alpha_composite(arrow)
        sheet.paste(cell, box[:2])
    sheet.save(path)
    if len(sys.argv) > 2:
        sheet.crop((160, 0, 280, 120)).resize((960, 960), Image.NEAREST).save(sys.argv[2])


main()
