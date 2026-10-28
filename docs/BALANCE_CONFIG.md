# Spell balance settings

Edit `config/godwand-server.json`, then restart the world or server. Older valid files automatically gain these options without losing existing choices. Server settings decide damage and cooldowns; K only changes your own graphics and wand appearance.

## Cooldowns

Values are in ticks: **20 ticks = 1 second**. Lists run from charge level 1 to the highest charge level. Set an entry to 0 for no cooldown. The existing cooldownMultiplier still scales each value.

| Setting | Default | Applies to |
| --- | --- | --- |
| spatialPathCooldownTicks | 20 | Path on/off |
| singularityCooldownTicks | [20,20,20] | Singularity levels 1–3 |
| earthDrillCooldownTicks | [35,35,35,35] | Earth Drill levels 1–4 |
| stoneCannonCooldownTicks | [30,30,30,30] | Stone Cannon levels 1–4 |
| supernovaCooldownTicks | 520 | Supernova |
| atomicCooldownTicks | 600 | Atomic |

The wand still shares its cooldown across skills. These options set how long each cast starts that cooldown; they do not create six independent timers. Charge durations and cinematic timing are unchanged.

## Damage

Some spells hit repeatedly or lose damage with distance. Their settings multiply the existing damage instead of replacing all those hits with one fixed number. **1 keeps the current damage, 2 doubles it, 0 disables it.** The global damageMultiplier is applied once afterward. Physics and visual strength do not change when you adjust damage.

| Setting | Default | Applies to |
| --- | --- | --- |
| singularityDamageMultipliers | [1,1,1] | Collapse and burial damage, levels 1–3 |
| earthDrillDamageMultipliers | [1,1,1,1] | Gathering, drill and final slam damage, levels 1–4 |
| stoneCannonDamageMultipliers | [1,1,1,1] | Direct and nearby blast damage, levels 1–4 |
| supernovaDamageMultiplier | 1 | Core and explosion damage |
| supernovaInstantKillHealthThreshold | 50 | Enemies with this maximum health or less can be killed outright;0 disables this rule |
| atomicDamage | 9999 | Atomic center damage before distance falloff |
| atomicShockwaveDamage | 15 | Each successful outer wave hit |

Spatial Path has no separate spell damage to tune. Its shield keeps normal projectile behavior.

To reduce Supernova damage for all enemies, lower its multiplier **and set supernovaInstantKillHealthThreshold to 0**. Setting its multiplier to 0 also disables instant kills.

Example: this makes level 1 Singularity harmless, gives levels 2/3 half damage, and gives Earth Drill increasing cooldowns:

```json
{
  "singularityDamageMultipliers": [0, 0.5, 0.5],
  "earthDrillCooldownTicks": [20, 40, 60, 80],
  "supernovaDamageMultiplier": 0.5,
  "supernovaInstantKillHealthThreshold": 0
}
```

Merge those keys into your file; do not replace your other settings. Missing list entries keep their defaults. Invalid list lengths are corrected; damage multipliers are limited to 0–1000, cooldowns to 0–72000 ticks, and non-finite values fall back to defaults. Existing terrain options remain separate.

## Normal attacks

The wand deals **5 attack damage** at full attack charge before armor, enchantments and other modifiers. All three appearances have the same damage. The appearance menu stays cosmetic, so choosing a different look cannot change server combat rules.

## Version status

These settings are in the **1.3.2** preview for the four existing Minecraft 1.20.1/1.21.1 builds. The Fabric 26.3 test build includes the same settings. NeoForge 26.3 and Minecraft 1.21.11 are pending; see ports/README.md.

Verification (2026-10-28): all four supported loader/version builds passed and 212 common tests passed. Checks cover config migration, missing/invalid tier arrays, global and tier scaling, zero damage/cooldown, minimum positive cooldown, the five-damage melee attribute and parity of damage/cooldown call sites in both 1.20.1 ports. Packaged versions and original SFX bytes were checked; released 1.3.1 checksums remain unchanged. No native gameplay or multiplayer testing was run.
