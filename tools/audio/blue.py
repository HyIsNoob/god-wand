"""Blue's sounds (owner, 2026-11-20: "design Blue's SFX"): mono (they are placed in the world), written as .ogg into the mod's sounds.

  blue_appear  ~1.4 s  air sucked in from all round, closing on a deep thoom and a faint glassy ring
  blue_hold     4 s    loop: a deep gravitational hum beating slowly, wind drawn in in pulses, a thin cold shimmer
  blue_ready   ~1.8 s  a heavy swell (whoomph) and a bright crystalline bell
  blue_vanish  ~1.0 s  a fast rising suck, then a pop

The loop is periodic over its length (noise built in the frequency domain, tones on whole cycles), so it repeats without a seam.
Usage: python tools/audio/blue.py   (needs numpy, scipy and ffmpeg with libvorbis)
"""
import subprocess, tempfile
from pathlib import Path
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, sosfilt

SR = 44100
OUT = Path(__file__).resolve().parents[2] / 'common/src/main/resources/assets/godwand/sounds'
rng = np.random.default_rng(20261120)


def bandpass(x, lo, hi, order=2):
    return sosfilt(butter(order, [lo, hi], btype='band', fs=SR, output='sos'), x)


def lowpass(x, hi, order=2):
    return sosfilt(butter(order, hi, btype='low', fs=SR, output='sos'), x)


def sweep_band(x, lo0, lo1, width, steps=48):
    """Band-pass whose centre glides from lo0 to lo1 (log), crossfading short blocks."""
    n = x.size
    out = np.zeros(n)
    edges = np.linspace(0, n, steps + 1).astype(int)
    win = np.hanning(2 * (edges[1] - edges[0]) + 2)
    for i in range(steps):
        c = lo0 * (lo1 / lo0) ** (i / (steps - 1))
        a, b = max(0, edges[i] - (edges[1] - edges[0])), min(n, edges[i + 1] + (edges[1] - edges[0]))
        seg = bandpass(x[a:b], max(20, c / width), min(SR / 2 - 100, c * width))
        w = np.hanning(b - a)
        out[a:b] += seg * w
    return out


def env_exp(n, tau):
    return np.exp(-np.arange(n) / (tau * SR))


def write(name, x):
    x = x / max(1e-9, np.max(np.abs(x))) * .89
    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as d:
        wav = Path(d) / f'{name}.wav'
        wavfile.write(wav, SR, (x * 32767).astype(np.int16))
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', str(wav), '-c:a', 'libvorbis', '-q:a', '6', str(OUT / f'{name}.ogg')], check=True)
    print(name, f'{x.size / SR:.2f} s', f'rms {20 * np.log10(np.sqrt(np.mean(x ** 2))):.1f} dBFS')


def appear():
    n = int(1.4 * SR)
    t = np.arange(n) / SR
    noise = rng.standard_normal(n)
    # Air drawn in: a band of wind rising in pitch and loudness (reversed decay), cut off where the sphere closes at 1.0 s.
    close = 1.0
    swell = np.clip(t / close, 0, 1) ** 2.2 * (t < close)
    wind = sweep_band(noise, 250, 2400, 1.6) * swell
    # The thoom as it closes: a sine dropping 95 -> 42 Hz, with a sub under it.
    k = np.clip(t - close, 0, None)
    f = 42 + 53 * np.exp(-k / .08)
    phase = 2 * np.pi * np.cumsum(f) / SR
    thoom = np.sin(phase) * np.exp(-k / .35) * (t >= close)
    click = lowpass(rng.standard_normal(n), 900) * np.exp(-k / .012) * (t >= close)
    # A faint glassy ring, inharmonic partials.
    ring = sum(a * np.sin(2 * np.pi * fr * k) * np.exp(-k / d) for fr, a, d in ((1318, .35, .5), (1977, .22, .35), (2793, .14, .25))) * (t >= close)
    return wind * .8 + thoom * 1.1 + click * .4 + ring * .12


def hold():
    L = 4.0
    n = int(L * SR)
    t = np.arange(n) / SR

    def tone(fr):
        assert abs(fr * L - round(fr * L)) < 1e-9
        return np.sin(2 * np.pi * fr * t)

    def periodic_noise(lo, hi):
        f = np.fft.rfftfreq(n, 1 / SR)
        spec = (1 / (1 + (lo / np.maximum(f, 1)) ** 4)) * (1 / (1 + (f / hi) ** 4)) * np.exp(1j * rng.uniform(0, 2 * np.pi, f.size))
        spec[0] = 0
        x = np.fft.irfft(spec, n)
        return x / np.max(np.abs(x))

    def lfo(cycles, phase=0.0):
        return np.sin(2 * np.pi * cycles * t / L + phase)

    # Deep hum beating slowly (41.25 against 41.5 Hz: a quarter-hertz throb) with a soft octave.
    hum = (tone(41.25) + tone(41.5)) * .5 * (.85 + .15 * lfo(1)) + tone(82.5) * .18 + tone(123.75) * .06
    # Wind drawn in, in four pulses a loop: each swells and is cut short, as if sucked away.
    saw = (t * 4 / L) % 1
    pulse = saw ** 1.8 * (1 - np.clip((saw - .9) / .1, 0, 1))
    wind = periodic_noise(180, 1400) * (.35 + .65 * pulse) * (.9 + .1 * lfo(3, 1))
    hiss = periodic_noise(2500, 7000) * (.08 + .1 * pulse)
    shimmer = (tone(1650.0) * (1 + .3 * lfo(2)) + tone(1650.25) * .7) * .02
    return hum * .9 + wind * .45 + hiss * .3 + shimmer


def ready():
    n = int(1.8 * SR)
    t = np.arange(n) / SR
    f = 38 + 90 * np.exp(-t / .12)
    whoomph = np.sin(2 * np.pi * np.cumsum(f) / SR) * (1 - np.exp(-t / .02)) * np.exp(-t / .6)
    gust = sweep_band(rng.standard_normal(n), 1800, 220, 1.7) * np.exp(-t / .35) * (1 - np.exp(-t / .01))
    bell = sum(a * np.sin(2 * np.pi * fr * t + .3 * np.sin(2 * np.pi * 5.5 * t)) * np.exp(-t / d)
               for fr, a, d in ((987.8, .5, .9), (1479, .35, .7), (1975.5, .25, .55), (2960, .15, .4), (3951, .08, .3)))
    return whoomph * 1.0 + gust * .45 + bell * .3


def vanish():
    n = int(1.0 * SR)
    t = np.arange(n) / SR
    pop_at = .72
    rise = np.clip(t / pop_at, 0, 1) ** 3 * (t < pop_at)
    suck = sweep_band(rng.standard_normal(n), 300, 4200, 1.5) * rise
    k = np.clip(t - pop_at, 0, None)
    pop = (np.sin(2 * np.pi * (70 + 120 * np.exp(-k / .02)) * k) * np.exp(-k / .09) + lowpass(rng.standard_normal(n), 1500) * np.exp(-k / .01) * .6) * (t >= pop_at)
    return suck * .7 + pop * 1.0


if __name__ == '__main__':
    for name, fn in (('blue_appear', appear), ('blue_hold', hold), ('blue_ready', ready), ('blue_vanish', vanish)):
        x = fn()
        if name != 'blue_hold':
            x[:200] *= np.linspace(0, 1, 200)
            x[-800:] *= np.linspace(1, 0, 800)
        else:
            print('  loop seam jump', f'{abs(x[-1] - x[0]):.4f}')
        write(name, x)
