# God Wand (Fabric 1.21.1)

God Wand adds a multi-function magic wand to Minecraft with 5 different skills. The wand can be used for exploration, crowd control, and combat. It includes a survival crafting recipe and its own dedicated Creative tab.

---

## Creative Tab & Crafting Recipe

### Creative Tab
The wand has its own creative tab named **God Wand**, and is also available in the **Combat** tab.

### Survival Crafting Recipe
Crafted at a standard Crafting Table using end-game materials:

- Top row: Echo Shard | Nether Star | Echo Shard
- Middle row: Amethyst Shard | Netherite Ingot | Amethyst Shard
- Bottom row: [Empty] | Blaze Rod | [Empty]

Ingredients summary:
- 1x Nether Star
- 1x Netherite Ingot
- 2x Echo Shards
- 2x Amethyst Shards
- 1x Blaze Rod

---

## The 5 Skills

To switch skills: hold **V** while holding the wand, move the mouse toward a skill, then release **V**. Release in the center or press **Esc** to keep the current skill. The wheel does not pause the game and is unavailable while charging. Rebind V in **Controls > God Wand**. **Shift + Right-Click** still cycles skills.

### Skill 1: Spatial Path (Tunnel & Bridge)
- **Controls:** Right-Click to toggle on or off. Tap Shift to toggle Bridge mode.
- **Tunnel:** Walk or sprint into hills and mountains. A 3x4 tunnel opens ahead of you and closes automatically behind you. Blocks return to normal with zero permanent terrain damage.
- **Bridge:** When bridge mode is active, solid blocks temporarily form beneath your feet when walking over gaps or cliffs, letting you cross safely.
- **Shield:** Creates a protective barrier around you that deflects incoming enemy arrows, tridents, and fireballs, and pushes approaching mobs away. The front of the barrier fades in first-person view so it does not block your sight.

### Skill 2: Singularity (Gravity Trap & Early Recall)
- **Controls:** Hold Right-Click to charge, release to fire the gravity orb. Left-Click (punch) or Right-Click while the orb is active to collapse it immediately.
- **Effect:** Launches a gravity orb that lifts nearby ground blocks into the air, tethered by purple beams.
- **Early Recall & Damage:** When the orb collapses (either automatically or triggered early), all floating blocks snap back to their original positions. Mobs caught inside take heavy crushing damage (70 damage to hostile mobs) and suffocate in the closing ground.
- **Terrain:** 100% restored. Does not destroy the landscape.

### Skill 3: Earth Drill
- **Controls:** Hold Right-Click to charge (up to 4 levels), release to fire.
- **Effect:** Pulls ground blocks into the air, forming a spinning boulder drill with wind effects above the target area.
- **Impact:** The drill slams into the ground, dealing up to 160+ damage to mobs and creating a crater.
- **Terrain:** Leaves a permanent crater.

### Skill 4: Supernova
- **Controls:** Hold Right-Click for 7 seconds (requires full charge). Range up to 180 blocks.
- **Target Marker:** Starting the seven-second charge locks the target. A violet light column marks the future black-hole position; there is no distance cursor.
- **Effect:** Spawns a floating black hole that pulls nearby blocks inward over several seconds.
- **Explosion:** Detonates with an anime-style visual flash, camera shake, and a massive 96-block blast radius. Deals lethal damage to regular mobs and bosses (including Wither and Ender Dragon).
- **Terrain:** Leaves a massive permanent crater with its natural exposed floor.

### Skill 5: Stone Cannon
- **Controls:** Hold Right-Click to charge (levels 1 to 4), release to fire.
- **Effect:** A spinning stone drill forms in front of you while charging. Releasing fires a high-speed projectile forward with noticeable recoil.
- **Damage:** Deals 130 / 240 / 370 / 520 direct damage to hostile mobs depending on charge level.
- **Terrain:** Bores a cone-shaped drill hole permanently into walls or terrain upon impact.

---

## Controls Summary

| Action | Skill Mode | Description |
| :--- | :--- | :--- |
| Hold V | Wand held | Move mouse to a skill, release to select; center or Esc cancels |
| Shift + Right-Click | Any | Switch to next skill (1 to 5) |
| Right-Click | Skill 1 (Spatial Path) | Turn tunnel and shield on or off |
| Shift (Tap) | Skill 1 (Spatial Path) | Turn auto-bridge on or off |
| Hold & Release Right-Click | Skill 2 (Singularity) | Charge and fire gravity orb |
| Left-Click or Right-Click | Skill 2 (Active) | Instantly collapse gravity orb early |
| Hold & Release Right-Click | Skill 3 (Earth Drill) | Charge and launch earth drill |
| Hold Right-Click (7s) | Skill 4 (Supernova) | Lock target and unleash black hole explosion |
| Hold & Release Right-Click | Skill 5 (Stone Cannon) | Charge drill and fire high-speed shot |

---

## Installation Requirements

- **Minecraft:** 1.21.1
- **Mod Loader:** Fabric Loader (0.16.0 or newer)
- **Dependencies:** Fabric API
- **Java:** Java 21

Place `godwand-1.0.0.jar` into your `.minecraft/mods` folder.

---

## License & Modpacks

- Open-source under the MIT License.
- You are free to use this mod in any modpack.
