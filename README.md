# MineGoals

A multi-loader Minecraft **26.3** mod (**Fabric** and **NeoForge**): personal goals organised into
**categories** and **tiers**, battle-pass style, fully defined by datapacks.

- Each completed goal earns **points** for its category.
- Reaching a points threshold unlocks the next **tier**, with its reward and new goals.
- Total points give a **global level**: one level every 100 points.
- In-game **HUD** (press **H** to hide it) and a dedicated **screen** (press **G**).
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
  "tiers": [
    { "points": 0 },
    { "points": 30, "rewards": { "items": [{"id": "minecraft:cod", "count": 16}] } },
    { "points": 75, "rewards": { "experience": 100, "commands": ["say @s is a fishing pro"] } }
  ]
}
```
- `tiers`: the first tier is at 0 points, and thresholds must strictly increase.
- A tier's reward is granted once, when the tier is reached.

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
| `rewards` | none | `items`, `experience`, `commands` (run as the player, permission level 2) |

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

A goal only counts progress once it is unlocked.
The mod ships example content (Mining, Combat, Exploration, Crafting; 32 goals) in
`common/src/main/resources/data/minegoals/`. A datapack can override these files.

## Commands (operators)

- `/minegoals list [player]`: level, tiers and the state of every goal.
- `/minegoals reset <player> [goal|category]`: resets progress (everything if nothing is given).
- `/minegoals complete <player> <goal>`: completes a goal, even a locked one, and grants its rewards.

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
