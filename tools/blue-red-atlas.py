"""Author the Blue/Red/Purple VFX atlas (1024x1024 RGBA, white tinted per vertex). Optional tooling: Python + numpy + Pillow.
Regions must match BlueRedAtlas.java. The 8x8 texel at (504, 504) stays opaque white: GateDraw's solid quads sample it.
Run: python tools/blue-red-atlas.py  ->  common/src/main/resources/assets/godwand/textures/vfx/blue_red.png
"""
from pathlib import Path
import numpy as np
from PIL import Image

SIZE = 1024
rng = np.random.default_rng(1606)
lum = np.zeros((SIZE, SIZE), np.float32)
alpha = np.zeros((SIZE, SIZE), np.float32)


def tile_noise(w, h, cells_x, cells_y, seed):
    """Smooth value noise that tiles in both directions (cells_x by cells_y lattice)."""
    g = np.random.default_rng(seed).random((cells_y, cells_x)).astype(np.float32)
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    fx, fy = xs / w * cells_x, ys / h * cells_y
    x0, y0 = np.floor(fx).astype(int), np.floor(fy).astype(int)
    tx, ty = fx - x0, fy - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    x0, y0, x1, y1 = x0 % cells_x, y0 % cells_y, (x0 + 1) % cells_x, (y0 + 1) % cells_y
    a, b, c, d = g[y0, x0], g[y0, x1], g[y1, x0], g[y1, x1]
    return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty


def fbm(w, h, base_x, base_y, octaves, seed, ridged=False):
    out = np.zeros((h, w), np.float32)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        n = tile_noise(w, h, base_x * 2 ** o, base_y * 2 ** o, seed + o * 17)
        if ridged:
            n = 1 - np.abs(n * 2 - 1)
        out += n * amp
        total += amp
        amp *= .5
    return out / total


def smooth(x):
    x = np.clip(x, 0, 1)
    return x * x * (3 - 2 * x)


def put(x, y, l, a):
    h, w = a.shape
    lum[y:y + h, x:x + w] = l
    alpha[y:y + h, x:x + w] = a


def radial(w, h):
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    return np.hypot((xs + .5) / w * 2 - 1, (ys + .5) / h * 2 - 1)


# GLOW (0,0,128,128): soft gaussian with a hot centre.
r = radial(128, 128)
put(0, 0, np.ones_like(r), np.exp(-(r * 2.2) ** 2) * .85 + np.exp(-(r * 6) ** 2) * .15)

# STAR (128,0,128,128): four-point glint with a halo.
ys, xs = np.mgrid[0:128, 0:128].astype(np.float32)
dx, dy = np.abs((xs + .5) / 64 - 1), np.abs((ys + .5) / 64 - 1)
star = np.exp(-dx * 30) * np.exp(-dy * 2.2) + np.exp(-dy * 30) * np.exp(-dx * 2.2)
star = np.clip(star + np.exp(-(np.hypot(dx, dy) * 4) ** 2) * .6, 0, 1)
put(128, 0, np.ones_like(star), star)

# BUBBLE (256,0,64,64): a thin soft ring.
r = radial(64, 64)
put(256, 0, np.ones_like(r), np.exp(-((r - .78) / .07) ** 2))

# SHOCK (384,0,128,128): a shockwave ring, sharp outside, soft trailing inside.
r = radial(128, 128)
shock = np.where(r < .92, np.exp(-((r - .92) / .22) ** 2), np.exp(-((r - .92) / .03) ** 2))
put(384, 0, np.ones_like(r), shock * (r < 1))

# LIGHT (320,0,64,64): a strip for beams and lightning; U along, V across: sharp white core, soft falloff.
v = (np.mgrid[0:64, 0:64][0].astype(np.float32) + .5) / 64 * 2 - 1
put(320, 0, np.ones((64, 64), np.float32), np.clip(np.exp(-(v / .12) ** 2) + .55 * np.exp(-(v / .45) ** 2), 0, 1))

# FLAME 0..3 (512+i*128, 0, 128, 256): anime flame tongues, base at the bottom (V=1), tip curling at the top.
for i in range(4):
    w, h = 128, 256
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    t = 1 - ys / h                      # 0 at the base, 1 at the tip
    sway = np.sin(t * (3 + i) + i) * .18 * t ** 1.4
    half = .42 * (1 - t) ** .55 * (.75 + .25 * np.sin(t * 9 + i * 2))
    edge = fbm(w, h, 4, 8, 3, 100 + i) - .5
    d = np.abs((xs + .5) / w - .5 - sway) - half - edge * .12 * (.4 + t)
    body = smooth(-d / .045)
    inner = smooth(-(d + half * .45) / .05)        # the brighter inner flame (two cel tones)
    tip = smooth((1 - t) / .08)
    put(512 + i * 128, 0, .72 + .28 * inner, body * tip * smooth(t / .06 + .2))

