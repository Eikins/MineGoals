# MineGoals

A multi-loader Minecraft **26.3** mod (**Fabric** and **NeoForge**): personal goals organised into
**categories** and **tiers**, battle-pass style, fully defined by datapacks.

- Each goal earns **points** for its tier when the player **claims** it.
- Each **tier** needs a number of points from its own goals. Reaching it completes the tier; claiming the tier's
  reward unlocks the next tier and its goals.
- Goal and tier **rewards are claimed** by the player with a "Claim" button in the goals screen.
- Each category chooses whether players can see tiers they have not reached yet, and whether it is available from
  the start or unlocked later (vanilla advancements or a command).
- A **journal icon** in the bottom-right corner of the HUD shows the key that opens the goals **screen** (**G**), with
  the vanilla "!" badge when a reward is ready to be claimed.
- The goals screen looks like the vanilla advancements screen and is drawn with vanilla GUI sprites (window, tabs,
  advancement frames and title boxes, slots, buttons, experience bar).
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
    { "points": 20 },
    { "points": 45, "rewards": { "items": [{"id": "minecraft:cod", "count": 16}] } },
    { "points": 60, "rewards": { "experience": 100, "commands": ["say @s is a fishing pro"] } }
  ]
}
```
- `background` (optional): texture tiled behind the category in the goals screen, e.g.
  `minecraft:textures/gui/advancements/backgrounds/nether.png` (vanilla has `stone`, `adventure`, `husbandry`,
  `nether` and `end`). Default: `stone`.
  A tier can set its own `background` too (`{"points": 30, "background": "..."}`); it replaces the category's while
  that tier is selected. Road to the End uses this to follow the journey (overworld, Nether, stronghold, End).
- `tiers`: at least one tier. Each tier's `points` is how many points the player must earn **from that tier's own
  goals** (any non-negative number; every tier counts from 0).
- `unlock` (optional) controls when the category is available. Without it, the category is unlocked for everyone:
  ```json
  "unlock": {"default": false, "mode": "any", "advancements": ["minecraft:story/enter_the_nether"]}
  ```
  | Field | Default | Description |
  |---|---|---|
  | `default` | `true` | Unlocked from the start. When `false`, the category is **hidden** until it unlocks |
  | `advancements` | `[]` | Vanilla (or datapack) advancements that unlock it; checked every second |
  | `mode` | `all` | `all`: every advancement is needed; `any`: one is enough |

  When a category unlocks (by advancement or command), the player gets a toast in the top-right corner with the
  category's icon and name. A locked category with no `advancements` only unlocks through `/minegoals unlock`. Once unlocked, a category stays
  unlocked (unless `/minegoals lock` is used). Goals of a locked category only count progress if they use
  `track_before_unlock`; nothing can be claimed until the category unlocks.
- Tier N is **complete** when the claimed goals of tier N add up to its `points`. Goals of other tiers never count
  towards it, so a surplus in one tier does not carry over to the next.
- A tier's `rewards` are earned by completing that tier. The player claims them manually, and claiming them
  **unlocks the next tier**. A tier without rewards unlocks the next one as soon as it is complete.
- `tier_visibility` (default `visible`) controls what players see of tiers they have not reached:

| Value | Track shows | Goals and rewards of unreached tiers |
|---|---|---|
| `visible` | every tier | shown |
| `next` | reached tiers, then the next one as "???" | hidden |
| `hidden` | reached tiers only; progress shows the points earned but not the points needed | hidden |

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
| `tier` | `1` | Category tier that unlocks the goal (unlocked once the previous tier is complete and claimed) |
| `points` | `10` | Points earned when the completed goal is claimed |
| `title`, `description` | | Plain text or a JSON text component (`{"translate": ...}`) |
| `icon` | required | Item id |
| `requires` | `[]` | Goals to complete first (in addition to the tier) |
| `criterion` | required | See below |
| `track_before_unlock` | `false` | Count progress while the goal's tier is not reached yet; the goal completes as soon as the tier unlocks. `requires` still blocks counting |
| `rewards` | none | `items` (with optional data components), `experience`, `commands`; claimed from the goals screen (see Rewards) |

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
| `minegoals:category_tier` | `category`, `tier` | the given tier of a category (often another one) is complete, i.e. its claimed goals reach the points it needs; claiming the tier's reward is not required |

By default a goal only counts progress once it is unlocked. Set `track_before_unlock` to count it while its tier
is still locked, so players do not have to do the work again after reaching the tier.

### Rewards
`rewards` is shared by goals and tiers:
```json
"rewards": {
  "experience": 50,
  "items": [
    "minecraft:diamond",
    {"id": "minecraft:arrow", "count": 32},
    {"id": "minecraft:diamond_sword", "components": {
      "minecraft:enchantments": {"minecraft:sharpness": 5, "minecraft:unbreaking": 3},
      "minecraft:custom_name": {"text": "Excalibur", "color": "gold", "italic": false},
      "minecraft:lore": [{"text": "Forged for a true goal hunter", "color": "gray"}]
    }}
  ],
  "commands": ["effect give @s minecraft:speed 60"]
}
```
- `items` uses the vanilla item format: an item id, or `{"id", "count" (1-99, default 1), "components"}`.
  `components` are the same data components as `/give minecraft:diamond_sword[enchantments={...}]`, written in JSON
  with their full id (`minecraft:enchantments`, `minecraft:custom_name`, `minecraft:lore`, `minecraft:rarity`...).
  Enchantments and other registry entries are checked when the datapack loads; an invalid reward makes the goal or
  category fail to load, with the error in the logs.
- `experience` is given as experience points.
- `commands` run as the player (`@s`) with permission level 2. Each entry is a command string (shown as a nether
  star named "Special reward"), or an object that also says how the reward looks in the goals screen:
  ```json
  {"run": ["effect give @s minecraft:speed 120", "playsound minecraft:entity.player.levelup player @s"],
   "icon": {"id": "minecraft:potion", "components": {"minecraft:potion_contents": {"potion": "minecraft:swiftness"}}},
   "name": "Speed boost",
   "description": "Two minutes of Speed"}
  ```
  `run` is one command or a list. `icon` is an item (id or `{"id", "components"}`) or a texture:
  `{"texture": "minegoals:textures/gui/reward/speed.png"}` (full path, drawn at 16x16; textures come from a mod or
  resource pack, not from a datapack). `name` and `description` are text or JSON text components.

Nothing is granted automatically. When a goal or a tier is completed, a green dot appears on the journal icon and the
screen shows how many rewards are waiting (plus a toast and a chat message when `NOTIFICATIONS` is on). In the
screen, each tier box on the track only shows its number and state colour (in progress, ready to claim, done, locked).
The first row of the list is the selected tier: its reward, its state and its progress (points earned from the
tier's goals / points it needs). Every row shows its rewards as icons in three fixed slots on the
right: experience first as an XP bottle, then items, then commands as a nether star. Extra rewards are listed in the
row's tooltip, and claimed rewards turn green with a check mark. The selected tier stays pinned above the goals, which scroll;
claimed goals are listed last, darkened by a mask texture. The
tier reward row and every completed goal have a **Claim** button. A goal's points are only added to its category when
it is claimed, so claiming goals is what moves the player along the tiers; a goal without rewards is still claimed for
its points. The server checks
every claim, so a reward can only be claimed once. Clicking Claim plays a UI sound on the client: an experience pickup
for a goal, the level-up sound for a tier.

Goal files can be grouped in sub-folders: `goal/road_to_end/first_table.json` has the id
`minegoals:road_to_end/first_table`.

### Example content
The mod ships 10 categories and 152 goals in `common/src/main/resources/data/minegoals/`. A datapack can override
these files.

| Category | Tiers | Idea |
|---|---|---|
| Road to the End | 12, `next` | A guided path from the first log to the Ender Dragon: stone, iron, diamonds, Nether fortress, blaze rods, ender pearls, stronghold, preparation, the End. Each tier needs all of its goals, and each tier reward prepares the next step. |
| Oceans | 4 | Fishing, drowned, coral reefs, ocean monuments, conduit |
| Farms & Animals | 4 | Crops, breeding, taming, bees, sniffers |
| Villages & Trading | 4 | Villagers, trading, iron golems, raids |
| Magic | 4 | Enchanting, anvils, brewing, beacons |
| Caves & Depths | 4, `next` | Deepslate, geodes, deep dark, sculk, trial chambers, warden |
| Advanced Nether | 4, hidden until the player enters the Nether | Nether biomes, bastions, striders, netherite, the Wither |
| World Exploration | 4 | Biomes, maps, archaeology, lodestones, End cities, elytra |
| Combat | 4 | Common mobs, ranged combat, raids, breezes, the mace |
| Redstone | 4, hidden until unlocked by a reward | Redstone components, pistons, observers, comparators, crafters. Claiming Road to the End's "First Table" goal runs `/minegoals unlock @s minegoals:redstone` (a command reward with its own icon and name) |

Themed tiers need about 75% of their goals' points, so players can skip a few goals, and
their goals use `track_before_unlock`. Road to the End does not: each step starts counting when it is revealed.
The content and both lang files are generated by `tools/generate_content.py` (run `python tools/generate_content.py .`
from the project root). Edit the script rather than the generated JSON, or your changes will be overwritten.
`GoalDataTest` checks that every advancement, biome, tag, block, entity and statistic referenced exists.

## Feature flags

`common/.../FeatureFlags.java` keeps finished features in the code while they are switched off. Change a flag and
rebuild to turn the feature back on.

| Flag | Default | Feature |
|---|---|---|
| `GOAL_PANEL` | `false` | Top-right goals panel (focused category, three active goals), with keys **H** (hide) and **J** (next category) |
| `NOTIFICATIONS` | `false` | Toasts and chat messages when a goal is completed or a tier is reached. The toast for a newly unlocked category is always shown |

## Commands (operators)

- `/minegoals list [player]`: points, tiers, locked categories and the state of every goal.
- `/minegoals reset <player> [goal|category]`: resets progress and claims (everything if nothing is given).
- `/minegoals unlock <player> <category>` / `/minegoals lock <player> <category>`: unlocks or hides a category for
  a player. Usable from reward commands (`/minegoals unlock @s mypack:fishing`), functions and command blocks.
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
