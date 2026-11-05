"""Renders the Fuga hold loop (bow drawn, waiting to fire). Every component is periodic over L seconds, so the
file loops without a seam: noise is synthesised in the frequency domain (an inverse FFT is periodic), tones use
whole cycles per loop, and crackles/thumps wrap around the end."""
import sys, numpy as np
from scipy.io import wavfile

SR, L = 44100, 8
N = SR * L
t = np.arange(N) / SR
rng = np.random.default_rng(1793)


def periodic_noise(shape):
    f = np.fft.rfftfreq(N, 1 / SR)
    spec = shape(np.maximum(f, 1e-3)) * np.exp(1j * rng.uniform(0, 2 * np.pi, f.size))
    spec[0] = 0
    x = np.fft.irfft(spec, N)
    return x / np.max(np.abs(x))


def band(lo, hi, tilt):
    return lambda f: (f / 300) ** tilt * (1 / (1 + (lo / f) ** 4)) * (1 / (1 + (f / hi) ** 4))


def lfo(cycles, phase=0.0):
    return np.sin(2 * np.pi * cycles * t / L + phase)


def tone(freq, beta=0.0, cycles=1):
    assert abs(freq * L - round(freq * L)) < 1e-9, freq
    return np.sin(2 * np.pi * freq * t + beta * lfo(cycles))


def wrap_add(dst, start, burst):
    idx = (start + np.arange(burst.size)) % N
    np.add.at(dst, idx, burst)


out = np.zeros((N, 2))
breath = 1 + .22 * lfo(4) + .08 * lfo(1, 1.3)  # 2 s swell, slow 8 s drift
for ch in range(2):
    roar = periodic_noise(band(70, 1800, -.9)) * (.55 + .25 * lfo(3, ch))
    hiss = periodic_noise(band(1800, 7000, -.5)) * (.07 + .04 * lfo(5, 2 + ch))
    out[:, ch] += (roar * .34 + hiss) * breath

throb = (tone(38.0) + tone(38.5)) * .5 * (.8 + .2 * lfo(4))
drone = tone(76.0, .4, 2) * .16 + tone(114.0, .3, 3) * .09 + tone(152.0, .2, 1) * .05
shimmer = (tone(1520.0, 2, 3) + tone(1520.5, 2, 3)) * .006 * (1 + lfo(2))
for ch in range(2):
    out[:, ch] += throb * .30 + drone * (1 + .15 * lfo(4, np.pi)) + shimmer * (1 if ch else -1)

thump = np.zeros(N)
k = np.arange(int(.45 * SR)) / SR
pulse = np.sin(2 * np.pi * 46 * k - 6 * k) * np.exp(-k / .11) * (1 - np.exp(-k / .006))
for beat in range(4):
    wrap_add(thump, beat * 2 * SR, pulse)
out += thump[:, None] * .42

for _ in range(46):
    n = int(SR * rng.uniform(.004, .03))
    burst = rng.standard_normal(n) * np.exp(-np.arange(n) / (n / 4)) * rng.uniform(.04, .16)
    burst = np.diff(burst, prepend=0)  # brighter, ember-like
    pan = rng.uniform(.2, .8)
    start = int(rng.uniform(0, N))
    wrap_add(out[:, 0], start, burst * (1 - pan))
    wrap_add(out[:, 1], start, burst * pan)

out *= .89 / np.max(np.abs(out))
seam = np.max(np.abs(out[-1] - out[0]))
rms = 20 * np.log10(np.sqrt(np.mean(out ** 2)))
print(f"seam jump {seam:.4f}  rms {rms:.1f} dBFS")
wavfile.write(sys.argv[1], SR, (out * 32767).astype(np.int16))
