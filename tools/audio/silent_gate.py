"""Silent Gate's whole soundtrack, mixed from the owner's CC0 library (freesound.org, CC0; owner, 2026-11-15: "bells"). Timed tracks follow the cast's
beats in ticks (20 per second, see SilentGateSequence); loops are cross-faded on themselves so they repeat without a seam; one-shots are layered
samples. Usage: python silent_gate.py <out dir> [library dir]. Needs numpy, scipy and ffmpeg (libvorbis) on PATH."""
import os, subprocess, sys
import numpy as np
from scipy.signal import butter, sosfilt, resample_poly

SR = 44100
LIB = sys.argv[2] if len(sys.argv) > 2 else r'D:\fileluu\SFX-CC0'
rng = np.random.default_rng(1508)
_cache = {}

# The samples, by freesound id (all CC0).
TOLL = '73678__daytripper__tollbell.wav'   # the only bell: struck where the spell is set off (owner, 2026-11-15)
MAGIC_IMPACT, CHANNEL = '849412__cvltiv8r__magical-impact-with-trippy-trail-fx.wav', '849806__mikiko850__channeling-magic-texture.wav'
IMPLOSION, ENERGY, ZAP = '693655__kanakanna__implosion.wav', '576092__tim_verberne__s_l_2_energy_intense_01.wav', '867248__bassimat__astronomical-energy-zap-with-futuristic-laser-burst.wav'
RISE_HIT, SHOCKWAVE, SD24, SD47, SD72 = '401106__s-cheremisinov__cinematic-rise-and-hit_05.wav', '366091__bevibeldesign__huge-explosion-part-1-shockwave.aiff', '755974__audiopapkin__sound-design-elements-sfx-ps-0024.wav', '812756__audiopapkin__sound-design-elements-sfx-ps-047.wav', '812768__audiopapkin__sound-design-elements-sfx-ps-072.wav'
INCOMING, LARGE, FIRE_WHOOSH, ENGINE, RISER_HIT69, WARHEAD, ROCKET = '719426__zapsplatcom__missile-or-bomb-incoming-whoosh-and-explosion.wav', '482993__v-ktor__large-explosion-1.wav', '244926__hnhnh__fire-whoosh.wav', '186961__readeonly__engine3_down.wav', '746474__audiopapkin__riser-hit-sfx-069.wav', '568877__fusionwolf3740__self-destructalpha-11-warhead-explosion.wav', '774245__thelittlecrow__rocket-launch-boost-and-burning-version-a.wav'
VOICE = '855303__kkenny101__granular-voice-drone-low-pass-sub-bass-paulstretch30x.flac'
HUM, METAL, GRIND = '812392__zeraora__forcefield-hum-2.wav', '854350__kkenny101__seamlessly-looping-metallic-snare-drone.wav', '864691__bassimat__mechanical-industrial-wavetable-drone-by-mantice.wav'
HEART, SPIN = '860781__bassimat__low-frequency-vibrating-drone-with-heart-pressure-rumbles-and-booms.wav', '334003__uzbazur__big-cinematic-spinning-sound.wav'
SUB, JUNO, RUMBLE = '59541__uzerx__sub-bass-5-secondsssss.wav', '268608__breo2012__juno-sub.wav', '856174__brktkrgll__subterranean-deep-sub-bass-rumble-drop.mp3'
SYNTH_IMPACT, TRAILER, HELLISH = '829915__jw_audio__dsgnimpt_synthetic-impacts_boom-impact-sci-fi-synthetic-slow-evolving-recording-8_jw-audio_si.wav', '816376__harrisonlace__dsgnstngr_basic-trailer-boom-impact.wav', '826216__rohhsadotcom__powerful-rumbly-hellish-impact.wav'
DEEP_HIT, WHOOSH, QUICKSILVER = '845165__danjfilms__cinematic-boom-deep-synth-hit-stab.wav', '260555__lookimadeathing__basic-fire-whoosh-2.flac', '333515__oscaraudiogeek__flash-and-quicksilver.wav'
RISER_LAND, RISER_DOORS, EXPLOSION8 = '754772__audiopapkin__riser-hit-sfx-098.wav', '745429__audiopapkin__riser-hit-sfx-067.wav', '869468__signaturesoundsorg__experimental_explosion_8.wav'
AURA, IMPACT01, STORM, RAY = '197266__qubodup__wind-water-aura.flac', '719831__vekon__impact_01.wav', '735350__vrymaa__wind-hurricane-typhoon-storm.wav', '846943__artninja__custom_giant_death_ray_beam_loop_sound_03012026.wav'
BOOM, KICK, EXPLOSION, CLAP, SNARE = '157245__snapper4298__boom.wav', '787668__securesubset__techno-kick-heavy-low-end-boom.wav', '446625__idkmrgarcia__explosion.wav', '579205__jonsept__clap-at-two-church-6.wav', '667125__mo-reno-lopez__claps-snares-002.wav'
ASTRO, MASSIVE, SUNLIGHT = '760509__samsterbirdies__astronomical-explosion.flac', '741175__qubodup__massive-explosion.flac', '826672__johnmode__first-sunlight-cinematic-riser-sound-abrupt-stop-and-drone.wav'


