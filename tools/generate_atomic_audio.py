"""Generate legacy placeholder Foley into build/ only; never overwrite authored release SFX."""
from pathlib import Path
import subprocess
import tempfile
import wave
import numpy as np

RATE = 44100
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/legacy-atomic-audio"
rng = np.random.default_rng(1300)


def filtered_noise(size, width):
    raw = rng.normal(size=size + width - 1)
    return np.convolve(raw, np.ones(width) / np.sqrt(width), mode="valid")


def save(name, left, right):
    samples = np.stack([left, right], axis=1)
    samples = np.tanh(samples * .65) * .86
    samples[:600] *= np.linspace(0, 1, 600)[:, None]
    samples[-4000:] *= np.linspace(1, 0, 4000)[:, None]
    assert np.isfinite(samples).all() and np.max(np.abs(samples)) <= .87
    assert np.sqrt(np.mean(samples ** 2)) > .025
    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="godwand-atomic-audio-") as directory:
        wav = Path(directory) / f"{name}.wav"
        with wave.open(str(wav), "wb") as output:
            output.setparams((2, 2, RATE, 0, "NONE", "not compressed"))
            output.writeframes((samples * 32767).astype("<i2").tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav),
                        "-c:a", "libvorbis", "-q:a", "5", str(OUT / f"{name}.ogg")], check=True)
    print(f"{name}: {len(samples)/RATE:.2f}s, peak={np.max(np.abs(samples)):.3f}, RMS={np.sqrt(np.mean(samples**2)):.3f}")


t = np.arange(RATE * 19) / RATE
q = t / 19
phase = 2 * np.pi * (30 * t + 1.8 * t * t)
drone = np.sin(phase) * .35 + np.sin(phase * 2.003) * .16
spark = np.sin(phase * 8 + np.sin(t * 7)) * .045
air = filtered_noise(len(t), 31) * (.045 + .30 * q ** 3)
pulse = 1 + .13 * np.sin(2 * np.pi * (2 * t + .8 * t * t))
envelope = (.12 + .95 * q ** 1.8) * np.minimum(t * 2, 1)
left = (drone + spark + air) * envelope * pulse
right = (drone + np.roll(spark + air, 430)) * envelope * pulse
save("atomic_charge", left, right)

t = np.arange(RATE * 21) / RATE
sub = np.sin(2 * np.pi * (34 * t + 11 * (1 - np.exp(-t * 9)))) * np.exp(-t * 1.2) * 1.1
impact = filtered_noise(len(t), 5) * np.exp(-t * 11) * .9
thunder = filtered_noise(len(t), 130) * np.exp(-t * .55) * .65
corona = filtered_noise(len(t), 13) * (1 - np.exp(-t * 5)) * np.exp(-t * .65) * .28
pressure = filtered_noise(len(t), 65) * np.exp(-((t-1.3)/.22)**2) * .65
pressure += np.sin(2*np.pi*29*t) * np.exp(-((t-1.3)/.3)**2) * .55
pressure += filtered_noise(len(t), 90) * np.exp(-((t-5)/.35)**2) * .45
collapse = np.clip((t-12)/2, 0, 1) * np.clip((18-t)/3, 0, 1)
pressure += filtered_noise(len(t), 31) * collapse * (.09+.08*np.sin(t*34)**2)
crackle = np.sin(t * 271 + np.sin(t * 931) * 3) * np.exp(-t * 1.8) * .08
left = sub + impact + thunder + corona + crackle + pressure
right = sub + np.roll(impact, 370) + np.roll(thunder, 731) + np.roll(corona, 211) - crackle + np.roll(pressure, 270)
save("atomic_blast", left, right)
