# Gate QA M3 (Silent Gate gate + x3 scale + new barrage camera, Fabric 1.21.1)

Runs: build/gate-m3-run1.log (gate only, before scale), run2/run3 (scaled, kill-shot camera). Sheets: build/fuga-qa/gate-sheets-m3/ (build.png ages 120..300 step 4 ticks, barrage-step2.png frames 170..276 step 2, build-150/175/190/200/215/240/300, build-cmd30, build-establishing-wide-248).
Tests: `:common:test --tests "*SilentGate*" --tests "*GateAtlas*" --tests "*GodWandConfig*"` green.

## What was built
- client/GateModel.java: lathe columns (34-point spinning-top profile, 24 sides, fluting by vertex shading + 4.5% groove, 3 engraved inlay bands), bevelled sill/jambs/lintel/crown with inlay strips, spire, scroll-hook tubes (2 big at the crown, 2 small round the hanging spike), two door leaves (slab + raised border/rail, seal + filigree + rune inlay both faces). Normal-based shading per vertex (porcelain 0xEEF2F8 -> shadow 0x6A7892, darker to the foot and underneath, headlight fill, silver highlight), software back-face cull, pieces sorted far to near, LOD 24/16/12 sides. Rim light additive (silhouette for round parts, light-based on bevel edges), veins = same inlay quads additive, lit only after the 206 shockwave (flare then 20-tick beat, extra flare on the activation click).
- Depth prepass (SilentGateRenderer.depthPrepass): the body is alpha blended without depth writes, so the additive layer (circle glow) used to shine through it; the body is now stamped into depth between the two passes.
- client/GateFx.java: light slit (two crossed lens planes, opens 138-148, closes 188-200, ending reopens), descent wind streaks + dust specks scaled with fall speed, dust ring at rest (184-206), horizontal shockwave at 206 (clouds pushed out one beat in GateSky), pressure rings every 20 ticks + breathing shell, light plane limited to the real gap between leaves, god rays, bloom.
- GateDraw.shaded: per-corner colour/alpha quad (optionally textured) with a gentle camera-proximity fade.
- Doors: hinged on the jambs, swing OUT TOWARDS THE CASTER (-d). Flipped on owner request (first version swung away).
- Scale: SilentGateSequence.SCALE = 3. Gate 192 tall frame (finials 222), 108 wide, opening 66x132; great circle radius 168 at altitude min(300, 304 - anchor.y); gate foot = altitude - 240 (finials 18 clear); sky circles spread over 3x area (y +90..+210) with mushrooms x2; clouds, tiers, halos, plate, shafts, wave speed (18/tick) x3; beams/impacts x2; ground arc unchanged. Config: gateTargetRadius 160 (max 256), gateCraterRadius 3 (max 6); compat adapter JSONs for GodWandConfig (fabric/forge 1.20.1) updated for the new defaults.
- Camera: boundaries 12/60/125/165/end scaled with barrage length. 12-60 low wide rain, slow dolly; 60-125 kill shot: lock once at tick 60 on the highest max-health living entity (tie: nearest), tracked by id (last position if it dies; densest impact cluster if none), 12 blocks, below head, 25 degree orbit; 3-tick hit-stop = render clock runs at 15% for 3 ticks from the first beam landing within 6 blocks of the target (client only); 125-165 high shot sinking from +200 to +90; 165-end behind the caster toward the gate. Build shots 140-250 rescaled: ground view of the descent, close under the left column and spike, easing out to a 120-block wide shot.

## Numbers (run2 / run3, 1280x720, render 16, same machine, world partly regenerated because the target field grew to x +-130, z 8..150)
FPS BUILD 27.3 (min 1) / 28.4 (min 10); WAIT 55.4 / 51.5; BARRAGE 36.3 (min 14) / 23.0 (min 6); END 49.7 / 20.2. Run 3 is noisy (machine contention); run 2 is the cleaner one.
Quads per tick: BUILD avg 1563 max 2722; WAIT 2494 / 2625; BARRAGE avg 3299 max 4078; END max 3322. Gate alone <= 4000 in tests (peak measured in test: gate with both layers).
Server: worst tick 175 ms in BUILD (chunk generation at the new far targets), barrage MSPT avg 14 max 115.
killed 7/14 (rest of the mobs lie beyond the 160 target radius from the anchor or not hostile-reachable at the field edge; not investigated).

## Weak points
- Columns still read slightly plain in the long shots: fluting only shows close up; pillar shafts look thin next to the circle.
- The light behind the open doors is a hot white blob (additive); fine as a light source but it has no structure.
- Wide establishing frame with the caster visible is not achieved: at 120 blocks back the caster is below the lower frame edge because the camera pitches up at the gate.
- Sky circles can lie inside the gate volume (min radius 24 < half width 54).
- Kill shot: target is small (12 blocks); beams arrive from far above and are thin lines.
- Network range still 256 blocks, so far viewers lose the outer circles (SilentGateManager adapters would need regenerating to raise it).