def load(name, start=0.0, dur=None):
    """Stereo float32 at 44.1 kHz, the slice [start, start + dur) in seconds."""
    if name not in _cache:
        r = subprocess.run(['ffmpeg', '-v', 'error', '-i', os.path.join(LIB, name), '-f', 'f32le', '-ac', '2', '-ar', str(SR), '-'], capture_output=True, check=True)
        _cache[name] = np.frombuffer(r.stdout, np.float32).reshape(-1, 2).astype(np.float64)
    x = _cache[name]
    a = int(start * SR)
    return x[a: a + int(dur * SR) if dur else None].copy()


def fade(x, fin=0.0, fout=0.0):
    n = len(x)
    if fin: k = min(n, int(fin * SR)); x[:k] *= np.linspace(0, 1, k)[:, None]
    if fout: k = min(n, int(fout * SR)); x[n - k:] *= np.linspace(1, 0, k)[:, None]
    return x


def filt(x, kind, hz, order=2):
    return sosfilt(butter(order, hz, kind, fs=SR, output='sos'), x, axis=0)


def pitch(x, semis):
    """Up or down by resampling (the length changes with it, as a tape would)."""
    if not semis: return x
    up, down = (1000, int(round(1000 * 2 ** (semis / 12))))
    return resample_poly(x, up, down, axis=0)


def pan(x, p):
    """p in -1 (left) .. 1 (right), constant power."""
    a = (p + 1) * np.pi / 4
    return x * np.array([np.cos(a), np.sin(a)]) * np.sqrt(2)


def peak_at(x):
    """Seconds into x where its loudness peaks (20 ms windows): what lines up with a beat."""
    hop = SR // 50
    env = [np.sqrt(np.mean(x[i:i + hop] ** 2)) for i in range(0, max(1, len(x) - hop), hop)]
    return int(np.argmax(env)) / 50


class Track:
    def __init__(self, seconds): self.x = np.zeros((int(seconds * SR), 2))

    def put(self, x, t, gain=1.0, align_peak=False):
        """x at t seconds (its loudest moment at t when align_peak); clipped to the track."""
        t -= peak_at(x) if align_peak else 0
        a = int(round(t * SR))
        if a < 0: x, a = x[-a:], 0
        n = min(len(x), len(self.x) - a)
        if n > 0: self.x[a: a + n] += x[:n] * gain
        return self

    def duck(self, t0, t1, t2, t3, floor):
        """Pull everything down to floor between t1 and t2 (ramps t0..t1 and t2..t3): the silence of the 'shh'."""
        t = np.arange(len(self.x)) / SR
        g = np.ones_like(t)
        g = np.where((t > t0) & (t < t1), 1 - (1 - floor) * (t - t0) / (t1 - t0), g)
        g = np.where((t >= t1) & (t <= t2), floor, g)
        g = np.where((t > t2) & (t < t3), floor + (1 - floor) * (t - t2) / (t3 - t2), g)
        self.x *= g[:, None]
        return self


