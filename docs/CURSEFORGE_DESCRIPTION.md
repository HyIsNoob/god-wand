# God Wand (Fabric & NeoForge 1.21.1)

God Wand adds a multi-function magic wand to Minecraft with 5 unique skills for exploration, terrain control, and combat. It includes survival crafting and a dedicated creative tab.

Available for both **Fabric** and **NeoForge** on Minecraft 1.21.1.

---

## The 5 Skills

Switch skills quickly by holding **V** to open the skill wheel, or by pressing **Shift + Right-Click** to cycle through them.

### Skill 1: Spatial Path (Tunnel & Bridge)
- **Controls:** Right-Click to toggle on or off. Tap Shift while active to toggle Bridge mode.
- **Tunnel:** Walk straight into hills and mountains. A 3x4 tunnel opens ahead of you and seals itself cleanly behind you without breaking blocks.
- **Bridge:** Walk over cliffs and gaps; temporary blocks form under your feet so you can cross safely.
- **Shield:** Surrounds you with a protective barrier that deflects arrows, tridents, and fireballs, and pushes approaching mobs away. Fades out in first-person so your view stays clear.

### Skill 2: Singularity (Gravity Trap & Early Recall)
- **Controls:** Hold Right-Click to charge (spins a cyan energy ball in front of the wand), release to fire. Left-Click or Right-Click while active to collapse early.
- **Effect:** Launches a gravity orb that lifts ground blocks and nearby mobs into the air.
- **Damage:** Collapsing the orb snaps blocks back into place, crushing trapped regular mobs (< 50 HP). Bosses and high-health mobs take heavy burst damage.
- **Terrain:** 100% restored after the effect ends.

### Skill 3: Earth Drill
- **Controls:** Hold Right-Click to charge (up to 4 levels), release to fire.
- **Effect:** Pulls ground blocks into the air into a spinning boulder drill with wind effects above the target area.
- **Impact:** Slams into the ground, dealing heavy area damage to mobs and carving out a permanent crater.

### Skill 4: Supernova
- **Controls:** Hold Right-Click for 7 seconds to charge (range up to 180 blocks).
- **Targeting:** A light column marks and locks onto the target location while charging.
- **Effect:** Summons a black hole that pulls in surrounding blocks and explodes in a massive blast. Regular mobs are eliminated, while bosses take heavy burst damage.
- **Terrain:** Leaves a permanent crater.

### Skill 5: Stone Cannon
- **Controls:** Hold Right-Click to charge (levels 1 to 4), release to fire.
- **Effect:** A spinning stone drill forms in front of you while charging. Releasing fires a high-speed projectile that pierces mobs and drills a hole into walls.
- **Damage:** Deals 130 / 240 / 370 / 520 damage depending on charge level.

---

## Controls Summary

| Action | Skill Mode | Description |
| :--- | :--- | :--- |
| Hold V | Any | Open skill wheel to select a skill |
| Shift + Right-Click | Any | Cycle to next skill |
| Right-Click | Skill 1 (Spatial Path) | Turn tunnel and shield on / off |
| Shift (Tap) | Skill 1 (Active) | Turn auto-bridge on / off |
| Hold & Release Right-Click | Skill 2 (Singularity) | Charge and fire gravity orb |
| Left-Click or Right-Click | Skill 2 (Active) | Collapse gravity orb early |
| Hold & Release Right-Click | Skill 3 (Earth Drill) | Charge and launch earth drill |
| Hold Right-Click (7s) | Skill 4 (Supernova) | Lock target and cast black hole explosion |
| Hold & Release Right-Click | Skill 5 (Stone Cannon) | Charge drill and fire high-speed shot |

---

## Crafting & Creative Tab

### Creative Tab
Found in its own tab named **God Wand**, as well as in the **Combat** tab.

### Survival Crafting Recipe
Crafted at a standard Crafting Table using:
- Top row: Echo Shard | Nether Star | Echo Shard
- Middle row: Amethyst Shard | Netherite Ingot | Amethyst Shard
- Bottom row: [Empty] | Blaze Rod | [Empty]

---

## Installation Requirements

### Fabric 1.21.1
- Minecraft 1.21.1
- Fabric Loader (0.16.0 or newer)
- Fabric API
- Java 21

### NeoForge 1.21.1
- Minecraft 1.21.1
- NeoForge (21.1.72 or newer)
- Java 21

---

## Recent Changelog (v1.1.0)

- Added NeoForge 1.21.1 support alongside Fabric 1.21.1.
- Skill 1: Added expanding shockwave activation animation. Fixed a bug where dying while the skill was active left blocks permanently displaced.
- Skill 2: Added a rotating energy ball with swirling intake particles while charging.
- Combat balance: Bosses and high-health mobs (such as the Ender Dragon and Wither) now take heavy burst damage from Skill 2 and Skill 4 instead of dying instantly. Mobs under 50 HP are still crushed immediately.
- Visual fixes: Fixed bright purple outlines on blocks when playing with shaderpacks (Complementary). Fixed lines cutting across the screen when standing close to the black hole.

---

## Modpacks & License

- MIT License. You are free to include this mod in any modpack.
