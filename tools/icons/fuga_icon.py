"""Redraws the Fuga cell (column 6) of skill_wheel.png in the same language as the other skill icons.

Frame: the same frame as every other skill (taken from Earth Drill, column 2, for all three states) with its hue
turned to amber, so only the colour tells Fuga apart. Symbol: one small fire arrow centred in the tile with a clear
margin to the frame, a single hue in five tones, no outline, hard pixel edges.
usage: python fuga_icon.py <skill_wheel.png> [preview.png] [--preview-only]"""
import sys, colorsys
from PIL import Image, ImageDraw

BG = (22, 19, 28, 255)
DARK, RED, ORANGE, LIGHT, WHITE = (122, 38, 18), (217, 72, 31), (255, 138, 42), (255, 194, 92), (255, 240, 200)
FRAME_SOURCE, FUGA, HUE_TURN = 2, 6, 20 / 360  # Earth Drill's frame, turned from orange-red to amber


def frame(sheet, row):
    cell = sheet.crop((FRAME_SOURCE * 40, row * 40, FRAME_SOURCE * 40 + 40, row * 40 + 40))
    out = Image.new('RGBA', (40, 40))
    for y in range(40):
        for x in range(40):
            r, g, b, a = cell.getpixel((x, y))
            corner = (x < 8 or x > 31) and (y < 8 or y > 31)   # the four corner squares belong to the frame
            if 5 <= x < 35 and 5 <= y < 35 and not corner:
                out.putpixel((x, y), BG)       # empty interior; the symbol is laid on top
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s > .25:                        # only the coloured frame; dark rims and rivets stay as they are
                r, g, b = (round(c * 255) for c in colorsys.hsv_to_rgb((h + HUE_TURN) % 1, s, v))
            out.putpixel((x, y), (r, g, b, a))
    return out


def fire_arrow():
    """Sukuna's flame arrow: a long white-hot point, two flame barbs swept back, a tapering fire trail and a few
    broken embers behind it, flying up and to the right. Drawn large, then snapped to five tones of one hue."""
    import math
    from PIL import ImageDraw as D
    S = 8
    red, orange, amber, yellow, white = (209, 42, 24), (255, 110, 26), (255, 170, 40), (255, 226, 80), (255, 250, 214)
    u, n = (1 / math.sqrt(2), -1 / math.sqrt(2)), (1 / math.sqrt(2), 1 / math.sqrt(2))
    big = Image.new('RGBA', (40 * S, 40 * S), (0, 0, 0, 0))
    d = D.Draw(big)
    at = lambda t, s: ((8.2 + u[0] * t + n[0] * s) * S, (31.8 + u[1] * t + n[1] * s) * S)
    poly = lambda pts, c: d.polygon([at(t, s) for t, s in pts], fill=c + (255,))
    # Embers trailing behind, broken like the reference.
    for t0, t1 in ((-.5, 1.2), (2.4, 4.2)):
        poly([(t0, -.8), (t1, -.8), (t1, .8), (t0, .8)], red)
    # Fire trail tapering back from the head.
    poly([(17, -3.4), (5, -.8), (5, .8), (17, 3.4)], red)
    poly([(18, -2.3), (7.5, -.5), (7.5, .5), (18, 2.3)], orange)
    # Two barbs of flame swept back from behind the point, curling out like tongues.
    for side in (-1, 1):
        poly([(24.5, side * 1.4), (17.5, side * 5.0), (11.5, side * 9.2), (14.5, side * 4.4), (13, side * 1.4)], red)
        poly([(24.5, side * 1.2), (18.5, side * 3.8), (14.5, side * 6.6), (16.5, side * 2.8), (15.5, side * 1.0)], orange)
    # The point: long and sharp, white-hot at its tip.
    poly([(31, 0), (21, -2.8), (16.5, -1.6), (15.5, 0), (16.5, 1.6), (21, 2.8)], amber)
    poly([(30.5, 0), (22.5, -1.7), (18.5, -1.0), (18, 0), (18.5, 1.0), (22.5, 1.7)], yellow)
    poly([(30, 0), (25, -.7), (23, 0), (25, .7)], white)
    small = big.resize((40, 40), Image.BOX)
    tones = [red, orange, amber, yellow, white]
    out = Image.new('RGBA', (40, 40), (0, 0, 0, 0))
    for y in range(40):
        for x in range(40):
            r, g, b, a = small.getpixel((x, y))
            if a < 120: continue
            out.putpixel((x, y), min(tones, key=lambda p: (p[0] - r) ** 2 + (p[1] - g) ** 2 + (p[2] - b) ** 2) + (255,))
    return out


def arrow():
    art = Image.new('RGBA', (40, 40), (0, 0, 0, 0))
    d = ImageDraw.Draw(art)
    px = lambda pts, c: [art.putpixel(p, c + (255,)) for p in pts]
    # Tail: a full tongue of fire streaming back, banded dark to bright like the other fire icon.
    d.polygon([(17, 22), (12, 22), (9, 21), (10, 24), (8, 26), (11, 27), (9, 30), (12, 29), (13, 31), (14, 28), (17, 27)], fill=RED)
    d.polygon([(16, 23), (12, 24), (10, 26), (12, 27), (12, 29), (14, 27), (16, 26)], fill=ORANGE)
    d.polygon([(16, 24), (14, 25), (14, 26), (15, 26)], fill=LIGHT)
    px([(9, 21), (8, 26), (9, 30), (13, 31)], DARK)
    # Shaft: three pixels wide, a bright core stripe down the middle.
    d.line([(15, 25), (25, 15)], fill=ORANGE, width=3)
    d.line([(16, 24), (25, 15)], fill=LIGHT, width=1)
    px([(18, 19), (21, 23), (22, 22)], RED)
    # Broadhead: a hard right-angled point up and to the right, white-hot along its outer edges.
    d.polygon([(30, 9), (21, 9), (30, 18)], fill=LIGHT)
    d.polygon([(28, 11), (23, 11), (28, 16)], fill=ORANGE)
    d.line([(21, 9), (30, 18)], fill=RED)
    d.line([(22, 9), (30, 9)], fill=WHITE)
    d.line([(30, 9), (30, 17)], fill=WHITE)
    return art


def main():
    path = sys.argv[1]
    sheet = Image.open(path).convert('RGBA')
    art = fire_arrow()
    print('art bbox', art.getbbox(), 'pixels', sum(1 for y in range(40) for x in range(40) if art.getpixel((x, y))[3]))
    for row in range(3):
        cell = frame(sheet, row)
        cell.alpha_composite(art)
        sheet.paste(cell, (FUGA * 40, row * 40))
    if len(sys.argv) > 2:
        sheet.crop((0, 0, 280, 120)).resize((1680, 720), Image.NEAREST).save(sys.argv[2])
        if '--preview-only' in sys.argv: return
    sheet.save(path)


main()