def tick(t): return t / 20.0


def master(x, peak):
    x = np.tanh(1.3 * x / max(1e-9, np.max(np.abs(x)))) / np.tanh(1.3)   # a soft knee, not a hard clip
    return x * peak


def loopify(x, seconds, xf=1.0):
    """The first seconds of x made into a seamless loop: the tail past the loop length is cross-faded into its head."""
    n, k = int(seconds * SR), int(xf * SR)
    if len(x) < n + k: x = np.concatenate([x, np.zeros((n + k - len(x), 2))])   # a sample or two short from rounding
    out = x[:n].copy()
    w = np.linspace(0, 1, k)[:, None]
    out[:k] = x[:k] * w + x[n: n + k] * (1 - w)
    return out


# ---- timed tracks --------------------------------------------------------------------------------------------
# Owner, 2026-11-15: bells only where the spell is set off (the click, the command); everywhere else many layers of other sounds, so each beat is rich.
def build():
    """Click 1 to the wait (480 ticks): the click, the mana, the great circle, the crack, the heavy descent, the landing, the wings, the silence of the
    'shh', the wand spun and the discs, the doors that stick and give, and the light that floods out over the sky."""
    T = Track(26)
    # The click: the bell, a burst of magic, an implosion pulling the air in, a sub thump.
    T.put(load(TOLL), .05, .7).put(load(MAGIC_IMPACT, 0, 5), .05, .55, True).put(load(IMPLOSION, 0, 3), .05, .3, True).put(load(JUNO), .05, .45, True)
    # The mana: channelled magic, the hum of power and a rising charge of energy over the low choir bed (it rests for the "shh").
    T.put(fade(load(CHANNEL, 0, 4.2), .5, 1), .3, .55)
    T.put(fade(filt(load(HUM, 4, 4), 'low', 3000), 1, 1), .4, .25)
    T.put(fade(filt(load(ENERGY, 14, 4), 'band', [200, 5000]), 1.5, 1), .3, .25)
    T.put(fade(load(VOICE, 20, 13), 3, 1.5), .6, .35)
    # The great circle is written (70): something vast begins to turn, struck in with a rise and a hit, and a designed swell over it.
    T.put(fade(load(SPIN, 3.5, 4.2), .6, 1.2), tick(70), .5)
    T.put(load(RISE_HIT, 0, 4), tick(70), .45, True).put(fade(load(SD24, 0, 3.5), 0, 1), tick(72), .3, True)
    # The sky cracks (142): a synthetic impact, the front of a shockwave, a tearing element.
    T.put(load(SYNTH_IMPACT, 0, 4), tick(142), .55, True).put(load(SHOCKWAVE), tick(142), .35, True).put(load(SD72, 0, 3), tick(142), .3, True)
    # The descent (158..220): the drone of something heavy, the wind it drags through the sky, seven heavy beats, and what comes in before the landing.
    T.put(fade(filt(load(HEART, 10, 4), 'low', 400), .6, .5), tick(152), .6)
    T.put(fade(filt(load(STORM, 30, 3.6), 'low', 1200), .8, .6), tick(158), .35)
    for k in range(7): T.put(filt(load(JUNO, 0, .6), 'low', 200), tick(158 + 62 * (k + .5) / 7), .3 + .04 * k, True)
    T.put(load(RISER_LAND, 0, 6), tick(220), .5, True).put(load(INCOMING, 0, 4), tick(220), .3, True)
    # The landing (220): the boom, a hellish rumble, the ground giving, an explosion under it.
    T.put(load(TRAILER), tick(220), .85, True).put(load(HELLISH), tick(220), .5, True).put(load(RUMBLE), tick(220), .45, True).put(load(LARGE, 0, 2.5), tick(220), .3, True)
    # The wings thrown open (236): air torn in two, a deep hit, fire of light, a swell of air.
    T.put(filt(load(WHOOSH), 'low', 3000), tick(236), .6, True).put(load(DEEP_HIT), tick(236), .5, True)
    T.put(filt(load(FIRE_WHOOSH), 'low', 4000), tick(236), .3, True).put(fade(load(AURA, 3, 3), .2, 1.5), tick(234), .3)
    # The "shh" (258..290): everything falls away.
    T.duck(tick(246), tick(258), tick(286), tick(300), .04)
    # The wand is spun (290), the discs are made behind the caster (306..322), the choir comes back.
    T.put(load(QUICKSILVER), tick(296), .45, True).put(fade(load(SD47, 8.5, 2.4), .3, .8), tick(304), .3)
    T.put(load(MAGIC_IMPACT, 0, 4), tick(310), .3, True)
    T.put(fade(load(VOICE, 40, 10), 1.5, 2), tick(300), .3)
    # The doors stick (340..365) and give (365..412): grinding stone, strained metal, a mechanism winding down, a riser into the stop.
    T.put(fade(filt(load(GRIND, 30, 4), 'low', 1500), .8, .6), tick(336), .5)
    T.put(fade(filt(load(METAL, 2, 3.5), 'low', 2500), .8, .8), tick(342), .25)
    T.put(fade(filt(load(ENGINE, 1.2, 2.5), 'low', 900), .5, .6), tick(366), .3)
    T.put(load(RISER_DOORS, 0, 5), tick(412), .5, True)
    # They meet their stops (412): an explosion of air, the boom, a hellish rumble.
    T.put(load(EXPLOSION8, 0, 4), tick(412), .6, True).put(load(TRAILER), tick(412), .45, True).put(load(HELLISH), tick(412), .35, True)
    # The light floods out and the small circles open across the sky (420..470): a swell of air, a surge of energy, magic and a zap of light running through it.
    T.put(fade(load(AURA, 2, 6), 1, 2.5), tick(418), .45)
    T.put(fade(filt(load(ENERGY, 18, 3.5), 'band', [300, 8000]), .6, 1.2), tick(422), .3)
    T.put(fade(load(CHANNEL, 4, 3), .5, 1.2), tick(426), .35).put(fade(load(ZAP, 4.8, 1.6), .1, .6), tick(436), .25, True)
    return master(fade(T.x, 0, .5), .95)


