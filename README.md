# MineGoals

A multi-loader Minecraft **26.3** mod (**Fabric** and **NeoForge**): personal goals organised into
**categories** and **tiers**, battle-pass style, fully defined by datapacks.

- Each completed goal earns **points** for its category.
- Reaching a points threshold unlocks the next **tier**, with its reward and new goals.
- Goal and tier **rewards are claimed** by the player with a "Claim" button in the goals screen.
- Each category chooses whether players can see tiers they have not reached yet.
- Total points give a **global level**: one level every 100 points.
- In-game **HUD** (press **H** to hide it, **J** to show the next category) and a dedicated **screen** (press **G**).
- Criteria cover mining, killing, collecting, crafting, holding items, visiting biomes and dimensions, vanilla
  statistics and vanilla advancements.
- Works in singleplayer and on dedicated servers. Progress is stored server-side, per player.

## Project structure

| Folder | Purpose |
|---|---|
| `common/` | All mod code (vanilla only), example content and translations |
| `fabric/` | Fabric glue: networking, reload listener, HUD, key bindings, commands |
| `neoforge/` | NeoForge glue: the same, through NeoForge events |
| `build-logic/` | Shared Gradle scripts |

Code in `common/` knows nothing about Fabric or NeoForge. Anything loader-specific goes through the
services in `common/.../platform/services/` (`IPlatformHelper`, `INetworkHelper`), declared in `META-INF/services/`.

Key packages in `common/src/main/java/com/eikins/minegoals/`:
- `data/`: JSON formats (`Category`, `Goal`, criteria, rewards) and loading (`GoalDataLoader`).
- `progress/`: per-player progress (`PlayerGoalProgress`), persistence (`GoalProgressData`), logic (`GoalTracker`).
- `mixin/ServerPlayerMixin`: hooks vanilla statistics and a once-per-second tick into `GoalTracker`.
- `network/`: server → client payloads.
- `client/`: HUD, screen, toast, key bindings.
- `command/`: `/minegoals`.

## Creating goals (datapack)

### Category: `data/<namespace>/minegoals/category/<name>.json`
```json
{
  "title": {"translate": "category.mypack.fishing"},
  "icon": "minecraft:fishing_rod",
  "order": 10,
  "tier_visibility": "next",
  "tiers": [
    { "points": 0 },
    { "points": 30, "rewards": { "items": [{"id": "minecraft:cod", "count": 16}] } },
    { "points": 75, "rewards": { "experience": 100, "commands": ["say @s is a fishing pro"] } }
  ]
}
```
- `tiers`: the first tier is at 0 points, and thresholds must strictly increase.
- A tier's reward can be claimed once, after the tier is reached.
- `tier_visibility` (default `visible`) controls what players see of tiers they have not reached:

| Value | Track shows | Goals and rewards of unreached tiers |
|---|---|---|
| `visible` | every tier | shown |
| `next` | reached tiers, then the next one as "???" with its points threshold | hidden |
| `hidden` | reached tiers only (the HUD also hides the next threshold) | hidden |

Visibility is cosmetic: clients still receive every definition.

### Goal: `data/<namespace>/minegoals/goal/<name>.json`
```json
{
  "category": "mypack:fishing",
  "tier": 2,
  "points": 15,
  "title": "Seasoned Angler",
  "description": "Pick up 32 cod",
  "icon": "minecraft:cod",
  "requires": ["mypack:first_fish"],
  "criterion": { "type": "minegoals:collect_item", "item": "minecraft:cod", "count": 32 },
  "rewards": { "items": [{"id": "minecraft:diamond", "count": 1}], "experience": 20, "commands": [] }
}
```

| Field | Default | Description |
|---|---|---|
| `category` | required | Category id |
| `tier` | `1` | Category tier that unlocks the goal |
| `points` | `10` | Points earned on completion |
| `title`, `description` | | Plain text or a JSON text component (`{"translate": ...}`) |
| `icon` | required | Item id |
| `requires` | `[]` | Goals to complete first (in addition to the tier) |
| `criterion` | required | See below |
| `rewards` | none | `items`, `experience`, `commands` (run as the player, permission level 2), claimed from the goals screen |

A goal whose category, tier or `requires` entry does not exist is skipped, with a warning in the logs.

