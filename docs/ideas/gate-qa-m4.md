# Silent Gate M4 QA (caster poses, eyelids, mana) - Fabric 1.21.1

Run: build/gate-m4-run6.log (final), earlier iterations run2..run5. World restored from gate-qa-pristine before each run; probe stops at command+20 (marker file build/fuga-qa/short). Sheets: build/fuga-qa/gate-sheets-m4/ (build-0-80.png = frames 0..40 every tick-pair, build-10/30/40/60/75/300.png, command-6.png).

## What was built
- GatePose (client/, not client/silentgate/: the Gate* files live flat in client/): pure joint timeline (right/left arm, head) with a heavy ease (slow start, ~3% overshoot, settle), wand pose in world space (hover before the chest, spin around the forearm axis 25..46 with a small wind-up, grip at the hip, release to a float beside the caster, point at the sky in WAIT, throw on the command, drift back at the end). GatePoseMixin applies the joints (separate mixin, Atomic/Fuga mixin and its adapters untouched).
- The wand is drawn in the world pass with GodWandItemRenderer.renderFloating (own method, new early return hides the in-hand wand while a gate cast is visible). Skin is always the default model.
- GateMana: 8 ribbons x 24 points, spring chain + damping, steady backward wind (owner feedback: stream behind the caster, not orbit), gentle undulation/curl, soft body avoidance plus hard cylinder (r .45, h 1.9), 5 glints per ribbon. Silk and four-point glint tiles added to GateAtlas. Quads: 368 silk + <=40 glints = <=408 (test caps 1500). Colour 7FE8FF -> 5CFFC8 -> white at the tail.
- GateEyelids: almond lids (arched top/inverted bottom), 24 px feather, blink at 12 (30% close), vignette gone by 30.
- Camera: 20..45 shot moved to a front-right 3/4 (sees the spin and the backward stream); held/WAIT frame is now low and close behind the caster (the old one never showed the caster).

## Numbers (1280x720, Gate QA flat world, short run)
FPS BUILD avg 55.3 min 40; WAIT avg 59.5 min 59; BARRAGE (first 10 frames only) avg 59.3 min 59. Quads emitted per tick (whole scene incl. mana, pass 0): BUILD avg 1398 max 2454; WAIT avg 2293 max 2440; BARRAGE avg 2709 max 3362 (budget 20000). Server MSPT peak 26 ms.

## Honest visual notes
- Lids: visible as an almond opening in the first frames, but the capture cadence only gets ~2 frames inside the opening; verified numerically in GateManaTest.eyelidsOpenWithABlink.
- Left index finger: the arm cannot bend, so the fist covers the lower face ("shh" reads, a finger is not modelled).
- WAIT: right arm points up, tilted 37 degrees outward (roll .64) so it does not hide behind the head; not perfectly vertical.
- Ribbons from a front camera are foreshortened; they read best in build-30/40/60 and the 20..45 shots. During the spin they whip once and settle into the backward stream.
- Frame 0 is black (eyes closed); the first-person wand sits 1 block ahead of the camera.
- The floating wand pops to the vanilla in-hand wand when the cast ends.

## Adapters / other targets (not built, only Fabric 1.21.1 checked; verifySharedSources passes)
New common files that need per-target adapters: mixin/GatePoseMixin.java (setupAnim signature on 1.21.11/26.x; registered in fabric, fabric-1.20.1, forge-1.20.1, neoforge mixin json, not in ports/*), client/GateEyelids.java (GuiGraphics API on 26.3 GuiGraphicsExtractor), SilentGateRenderer.wands + GodWandItemRenderer.renderFloating (MultiBufferSource/ModelPart renderWand signature on 1.21.11+/26.x where GodWandItemRenderer is replaced by GodWandItemModel adapters), GateMana.tick uses Entity.position()/yBodyRot, GatePose uses ModelPart (same on all). Existing adapters unchanged (edits were inserted outside their "before" strings).