def ending():
    """The barrage is over (220 ticks): the doors close and settle (40), the gate climbs slowly into the great circle (80..170) on a long riser, and the
    great circle folds inward (160..220) as the choir fades. Softer than the build (owner, 2026-11-15: "the end is a bit loud"): low-passed, no hard hits."""
    T = Track(13)
    T.put(fade(filt(load(GRIND, 60, 2.4), 'low', 900), .4, .5), 0, .35)
    T.put(filt(load(TRAILER), 'low', 500), tick(40), .5, True)
    T.put(fade(load(SUNLIGHT, 0, 8.2), 1.5, .2), tick(170) - 7.54, 1.4)   # a quiet sample: it carries the climb, so it is lifted
    T.put(fade(filt(load(ROCKET, 3, 4.5), 'low', 500), 1.5, 1.5), tick(85), .2)
    T.put(fade(load(VOICE, 70, 9), 2, 3), tick(60), .4)
    T.put(fade(filt(load(IMPLOSION, 0, 5), 'low', 5000), .3, 1.5), tick(175), .25, True).put(fade(load(AURA, 8, 4), 1, 2.5), tick(165), .3)
    return master(filt(fade(T.x, 0, .8), 'low', 7000), .7)


# ---- loops -------------------------------------------------------------------------------------------------------
def wait_loop():
    """The gate stands open, waiting: the choir drone, the hum of the circles' power, a faint shimmer of strained metal (12 s, seamless)."""
    x = .9 * load(VOICE, 60, 13.5) + .25 * filt(load(HUM, 2, 13.5), 'low', 3000) + .08 * filt(load(METAL, 5, 13.5), 'band', [300, 2500])
    return master(loopify(x, 12), .5)


