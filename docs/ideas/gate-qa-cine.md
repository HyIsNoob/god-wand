# Silent Gate cinematography QA (Fabric 1.21.1)

Runs build/cine1..cine6.log (cine6 final, world restored from gate-qa-pristine each time). Sheets: build/fuga-qa/gate-sheets-cine/
(build-0-480.png step 4, barrage.png and ending.png step 3 frames = 6 ticks, key-b<age>.png and key-f<n>.png full-size).
Commits: b729d22, 2ccb6c1, 81eed5c, 06a31df (continuous sequences), then the final circle-size commit.

## Camera: continuous keyframe sequences (Hermite, velocity continuous, rest keys stop dead) and the only hard cuts
BUILD (480 ticks), 4 sequences, 3 cuts:
| Sequence | Ticks | Content |
|---|---|---|
| SEQ1 | 0-120 | first person, out round the shoulder, front 3/4 sideways drift (spin), push in on the face that stops dead |
| cut 1 | 120 | face close-up to the sky under the great circle: change of subject and scale |
| SEQ2 | 120-290 | orbit under the circle (written 120-168), slow dolly toward the gate (slit 166, gate out 210), arrives at rest beside the spike's path as the gate lands (248, camera shake), then a pull-back from rest to the extreme wide (shockwave 274) |
| cut 2 | 290 | extreme wide to the door close-up: 240 to 46 blocks, a move would be long and dull |
| SEQ3 | 290-380 | slow orbit round the door (0.5 deg/tick), stuck 292-316, push apart 316-366, heavy stop 392, ends frontal |
| cut 3 | 380 | door frontal to a ground overview looking back and up (place and direction reversal) |
| SEQ4 | 380-480 | overview from the ground under the gate looking up at the whole spread (circles bloom 384-420, 24 ring circles from 393), hold to 405, then ONE low flight toward the caster, always looking up at circles that bloom overhead (35..75 above the camera lane, 14 ticks ahead), long ease-out, lands exactly on the waiting frame (no cut into WAIT) |
Barrage (200 ticks authored), 3 sequences, 2 cuts: BAR1 0-84 starts exactly on the waiting frame, round the caster to behind and low, forward and up under the formation looking up (no cut from WAIT); cut at 84 (sky to ground target); BAR2 84-170 kill close-up (5.5 blocks, 0.4 deg/tick) then one continuous pull-away from zero speed (volley at 100); cut at 170 (ground at the target to behind the caster); BAR3 170-212 low behind the caster rising slowly, looking up at circles and beams.
Ending (160 ticks), one continuous move, 1 cut (entry from BAR3: high behind the caster to medium on the doors): medium on the doors from below while they close (0-40), back and aside while circles retract (20-85), low and steeply up as the gate climbs into the great circle (75-125), still while it unwrites tier by tier (108-160); screen dims 135-160; 15 tick blend back to the player.
Rule applied: circles are seen from below or above, never level (all circle shots look up; far circles are drawn camera-facing).

## Numbers (1280x720, sampled; screenshots every 4th tick in BUILD)
cine6: FPS BUILD avg 55.0 (min 17), WAIT 59.4, BARRAGE 47.2 (min 6, one server hitch of 60 ms), END 59.0. Quads/tick: BUILD avg 1571 max 3054, WAIT 2454, BARRAGE avg 4009 max 4992, END avg 1885 max 4201 (budget 20000).
cine5 (circles at 2x scale): BARRAGE avg 53.8 min 23. Terrain damage ~11,400 blocks removed (3.3x the 48-circle run).

## Honest notes
- SEQ1/SEQ2 read as one move; the pull-back from the landing gate to the wide is fast (peak ~5 blocks/tick) but starts from rest.
- SEQ4: the overview and the low flight now show big legible rings from below; the stroke style is the far-LOD octagon ring.
- Circle scale is now 5x the old size: in the barrage and ending the circles are numerous and large enough to clutter and partly hide the gate (looks busy, white glare). VFX polish (opacity, size, runes) is deferred as agreed.
- Caster is not visible in the wide shots. BAR1 pops by up to 0.2 blocks (WAIT breathing) at the command. BAR2 is only as good as the focus target (falls back to the densest impacts).

## Beam rain and kill beam pass (run cine7)
- Server: each targeted beam comes from the sky circle horizontally nearest its target that is free this tick (busy circles are skipped). If the beam would lean more than 8 degrees, the circle first glides over the target (10-15 ticks, eased there and held, eased back over 12); the shot is announced at glide start, damage and crater land when the circle has arrived (Pending list). Beams without a target (ground pattern) go straight down. Client derives the same glide from circle and hit, so the packet format only gained the heavy marker (shot index + 4096).
- Kill: the 6-beam volley is gone. During the first second of the kill shot (16 ticks before command tick 100) the circle nearest the featured target glides over it, then ONE heavy beam (2x thick, second white layer, stronger impact, hit-stop on it) lands for 6x ordinary damage.
- Circles back to 3x scale; a circle in front of the gate that overlaps its silhouette fades (GateMinor.gateClear).
- QA cine7 (FPS BUILD 56.6, WAIT 59.5, BARRAGE 53.8 min 30, END 59.1; quads BARRAGE avg 3826 max 4865): sheets in gate-sheets-cine; key-f95.png is the ref-11-like wide low look toward the gate with a near-vertical beam rain; kill-c084..c108.png are the kill-shot frames (c100 is the heavy beam landing).
- Caveat: the announced hit position is the target's centre at announce time, so a mob that walks during the 10-16 tick glide is struck (damage) where it now is, but the beam is drawn at where it was; a few diagonal beams remain at the sides of key-f95 (circles that had not finished gliding).

## Circles stay put + dense canopy (run cine9)
- Bug cause: the glide-over-target (up to ~100 blocks, 10-15 ticks) from the previous pass. Layout itself is a pure function of (anchor, yaw, seed, index) and never changed between bloom, WAIT and barrage.
- Fix: a circle only makes a short sideways slide (ordinary at most 15 blocks, the kill beam at most 20, 6-11 ticks) so the beam leans at most 8 degrees; out-of-canopy targets fire from the circle as it stands. Tests: SilentGateVisualTest.circlesStayExactlyWhereTheyBloom... (position equals the bloom position from bloom through WAIT, activation and barrage; with a shot it never moves more than 20 blocks and ends back home) and SilentGateSequenceTest beamsRainNearlyVertically...
- Layout: three height tiers (55-90, 115-160, 175-215 above the anchor), an even R2 spread over the ground 10..190 ahead and -150..10 behind the caster, 130 to either side; lane circles unchanged; default circles 160 to 220 (clamp 256). 95% of ground points in the target field have a circle within 30 blocks horizontally.
- QA cine9: FPS BUILD 56.5 (min 33), WAIT 59.3, BARRAGE 55.7 (min 45), END 59.1; quads BARRAGE avg 3967 max 4943. Before/after frames: gate-sheets-cine-before/before-b450/b500, gate-sheets-cine/after-b450/b479/b500 (WAIT frame: about 8 visible circles before, about 20 after), after-f30/f50/f95 and kill-c090/100/102.
