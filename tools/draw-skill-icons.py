"""Author the wheel's 1:1 pixel sprites. Optional tooling: Python + Pillow."""
from pathlib import Path
from PIL import Image, ImageDraw

COLORS = [(139, 216, 255), (72, 152, 255), (255, 100, 54),
          (224, 62, 112), (176, 135, 89), (180, 99, 236)]


def icon(mode):
    im = Image.new('RGBA', (24, 24))
    d = ImageDraw.Draw(im)
    color = COLORS[mode]
    dark = tuple(c // 3 for c in color)
    light = tuple(min(255, c // 2 + 125) for c in color)
    # A few separated motes, with hard pixel edges rather than a blur.
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
    elif mode == 1:  # A shaded blue energy sphere.
        d.ellipse((2, 2, 21, 21), fill=(16, 47, 114), outline=(40, 104, 205))
        d.ellipse((3, 3, 19, 19), fill=(30, 95, 214))
        d.ellipse((4, 4, 17, 17), fill=color)
        d.ellipse((5, 4, 14, 13), fill=(95, 185, 255))
        d.rectangle((7, 5, 11, 7), fill=(181, 229, 255))
        d.rectangle((6, 8, 8, 10), fill=(139, 208, 255))
        d.point((6, 6), fill=(236, 251, 255))
        d.arc((2, 2, 21, 21), 10, 85, fill=(75, 175, 255))
    elif mode == 2:  # Red-hot spiral drill pointing down.
        d.polygon([(6, 5), (7, 0), (10, 4), (15, 1), (17, 5)], fill=(255, 83, 37))
        d.polygon([(5, 5), (18, 5), (19, 8), (16, 15), (12, 23), (8, 16), (4, 9)], fill=(128, 32, 26))
        d.polygon([(6, 5), (17, 5), (17, 10), (14, 17), (12, 22), (8, 14)], fill=(235, 57, 30))
        d.line([(5, 7), (12, 10), (18, 7)], fill=(255, 188, 73), width=2)
        d.line([(7, 12), (12, 15), (16, 12)], fill=(255, 128, 45), width=2)
        d.line([(10, 17), (12, 19), (14, 17)], fill=(255, 209, 113))
        d.line((7, 4, 14, 4), fill=(255, 224, 140))
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
        d.line((0, 5, 5, 5), fill=(144, 99, 65))
        d.line((1, 11, 5, 11), fill=(195, 157, 105))
        d.line((0, 18, 5, 18), fill=(119, 84, 53))
        d.polygon([(7, 4), (14, 7), (23, 12), (14, 18), (7, 20), (4, 16), (4, 8)], fill=(91, 64, 45))
        d.polygon([(7, 5), (14, 8), (22, 12), (13, 16), (7, 18), (5, 14), (5, 8)], fill=color)
        d.polygon([(7, 5), (14, 8), (22, 12), (10, 10), (5, 8)], fill=(211, 179, 130))
        d.line([(7, 5), (10, 12), (7, 18)], fill=(110, 75, 48), width=2)
        d.line([(13, 8), (15, 12), (13, 16)], fill=(126, 89, 56), width=2)
        d.point((1, 14), fill=(185, 142, 94))
    else:  # Purple-black sword inside a flickering purple aura.
        d.line([(4, 18), (2, 13), (4, 6), (8, 2)], fill=(73, 28, 116), width=2)
        d.line([(5, 17), (4, 12), (6, 7)], fill=color)
        d.line([(17, 2), (21, 7), (22, 13), (19, 18)], fill=(103, 40, 164), width=2)
        d.line([(20, 7), (21, 12), (18, 17)], fill=(204, 115, 255))
        d.polygon([(12, 0), (14, 4), (13, 15), (10, 15), (10, 4)], fill=(57, 27, 76))
        d.line((12, 1, 10, 4), fill=(177, 100, 223))
        d.line((13, 4, 12, 14), fill=(150, 70, 193))
        d.line((11, 4, 11, 14), fill=(22, 16, 31))
        d.line([(6, 14), (11, 16), (17, 13)], fill=(30, 20, 40), width=3)
        d.line([(6, 14), (11, 16), (17, 13)], fill=(105, 59, 133))
        d.rectangle((11, 17, 12, 21), fill=(42, 27, 56))
        d.rectangle((10, 22, 13, 23), fill=(102, 55, 136))
    return im


def build():
    atlas = Image.new('RGBA', (240, 120))
    for mode, color in enumerate(COLORS):
        for state in range(3):
            tile = Image.new('RGBA', (40, 40))
            d = ImageDraw.Draw(tile)
            edge = [(3, 0), (36, 0), (39, 3), (39, 36), (36, 39), (3, 39), (0, 36), (0, 3)]
            d.polygon(edge, fill=(27, 25, 30))
            d.line(edge + [edge[0]], fill=(12, 11, 15))
            trim = [(113, 108, 117), (210, 164, 80), (236, 222, 179)][state]
            d.rectangle((2, 2, 37, 37), outline=trim, width=2)
            d.line([(4, 4), (35, 4), (35, 5)], fill=(163, 153, 160) if state == 0 else (255, 237, 176))
            d.line([(4, 35), (35, 35), (35, 6)], fill=(50, 44, 55))
            d.rectangle((5, 5, 34, 34), fill=(22 + state * 3, 19 + state * 2, 28 + state * 4))
            # Chisel marks belong to the stone frame, not the icon silhouette.
            for x, y in [(7, 2), (28, 3), (36, 11), (2, 25), (12, 36), (30, 36)]:
                d.point((x, y), fill=(61, 56, 65))
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


if __name__ == '__main__':
    build()
