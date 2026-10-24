# God Wand (Fabric & NeoForge 1.21.1)

A magic wand mod for Minecraft 1.21.1 supporting both Fabric Loader and NeoForge. Features 5 distinct skills for exploration and combat, full survival crafting, custom visual post-effects, and a dedicated creative tab.

---

## Features

- **Skill 1 - Spatial Path:** Walk through mountains by opening a 3x4 tunnel that seals itself behind you with zero terrain loss. Tap Shift to build a temporary 3-wide bridge across gaps. Projects a personal shield that deflects arrows, tridents, and fireballs.
- **Skill 2 - Singularity:** Fires a gravity orb that pulls ground blocks into the air. Punch or right-click to trigger an early collapse, crushing and suffocating trapped mobs. Automatically restores the terrain after.
- **Skill 3 - Earth Drill:** Pulls ground blocks up into a spinning boulder drill, then slams down into the ground to deal heavy area damage and leave a permanent crater.
- **Skill 4 - Supernova:** A long-range (180 blocks) 7-second charged spell with a locked-target light column while charging. Spawns a black hole that pulls in blocks and detonates in a colossal 120-meter spherical blast with an anime-style visual flash, custom sound sequence, and devastating damage.
- **Skill 5 - Stone Cannon:** Charge up to 4 levels to form a spinning stone drill in front of you. Release to fire a high-speed projectile that deals up to 520 direct damage to hostile mobs and bores a cone-shaped hole into the target.

---

## Creative Tab & Crafting

- **Creative Tab:** Found in its own dedicated tab named **God Wand**, as well as in the **Combat** tab.
- **Crafting Recipe (Crafting Table):**
  - Row 1: Echo Shard | Nether Star | Echo Shard
  - Row 2: Amethyst Shard | Netherite Ingot | Amethyst Shard
  - Row 3: [Empty] | Blaze Rod | [Empty]

---

## Controls

| Keybind | Context | Action |
| :--- | :--- | :--- |
| Hold V | Wand held, not charging | Open the skill wheel; move the mouse and release V to select. Center or Esc cancels. Rebind in Controls > God Wand. |
| Right-Click | Skill 1 | Toggle tunnel and shield on / off |
| Shift | Skill 1 (Active) | Toggle bridge on / off |
| Hold & Release Right-Click | Skill 2 | Charge and shoot gravity orb |
| Left-Click / Right-Click | Skill 2 (Active) | Force early collapse of gravity orb |
| Hold & Release Right-Click | Skill 3 | Charge and launch earth drill |
| Hold Right-Click (7s) | Skill 4 | Cast black hole supernova |
| Hold & Release Right-Click | Skill 5 | Charge and fire stone cannon |

---

## Building from Source

This project uses a clean Multi-Loader architecture:
- `:common`: Shared game logic, skills, renderers, shaders, and assets.
- `:fabric`: Fabric loader subproject (Minecraft 1.21.1).
- `:neoforge`: NeoForge loader subproject (Minecraft 1.21.1).

To build all loader jars simultaneously:
```bash
./gradlew build
```

Individual subproject builds:
```bash
# Build Fabric jar: fabric/build/libs/godwand-fabric-1.1.0.jar
./gradlew :fabric:build

# Build NeoForge jar: neoforge/build/libs/godwand-neoforge-1.1.0.jar
./gradlew :neoforge:build
```

---

## Installation

### Fabric 1.21.1
1. Install Minecraft 1.21.1 with Fabric Loader (>= 0.16.0).
2. Install Fabric API for 1.21.1 into your `mods` folder.
3. Place `godwand-fabric-1.1.0.jar` into your `mods` folder.

### NeoForge 1.21.1
1. Install Minecraft 1.21.1 with NeoForge (>= 21.1.72).
2. Place `godwand-neoforge-1.1.0.jar` into your `mods` folder.
