# MineGoals

Mod Minecraft **26.3** multi-loader (**Fabric** et **NeoForge**) : des objectifs personnels organisés en
**catégories** et en **paliers**, façon battlepass, entièrement définis par datapack.

- Chaque objectif terminé rapporte des **points** à sa catégorie.
- Un seuil de points débloque le **palier** suivant, avec sa récompense et ses nouveaux objectifs.
- La somme des points donne un **niveau global** : 1 niveau tous les 100 points.
- **HUD** en jeu (touche **H** pour le masquer) et **écran** dédié (touche **G**).
- Fonctionne en solo et sur serveur dédié. La progression est stockée côté serveur, par joueur.

## Structure du projet

| Dossier | Rôle |
|---|---|
| `common/` | Tout le code du mod (vanilla uniquement) + contenu d'exemple + traductions |
| `fabric/` | Colle Fabric : enregistrement réseau, reload listener, HUD, touches, commandes |
| `neoforge/` | Colle NeoForge : idem, via les événements NeoForge |
| `build-logic/` | Scripts Gradle communs |

Le code de `common/` ne connaît ni Fabric ni NeoForge. Ce qui dépend du loader passe par les services de
`common/.../platform/services/` (`IPlatformHelper`, `INetworkHelper`), déclarés dans `META-INF/services/`.

Points clés de `common/src/main/java/com/eikins/minegoals/` :
- `data/` : formats JSON (`Category`, `Goal`, critères, récompenses) et chargement (`GoalDataLoader`).
- `progress/` : progression par joueur (`PlayerGoalProgress`), sauvegarde (`GoalProgressData`), logique (`GoalTracker`).
- `mixin/ServerPlayerMixin` : branche les statistiques vanilla et un tick par seconde sur `GoalTracker`.
- `network/` : payloads serveur → client.
- `client/` : HUD, écran, toast, touches.
- `command/` : `/minegoals`.

## Créer des objectifs (datapack)

### Catégorie : `data/<namespace>/minegoals/category/<nom>.json`
```json
{
  "title": {"translate": "category.monpack.peche"},
  "icon": "minecraft:fishing_rod",
  "order": 10,
  "tiers": [
    { "points": 0 },
    { "points": 30, "rewards": { "items": [{"id": "minecraft:cod", "count": 16}] } },
    { "points": 75, "rewards": { "experience": 100, "commands": ["say @s est un pro de la pêche"] } }
  ]
}
```
- `tiers` : le premier palier est à 0 point, puis les seuils sont strictement croissants.
- La récompense d'un palier est donnée une seule fois, quand il est atteint.

### Objectif : `data/<namespace>/minegoals/goal/<nom>.json`
```json
{
  "category": "monpack:peche",
  "tier": 2,
  "points": 15,
  "title": "Pêcheur confirmé",
  "description": "Ramasse 32 morues",
  "icon": "minecraft:cod",
  "requires": ["monpack:premier_poisson"],
  "criterion": { "type": "minegoals:collect_item", "item": "minecraft:cod", "count": 32 },
  "rewards": { "items": [{"id": "minecraft:diamond", "count": 1}], "experience": 20, "commands": [] }
}
```

| Champ | Défaut | Description |
|---|---|---|
| `category` | obligatoire | Id de la catégorie |
| `tier` | `1` | Palier de la catégorie qui débloque l'objectif |
| `points` | `10` | Points gagnés à la complétion |
| `title`, `description` | | Texte simple ou composant JSON (`{"translate": ...}`) |
| `icon` | obligatoire | Id d'objet |
| `requires` | `[]` | Objectifs à terminer avant (en plus du palier) |
| `criterion` | obligatoire | Voir ci-dessous |
| `rewards` | aucune | `items`, `experience`, `commands` (exécutées en tant que joueur, niveau 2) |

Un objectif dont la catégorie, le palier ou un `requires` n'existe pas est ignoré, avec un avertissement dans les logs.

### Critères
Les champs `block`, `entity`, `item` et `biome` acceptent un id (`minecraft:stone`) ou un tag (`#minecraft:logs`).

| `type` | Champs | Compté quand… |
|---|---|---|
| `minegoals:break_block` | `block`, `count` | le joueur casse le bloc (stat vanilla « miné ») |
| `minegoals:kill_entity` | `entity`, `count` | le joueur tue l'entité |
| `minegoals:collect_item` | `item`, `count` | le joueur ramasse l'objet au sol |
| `minegoals:craft_item` | `item`, `count` | le joueur fabrique ou fait cuire l'objet |
| `minegoals:hold_item` | `item`, `count` | l'inventaire contient `count` objets en même temps |
| `minegoals:visit_biome` | `biome` | le joueur se trouve dans le biome |
| `minegoals:visit_dimension` | `dimension` | le joueur se trouve dans la dimension |

La progression d'un objectif ne compte qu'à partir de son déblocage.
Le mod fournit un contenu d'exemple (Minage, Combat, Exploration, Artisanat ; 32 objectifs) dans
`common/src/main/resources/data/minegoals/`. Un datapack peut remplacer ces fichiers.

## Commandes (opérateurs)

- `/minegoals list [joueur]` : niveau, paliers, état de chaque objectif.
- `/minegoals reset <joueur> [objectif|catégorie]` : remise à zéro (tout si rien n'est précisé).
- `/minegoals complete <joueur> <objectif>` : termine un objectif, même verrouillé, avec ses récompenses.

## Développement

Prérequis : JDK **25**, IntelliJ IDEA (Eclipse n'est pas supporté).

```bash
./gradlew build                 # compile les deux loaders + tests
./gradlew :common:test          # tests unitaires (formats JSON, points, paliers, déblocages)
./gradlew :fabric:runClient     # Minecraft avec Fabric
./gradlew :neoforge:runClient   # Minecraft avec NeoForge
./gradlew :fabric:runServer     # serveur de test Fabric
./gradlew :neoforge:runServer   # serveur de test NeoForge
```

Les jars se trouvent dans `fabric/build/libs/` et `neoforge/build/libs/`.
Versions (mod, Minecraft, Fabric, NeoForge) : `gradle.properties`.

## À faire

- Ajouter une icône `minegoals.png` dans `common/src/main/resources/`.
- Choisir une licence (actuellement `All-Rights-Reserved`).
- Saisons (v2) : prévoir un champ `season` optionnel sur les catégories.
