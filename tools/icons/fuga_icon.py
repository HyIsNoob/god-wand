"""Redraws the Fuga cell (column 6) of skill_wheel.png as flat pixel art: a fire arrow silhouette in four colours,
keeping each state's frame. usage: python fuga_icon.py <skill_wheel.png> [preview.png]"""
import sys, math
from collections import Counter
from PIL import Image, ImageDraw

SCALE = 8
HEAD, SHAFT, FLAME, EMBER = (255, 226, 140), (196, 74, 22), (255, 138, 31), (205, 48, 26)
U = (1 / math.sqrt(2), -1 / math.sqrt(2))   # along the arrow, toward the top right
N = (1 / math.sqrt(2), 1 / math.sqrt(2))    # across it


def at(t, s, origin=(10.5, 29.5)):
    """Icon pixel coordinates (scaled) for distance t along the arrow and s across it."""
    return ((origin[0] + U[0] * t + N[0] * s) * SCALE, (origin[1] + U[1] * t + N[1] * s) * SCALE)


def draw_arrow(size=40):
    big = Image.new('RGBA', (size * SCALE, size * SCALE), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    # Flame tongues trailing off the tail on both sides, then the shaft over them, then the broadhead.
    for side in (-1, 1):
        d.polygon([at(10, 0), at(0, side * 5.2), at(4, side * 1.8), at(-2, side * 2.6), at(3, 0)], fill=EMBER)
        d.polygon([at(12, 0), at(3, side * 3.4), at(6, side * 1.1), at(1, side * 1.3), at(5, 0)], fill=FLAME)
    d.polygon([at(2, -1.6), at(21, -1.6), at(21, 1.6), at(2, 1.6)], fill=SHAFT)
    d.polygon([at(30, 0), at(19, -4.6), at(21.5, 0), at(19, 4.6)], fill=HEAD)
    small = big.resize((size, size), Image.BOX)
    # Snap to the palette and a hard alpha edge: crisp pixel art, no soft fringes.
    palette = [HEAD, SHAFT, FLAME, EMBER]
    out = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    for y in range(size):
        for x in range(size):
            r, g, b, a = small.getpixel((x, y))
            if a < 110: continue
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