def rain_loop():
    """The rain of light: a storm torn open over the field, the hum of the beams, the choir high above, the low throb of the ground (8 s, seamless)."""
    n = int(9.2 * SR)
    x = (.8 * filt(load(STORM, 10, 9.2), 'low', 2500) + .3 * filt(load(RAY, 0, 9.2), 'band', [400, 6000]) + .3 * pitch(load(VOICE, 100, 14), 7)[:n]
         + .25 * filt(load(HEART, 40, 9.2), 'low', 200))
    return master(loopify(x, 8, 1.2), .55)


# ---- one-shots ---------------------------------------------------------------------------------------------------
def command():
    """The order is given: the second and last bell, on a deep impact, a burst of magic and a riser-hit."""
    T = Track(6)
    T.put(load(TOLL), 0, .85).put(load(IMPACT01, 0, 5), 0, .5, True).put(load(MAGIC_IMPACT, 0, 5), 0, .4, True)
    T.put(load(RISER_HIT69), 0, .35, True).put(load(SUB), 0, .3, True)
    return master(fade(T.x, 0, .5), .95)


# Onsets of the laser bursts in the zap sample: each strike variant takes its own.
ZAPS = (.76, 1.78, 7.38, 14.16)


def strike(i):
    """A beam lands near by: the zap of the beam, a crack and a boom, each variant its own set and pitch."""
    T = Track(2)
    crack = [CLAP, SNARE, CLAP, SNARE][i]
    body = [BOOM, KICK, EXPLOSION, BOOM][i]
    T.put(fade(load(ZAP, ZAPS[i] - .05, .45), .01, .2), 0, .35)
    T.put(pitch(load(crack), [-3, 2, 0, -6][i]), .05, .5, True).put(fade(pitch(load(body, 0, 1.6), [0, -2, 3, -4][i]), 0, .4), .05, .9, True)
    return master(fade(T.x, 0, .3), .8)


def strike_far(i):
    T = Track(3)
    T.put(fade(filt(pitch(load([BOOM, EXPLOSION, RUMBLE][i], 0, 2.5), -3), 'low', 350), 0, .8), .02, 1, True)
    return master(fade(T.x, 0, .5), .55)


def kill():
    """The decisive beam lands: the astronomical blast, a warhead's crack, a massive tail, the ground giving under it."""
    T = Track(8)
    T.put(load(ASTRO, 1.9, 6), .05, 1, True).put(load(WARHEAD, 0, 4), .05, .4, True).put(fade(load(MASSIVE, 0, 7), 0, 2), .05, .45).put(load(RUMBLE), .05, .5, True)
    return master(fade(T.x, 0, 1), .98)


def write(out_dir, name, x):
    raw = np.clip(x, -1, 1).astype('<f4').tobytes()
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'f32le', '-ar', str(SR), '-ac', '2', '-i', '-', '-c:a', 'libvorbis', '-q:a', '5',
                    os.path.join(out_dir, name + '.ogg')], input=raw, check=True)
    print(name, '%.2fs' % (len(x) / SR))


if __name__ == '__main__':
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    # gate_build is the owner's own track (click 1 to the command, 31.68 s) and there is no wait loop any more: neither is written here.
    for name, loop in (('gate_rain', rain_loop()),):
        assert np.max(np.abs(loop[-1] - loop[0])) < .1, name   # the seam: the last sample runs on into the first
        write(out, name, loop)
    write(out, 'gate_end', ending())
    write(out, 'gate_command', command())
    write(out, 'gate_kill', kill())
    for i in range(4): write(out, 'gate_strike_%d' % (i + 1), strike(i))
    for i in range(3): write(out, 'gate_strike_far_%d' % (i + 1), strike_far(i))
