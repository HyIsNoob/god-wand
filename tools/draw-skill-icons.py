"""Author the wheel's 1:1 pixel sprites. Optional tooling: Python + Pillow."""
from pathlib import Path
import math
from PIL import Image, ImageDraw

COLORS = [(139, 216, 255), (72, 152, 255), (255, 100, 54),
          (224, 62, 112), (176, 135, 89), (180, 99, 236)]



def drill(base, ridge, groove):
    im = Image.new('RGBA', (24, 24))
    # One constant-pitch helix on a tapered cone, shared by both drill icons.
    for z in range(19):
        radius = 7.5 * (1 - z / 18)
        for x in range(4, 21):
            if abs(x - 12) > radius:
                continue
            side = (x - 12) / radius if radius else 0
            phase = (z / 6 + math.asin(side) / math.pi) % 1
            color = ridge if phase < .25 else groove if phase < .43 else base
            shade = 1.08 - (side + 1) * .17
            im.putpixel((x, z + 4), tuple(min(255, round(c * shade)) for c in color) + (255,))
    return im


def icon(mode):
    im = Image.new('RGBA', (24, 24))
    d = ImageDraw.Draw(im)
    color = COLORS[mode]
    dark = tuple(c // 3 for c in color)
    light = tuple(min(255, c // 2 + 125) for c in color)
    # A few separated motes, with hard pixel edges rather than a blur.
    if mode != 5:
        for x, y in [(3, 4), (20, 7), (4, 19), (18, 20)]:
            d.point((x, y), fill=dark)
    if mode == 0:  # A player enclosed by a pale-blue protective sphere.
        d.ellipse((2, 1, 21, 22), outline=color)
        d.arc((4, 1, 19, 22), 200, 290, fill=light, width=2)
        d.rectangle((10, 5, 13, 8), fill=(214, 237, 249))
        d.rectangle((9, 10, 14, 15), fill=(107, 168, 207))
        d.rectangle((7, 10, 8, 15), fill=light)
        d.rectangle((15, 10, 16, 15), fill=light)
        d.rectangle((9, 16, 11, 19), fill=(90, 124, 166))
        d.rectangle((13, 16, 14, 19), fill=(90, 124, 166))
        d.arc((2, 15, 21, 22), 0, 180, fill=light)
    elif mode == 1:  # White energy core radiating out into blue, without a wet highlight.
        d.ellipse((2, 2, 21, 21), fill=(22, 65, 157))
        d.ellipse((3, 3, 20, 20), fill=(36, 105, 234))
        d.ellipse((5, 5, 18, 18), fill=(53, 157, 255))
        d.ellipse((7, 7, 16, 16), fill=(120, 209, 255))
        d.ellipse((9, 9, 14, 14), fill=(213, 246, 255))
        d.rectangle((10, 10, 13, 13), fill=(255, 255, 255))
        d.line((11, 5, 11, 8), fill=(191, 237, 255))
        d.line((15, 12, 18, 12), fill=(169, 225, 255))
        d.line((5, 14, 8, 13), fill=(169, 225, 255))
        d.line([(0, 17), (2, 15)], fill=color)
        d.line([(21, 5), (23, 3)], fill=(99, 190, 255))
    elif mode == 2:  # Red-hot spiral drill pointing down.
        d.polygon([(6, 5), (7, 0), (10, 4), (15, 1), (17, 5)], fill=(255, 83, 37))
        im.alpha_composite(drill((235, 65, 30), (255, 197, 78), (116, 28, 22)))
        d.point((3, 14), fill=color)
        d.point((20, 11), fill=(255, 193, 73))
    elif mode == 3:  # Black hole with a deep red-pink accretion rim.
        d.ellipse((2, 2, 21, 21), fill=(72, 15, 43))
        d.ellipse((4, 3, 19, 20), fill=(13, 6, 16), outline=color, width=2)
        d.arc((4, 3, 19, 20), 195, 310, fill=(255, 114, 164))
        d.line([(0, 16), (6, 13), (16, 10), (23, 7)], fill=(165, 30, 73), width=3)
        d.line([(1, 15), (7, 12), (17, 9), (22, 7)], fill=(244, 73, 130))
        d.ellipse((8, 6, 16, 15), fill=(7, 5, 11))
        d.line([(4, 17), (11, 16), (18, 13), (23, 10)], fill=(202, 43, 103))
    elif mode == 4:  # Horizontal drill made of ordinary brown earth.
        d.line((0, 5, 5, 5), fill=(245, 218, 165))
        d.line((1, 11, 5, 11), fill=(255, 237, 196))
        d.line((0, 18, 5, 18), fill=(222, 185, 129))
        im.alpha_composite(drill((176, 135, 89), (239, 205, 151), (103, 72, 48)).transpose(Image.Transpose.ROTATE_90))
        d.point((1, 14), fill=(185, 142, 94))
    else:  # Neutral black blade, diamond-ended guard and three-pronged pommel.
        d.polygon([(12, 0), (14, 5), (14, 15), (10, 15), (10, 5)], fill=(18, 18, 18))
        d.line([(12, 0), (10, 5), (10, 14)], fill=(98, 98, 98))
        d.line([(12, 2), (13, 6), (13, 14)], fill=(43, 43, 43))
        d.rectangle((11, 5, 12, 14), fill=(12, 12, 12))
        d.line((5, 16, 19, 16), fill=(49, 49, 49))
        d.polygon([(3, 15), (5, 14), (7, 16), (5, 17)], fill=(83, 83, 83))
        d.polygon([(17, 16), (19, 14), (21, 15), (19, 17)], fill=(83, 83, 83))
        d.rectangle((10, 15, 14, 17), fill=(32, 32, 32))
        d.rectangle((11, 18, 12, 20), fill=(28, 28, 28))
        d.line((11, 18, 11, 20), fill=(58, 58, 58))
        d.polygon([(10, 21), (9, 20), (9, 22), (12, 23), (15, 22), (15, 20), (13, 21)], fill=(77, 77, 77))
    return im


def build():
    atlas = Image.new('RGBA', (240, 120))
    for mode in range(len(COLORS)):
        for state in range(3):
            tile = Image.new('RGBA', (40, 40))
            d = ImageDraw.Draw(tile)
            edge = [(3, 0), (36, 0), (39, 3), (39, 36), (36, 39), (3, 39), (0, 36), (0, 3)]
            d.polygon(edge, fill=(27, 25, 30))
            d.line(edge + [edge[0]], fill=(12, 11, 15))
            trims = [
                [(110, 161, 190), (169, 221, 246), (228, 246, 255)],
                [(46, 102, 181), (71, 149, 237), (147, 211, 255)],
                [(177, 84, 37), (239, 137, 57), (255, 204, 128)],
                [(139, 41, 99), (206, 63, 135), (248, 145, 207)],
                [(113, 108, 117), (210, 164, 80), (236, 222, 179)],
                [(132, 63, 185), (184, 90, 237), (211, 142, 255)],
            ]
            trim = trims[mode][state]
            d.rectangle((2, 2, 37, 37), outline=trim, width=2)
            highlight = (163, 153, 160) if state == 0 else (255, 237, 176)
            if mode < 4:
                highlight = tuple(min(255, c + 40) for c in trim)
            elif mode == 5:
                highlight = (187, 124, 225) if state == 0 else (224, 180, 255)
            d.line([(4, 4), (35, 4), (35, 5)], fill=highlight)
            d.line([(4, 35), (35, 35), (35, 6)], fill=(50, 44, 55))
            d.rectangle((5, 5, 34, 34), fill=(22 + state * 3, 19 + state * 2, 28 + state * 4))
            for x, y in [(7, 2), (28, 3), (36, 11), (2, 25), (12, 36), (30, 36)]:
                d.point((x, y), fill=(61, 56, 65))
            color = COLORS[mode]
            for x, y in [(4, 4), (33, 4), (4, 33), (33, 33)]:
                d.rectangle((x, y, x + 2, y + 2), fill=color)
                d.point((x, y), fill=tuple(min(255, c + 45) for c in color))
            tile.alpha_composite(icon(mode), (8, 8))
            atlas.alpha_composite(tile, (mode * 40, state * 40))
    assert atlas.size == (240, 120)
    out = Path(__file__).resolve().parents[1] / 'common/src/main/resources/assets/godwand/textures/gui/skill_wheel.png'
    out.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(out)
    print(out)
    sectors = Image.new('RGBA', (224 * 7, 224))
    sector_pen = ImageDraw.Draw(sectors)
    for y in range(-112, 112, 2):
        for x in range(-112, 112, 2):
            radius = math.hypot(x + 1, y + 1)
            if not 48 <= radius <= 112:
                continue
            angle = math.atan2(y + 1, x + 1) + math.pi / 2
            mode = math.floor(angle / (math.tau / 6) + .5) % 6
            if abs(math.remainder(angle - mode * math.tau / 6, math.tau)) > math.pi / 6 - .025:
                continue
            idle = (18, 17, 20, 64)
            hover = (96, 87, 76, 105)
            for column, paint in [(6, idle), (mode, hover)]:
                sector_pen.rectangle((column * 224 + x + 112, y + 112,
                                                   column * 224 + x + 113, y + 113), fill=paint)
    sectors.save(out.with_name('skill_sectors.png'))


if __name__ == '__main__':
    build()
