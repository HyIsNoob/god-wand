# Shader compatibility and modpack settings

God Wand targets **Fabric 1.21.1**. Its render hook works with vanilla rendering and is placed after Iris finishes composing the world. Sodium does not require a separate rendering backend. This JAR is not an Oculus/Forge port.

World matrices are captured during Fabric's `WorldRenderEvents.LAST`. God Wand's post effects run immediately after `WorldRenderer.render` returns, before vanilla clears world depth to draw the hand. Iris finishes its world composite inside that call. This retains the completed shaderpack image and its depth buffer for God Wand's VFX. Supernova's drawn impact frames remain a HUD overlay independent of the world shader. Resource reloads discard cached post processors without resetting the effect clock.

Reference: [Iris 1.21.1 world finalization](https://github.com/IrisShaders/Iris/blob/1.21.1/common/src/main/java/net/irisshaders/iris/mixin/MixinLevelRenderer.java). The supplied Shooting Star 1.4.4 JAR's Minecraft 1.21.1 module was inspected for its separate matrix/depth capture and HUD/render hooks. No reference code or assets are included in God Wand.

The implementation has automated hook, shader, framebuffer-resize and lifecycle checks. Real gameplay with individual shaderpacks is still required; this is not a claim that every pack or rendering mod has been tested.

## Files

Both files are created automatically in the instance's `config` directory. Restart the client/server after editing. Ship these JSON files with a modpack; no config library is required.

- `godwand-client.json`: each player's visual preferences. Does not change damage or terrain.
- `godwand-server.json`: authoritative server settings, also used by integrated singleplayer servers.

Missing keys retain defaults. Out-of-range numbers are clamped. Malformed files produce a log warning and defaults are used; the original file is preserved. Unknown keys are ignored.

## Client defaults

```json
{
  "postEffects": true,
  "impactFrames": true,
  "cameraFov": true,
  "skyBlackout": true,
  "targetMarker": true,
  "cameraShake": 1.0,
  "bloomStrength": 0.85,
  "bloomDownsample": 4
}
```

| Key | Range | Effect |
| --- | --- | --- |
| `postEffects` | boolean | Enables world post effects, including the black hole, explosion volume and sky tear. Turning it off is a troubleshooting option; mesh VFX and HUD impact drawings remain. |
| `impactFrames` | boolean | Enables anime impact drawings and shader impact-frame filters. |
| `cameraFov` | boolean | Enables cinematic FOV changes. |
| `skyBlackout` | boolean | Enables temporary sky loss and scene darkening at Supernova detonation. |
| `targetMarker` | boolean | Enables the locked Supernova target's charging light column. |
| `cameraShake` | 0–1 | Scales camera shake; 0 disables it. |
| `bloomStrength` | 0–1.5 | Brightness of God Wand's dedicated bloom. Set lower if a shaderpack already adds strong bloom. This changes brightness, not pass count. |
| `bloomDownsample` | integer 2–8 | Divisor for emission/bloom texture dimensions. 2 gives finer glow; 4 is the default; 8 reduces GPU work while retaining the full-resolution effect body. |

For a GPU-heavy shaderpack, start with `bloomDownsample: 8` and `bloomStrength: 0.5`. These are optional tuning values; the shipped defaults preserve the existing animation detail.

## Server defaults

```json
{
  "cooldownMultiplier": 1.0,
  "supernovaTerrainRadius": 120,
  "supernovaMaxDisplays": 1800
}
```

| Key | Range | Effect |
| --- | --- | --- |
| `cooldownMultiplier` | 0.1–10 | Multiplies existing wand item cooldowns, including Spatial Path's toggle delay. 2 doubles the delay. |
| `supernovaTerrainRadius` | integer 16–180 | Bounds Supernova's horizontal crater, terrain peeling and buildup suction reach. Reducing this lowers terrain work; the damage radius and cinematic explosion size remain unchanged. |
| `supernovaMaxDisplays` | integer 0–1800 | Caps the orbiting block display entities. 0 disables flying block displays. Does not cap damage or terrain removal. |

For a busy server, try radius 64 and display limit 900 before reducing visuals further. Skills 1–2 still restore terrain; Skills 3–5 still permanently damage it. Lower terrain radius changes gameplay and should be selected by the server/modpack author.

## Gameplay checks per shaderpack

1. Charge Supernova while moving: the light column and sky tear stay at the locked target, and no distance cursor is shown.
2. Activate near uneven terrain: no white pressure-shell outline should crawl across the ground or flying blocks. The pressure ripple is restricted to sky pixels; accreted stones use environmental lighting.
3. Detonate and look in different directions: the sky disappears, the world dims, and the sky/daylight gradually return. Impact drawings remain visible.
4. Check Skills 2 and 3 impact filters, transparent mesh ribbons, foreground occlusion, water and first-person hands.
5. Toggle the shaderpack, reload resources, and resize/fullscreen the window between casts. Check `latest.log` for post-processor initialization errors.
