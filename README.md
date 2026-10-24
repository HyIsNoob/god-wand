# God Wand (Fabric & NeoForge 1.21.1)

A magic wand mod for Minecraft 1.21.1 supporting both Fabric and NeoForge. Features 5 distinct skills for exploration, terrain control, and combat, with survival crafting, custom visual effects, and a dedicated creative tab.

---

## Skills

To switch skills: hold **V** while holding the wand to open the skill wheel, move your mouse, and release. You can also press **Shift + Right-Click** to cycle through skills.

1. **Skill 1 - Spatial Path:**
   - Walk through mountains: opens a 3x4 tunnel ahead of you and seals itself behind you without damaging the terrain.
   - Tap **Shift** while active to turn on Bridge mode, creating temporary solid blocks under your feet over gaps.
   - Creates a protective shield around you that deflects incoming arrows, tridents, and fireballs.

2. **Skill 2 - Singularity:**
   - Hold and release Right-Click to launch a gravity orb that pulls nearby ground blocks and mobs into the air.
   - Left-Click (punch) or Right-Click while active to collapse the orb early, crushing trapped mobs.
   - Terrain is 100% restored after the spell ends.

3. **Skill 3 - Earth Drill:**
   - Hold and release Right-Click to pull boulders into a spinning drill and slam the ground.
   - Deals heavy area damage and leaves a permanent crater.

4. **Skill 4 - Supernova:**
   - Hold Right-Click for 7 seconds to lock onto a target up to 180 blocks away.
   - Summons a black hole that pulls in surrounding blocks, then detonates in a massive explosion.
   - Regular mobs are eliminated, while bosses (like the Ender Dragon and Wither) take massive burst damage.

5. **Skill 5 - Stone Cannon:**
   - Charge up to 4 levels to form a spinning stone drill in front of you.
   - Release to fire a high-speed piercing projectile that damages enemies and drills a hole into walls.

---

## Controls

| Action | Skill Mode | Description |
| :--- | :--- | :--- |
| Hold V | Any | Open skill wheel to pick a skill |
| Shift + Right-Click | Any | Cycle to the next skill |
| Right-Click | Skill 1 | Turn tunnel and shield on / off |
| Shift (Tap) | Skill 1 (Active) | Turn temporary bridge on / off |
| Hold & Release Right-Click | Skill 2 | Charge and shoot gravity orb |
| Left-Click / Right-Click | Skill 2 (Active) | Collapse gravity orb early |
| Hold & Release Right-Click | Skill 3 | Charge and launch earth drill |
| Hold Right-Click (7s) | Skill 4 | Cast black hole supernova |
| Hold & Release Right-Click | Skill 5 | Charge and fire stone cannon |

---

## Crafting & Creative Tab

- **Creative Tab:** Found under its own tab named **God Wand**, and also in the **Combat** tab.
- **Crafting Recipe (Crafting Table):**
  - Row 1: Echo Shard | Nether Star | Echo Shard
  - Row 2: Amethyst Shard | Netherite Ingot | Amethyst Shard
  - Row 3: [Empty] | Blaze Rod | [Empty]

---

## Installation

### Fabric 1.21.1
1. Install Fabric Loader (0.16.0 or newer).
2. Install Fabric API into your `.minecraft/mods` folder.
3. Put `godwand-fabric-1.1.0.jar` into your `.minecraft/mods` folder.

### NeoForge 1.21.1
1. Install NeoForge (21.1.72 or newer).
2. Put `godwand-neoforge-1.1.0.jar` into your `.minecraft/mods` folder.

---

## Building from Source

```bash
# Build both Fabric and NeoForge jars
./gradlew build

# Or build individually
./gradlew :fabric:build
./gradlew :neoforge:build
```

Compiled jars are placed in `build/libs/`.

---

## License

MIT License. You are free to include this mod in any modpack.