# SMOKE 0..3 (i*256, 256, 248, 248): billowing puffs with self shading.
for i in range(4):
    w = h = 248
    r = radial(w, h)
    n = fbm(w, h, 3, 3, 5, 200 + i)
    shape = smooth((1 - r * (1.05 + .5 * (n - .5)) - .08) / .25)
    shade = fbm(w, h, 4, 4, 4, 300 + i)
    ys = np.mgrid[0:h, 0:w][0].astype(np.float32) / h
    put(i * 256, 256, np.clip(.62 + .5 * shade - .25 * ys, .35, 1), shape * (.75 + .25 * n))

# SURFACE (0,512,512,256): turbulent swirling plasma, tiling horizontally (two periods side by side) and vertically.
n1 = fbm(512, 256, 6, 3, 6, 400, ridged=True)
n2 = fbm(512, 256, 4, 2, 5, 450)
plasma = smooth((n1 * .7 + n2 * .5 - .35) / .65)
put(0, 512, .3 + .7 * plasma, .55 + .45 * plasma)

# INK 0..3 (512, 512+i*64, 512, 64): dry brush strokes along U: thin tail (U=0) to a full head (U=1), bristle streaks, splatter.
for i in range(4):
    w, h = 512, 64
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    u, v = xs / w, (ys + .5) / h * 2 - 1
    width = (.15 + .85 * smooth(u / .7)) * (1 - smooth((u - .93) / .07) * .6)
    bend = .15 * np.sin(u * 3 + i)
    bristle = fbm(w, h, 2, 16, 4, 500 + i)
    core = smooth((width - np.abs(v - bend)) / .08)
    dry = smooth((bristle - .32 + .35 * (1 - u)) / .15)
    splat = np.zeros_like(u)
    for _ in range(18):
        cx, cy, rr = rng.uniform(.75, 1), rng.uniform(-.9, .9), rng.uniform(.01, .03)
        splat = np.maximum(splat, smooth((rr - np.hypot((u - cx) * 8, v - cy) * .12) / .006))
    put(512, 512 + i * 64, .85 + .15 * bristle, np.clip(core * dry + splat, 0, 1))

# SLASH 0..1 (0, 768+i*64, 512, 64): a clean crescent streak with speed lines, head at U=1.
for i in range(2):
    w, h = 512, 64
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    u, v = xs / w, (ys + .5) / h * 2 - 1
    width = .1 + .9 * smooth(u / .85) * (1 - smooth((u - .96) / .04))
    body = smooth((width - np.abs(v)) / .12) * (.35 + .65 * u)
    lines = (np.sin(v * 40 + i * 3) * .5 + .5) ** 6 * smooth((width * 1.5 - np.abs(v)) / .2) * .6
    put(0, 768 + i * 64, np.ones_like(u), np.clip(body + lines * u, 0, 1))

# RAYS (512,768,256,256): a radial burst of thin streaks (impact frames, the blast).
w = h = 256
ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
x, y = (xs + .5) / w * 2 - 1, (ys + .5) / h * 2 - 1
ang, r = np.arctan2(y, x), np.hypot(x, y)
streak = fbm(512, 1, 64, 1, 3, 600)[0]
idx = ((ang + np.pi) / (2 * np.pi) * 511).astype(int)
rays = smooth((streak[idx] - .45) / .2) * smooth((r - .08) / .2) * (1 - smooth((r - .7) / .3))
put(512, 768, np.ones_like(r), rays)

# SOLID texel for GateDraw.quad / gradient.
lum[504:512, 504:512] = 1
alpha[504:512, 504:512] = 1

rgba = np.zeros((SIZE, SIZE, 4), np.uint8)
g = np.clip(lum * 255, 0, 255).astype(np.uint8)
rgba[..., 0] = rgba[..., 1] = rgba[..., 2] = g
rgba[..., 3] = np.clip(alpha * 255, 0, 255).astype(np.uint8)
out = Path(__file__).resolve().parents[1] / 'common/src/main/resources/assets/godwand/textures/vfx/blue_red.png'
out.parent.mkdir(parents=True, exist_ok=True)
Image.fromarray(rgba, 'RGBA').save(out)
print(out)