### Criteria
The `block`, `entity`, `item` and `biome` fields accept an id (`minecraft:stone`) or a tag (`#minecraft:logs`).

| `type` | Fields | Counted when… |
|---|---|---|
| `minegoals:break_block` | `block`, `count` | the player breaks the block (vanilla "mined" stat) |
| `minegoals:kill_entity` | `entity`, `count` | the player kills the entity |
| `minegoals:collect_item` | `item`, `count` | the player picks the item up from the ground |
| `minegoals:craft_item` | `item`, `count` | the player crafts or smelts the item |
| `minegoals:hold_item` | `item`, `count` | the inventory holds `count` items at the same time |
| `minegoals:visit_biome` | `biome` | the player is in the biome |
| `minegoals:visit_dimension` | `dimension` | the player is in the dimension |
| `minegoals:custom_stat` | `stat`, `count` | a vanilla "custom" statistic grows, e.g. `minecraft:fish_caught`, `minecraft:traded_with_villager`, `minecraft:animals_bred`, `minecraft:enchant_item` (see `Stats.java`) |
| `minegoals:advancement` | `advancement` | the player has the advancement, e.g. `minecraft:story/follow_ender_eye` (earned before the goal unlocked counts too) |

A goal only counts progress once it is unlocked.

### Rewards
Nothing is granted automatically. When a goal is completed or a tier is reached, the player gets a toast and a
chat message; the HUD and the screen show how many rewards are waiting. In the screen, the selected tier's reward
appears above its goals, and completed goals show a **Claim** button. The server checks every claim, so a reward
can only be claimed once.
Goal files can be grouped in sub-folders: `goal/road_to_end/first_table.json` has the id
`minegoals:road_to_end/first_table`.

### Example content
The mod ships 9 categories and 137 goals in `common/src/main/resources/data/minegoals/`. A datapack can override
these files.

| Category | Tiers | Idea |
|---|---|---|
| Road to the End | 12, `next` | A guided path from the first log to the Ender Dragon: stone, iron, diamonds, Nether fortress, blaze rods, ender pearls, stronghold, preparation, the End. Each tier needs every goal of the previous ones. |
| Oceans | 4 | Fishing, drowned, coral reefs, ocean monuments, conduit |
| Farms & Animals | 4 | Crops, breeding, taming, bees, sniffers |
| Villages & Trading | 4 | Villagers, trading, iron golems, raids |
| Magic | 4 | Enchanting, anvils, brewing, beacons |
| Caves & Depths | 4, `next` | Deepslate, geodes, deep dark, sculk, trial chambers, warden |
| Advanced Nether | 4 | Nether biomes, bastions, striders, netherite, the Wither |
| World Exploration | 4 | Biomes, maps, archaeology, lodestones, End cities, elytra |
| Combat | 4 | Common mobs, ranged combat, raids, breezes, the mace |

Themed categories unlock a tier at about 75% of the previous goals' points, so players can skip a few goals.
The content and both lang files are generated by `tools/generate_content.py` (run `python tools/generate_content.py .`
from the project root). Edit the script rather than the generated JSON, or your changes will be overwritten.
`GoalDataTest` checks that every advancement, biome, tag, block, entity and statistic referenced exists.

## Commands (operators)

- `/minegoals list [player]`: level, tiers and the state of every goal.
- `/minegoals reset <player> [goal|category]`: resets progress and claims (everything if nothing is given).
- `/minegoals complete <player> <goal>`: completes a goal, even a locked one; its rewards become claimable.

## Development

Requirements: JDK **25**, IntelliJ IDEA (Eclipse is not supported).

```bash
./gradlew build                 # builds both loaders and runs the tests
./gradlew :common:test          # unit tests (JSON formats, points, tiers, unlocking)
./gradlew :fabric:runClient     # Minecraft with Fabric
./gradlew :neoforge:runClient   # Minecraft with NeoForge
./gradlew :fabric:runServer     # Fabric test server
./gradlew :neoforge:runServer   # NeoForge test server
```

Jars are written to `fabric/build/libs/` and `neoforge/build/libs/`.
Versions (mod, Minecraft, Fabric, NeoForge) live in `gradle.properties`.

## To do

- Add a `minegoals.png` icon to `common/src/main/resources/`.
- Pick a license (currently `All-Rights-Reserved`).
- Seasons (v2): add an optional `season` field to categories.
