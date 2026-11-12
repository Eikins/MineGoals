"""Generates MineGoals example content: categories, goals and en_us/fr_fr lang files.

Usage (from the project root): python tools/generate_content.py .
The script deletes and rewrites data/minegoals/minegoals/{category,goal} and the two lang files.
Goals live in a sub-folder per category, so their ids look like minegoals:progression/crafting_table.
"""
import json
import os
import shutil
import sys

root = sys.argv[1]
res = os.path.join(root, "common", "src", "main", "resources")
data = os.path.join(res, "data", "minegoals", "minegoals")
lang_dir = os.path.join(res, "assets", "minegoals", "lang")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# ---- criterion helpers ---------------------------------------------------------------------------

def B(block, n=1): return {"type": "minegoals:break_block", "block": block, "count": n}
def K(entity, n=1): return {"type": "minegoals:kill_entity", "entity": entity, "count": n}
def P(item, n=1): return {"type": "minegoals:collect_item", "item": item, "count": n}
def CR(item, n=1): return {"type": "minegoals:craft_item", "item": item, "count": n}
def U(item, n=1): return {"type": "minegoals:use_item", "item": item, "count": n}
POTION_VARIANTS = {"swiftness": ["long", "strong"], "healing": ["strong"], "fire_resistance": ["long"],
                   "strength": ["long", "strong"], "invisibility": ["long"]}
def BREW(n=1, potion=None):
    """Brew n potions; a potion name also accepts its long and strong variants."""
    crit = {"type": "minegoals:brew_potion", "count": n}
    if potion:
        crit["potions"] = ["minecraft:" + potion] + ["minecraft:%s_%s" % (v, potion) for v in POTION_VARIANTS[potion]]
    return crit
def H(item, n=1): return {"type": "minegoals:hold_item", "item": item, "count": n}
def VB(biome): return {"type": "minegoals:visit_biome", "biome": biome}
def VD(dim): return {"type": "minegoals:visit_dimension", "dimension": dim}
def ADV(path): return {"type": "minegoals:advancement", "advancement": "minecraft:" + path}
def ST(stat, n=1): return {"type": "minegoals:custom_stat", "stat": "minecraft:" + stat, "count": n}
def TIER(category, tier): return {"type": "minegoals:category_tier", "category": "minegoals:" + category, "tier": tier}
def items(*pairs): return {"items": [{"id": i, "count": c} for i, c in pairs]}
def stack(item, count=1, **components):
    """An item reward with data components, as in /give: stack("minecraft:bow", enchantments={"minecraft:power": 3})."""
    entry = {"id": item, "count": count}
    if components:
        entry["components"] = {"minecraft:" + k: v for k, v in components.items()}
    return entry

# ---- content -------------------------------------------------------------------------------------
#
# One main category, Progression, is unlocked from the start. Some of its goals unlock a secondary category when
# claimed (a command reward), and some of its tiers ask for a tier of a secondary category (TIER_GOAL).
#
# A category: dict with "tiers" = list of {"reward": tier reward, "goals": [...], optional "background"}.
# A goal: (id, icon, criterion, points, en title, en description, fr title, fr description[, extra])
# extra may hold "unlocks": "<category id>" (claiming the goal unlocks that category) or any goal JSON field.
# Progression tiers need all of their goals. A secondary tier can set "skip": that many points are not needed, so
# players can leave out a frustrating goal (a rare biome, a hard advancement...). Goal rewards are 10 XP per point.

def bg(name): return "minecraft:textures/gui/advancements/backgrounds/%s.png" % name


def potion(potion_id, count=1):
    return {"id": "minecraft:potion", "count": count,
            "components": {"minecraft:potion_contents": {"potion": potion_id}}}


def TIER_GOAL(category, tier, points=2):
    """A Progression goal asking for a tier of a secondary category; titles come from that category's names."""
    cat = SECONDARY_BY_ID[category]
    numeral = "I II III IV V VI".split()[tier - 1]
    return (f"{category}_{tier}", cat["icon"], TIER(category, tier), points,
            f"{cat['en']} {numeral}", f"Complete tier {tier} of {cat['en']}",
            f"{cat['fr']} {numeral}", f"Termine le palier {tier} de la catégorie {cat['fr']}")


# ---- secondary categories ------------------------------------------------------------------------

MINING = {
    "id": "mining", "icon": "minecraft:iron_pickaxe", "background": "stone",
    "en": "Mining", "fr": "Minage",
    "tiers": [
        {"reward": {"items": [{"id": "minecraft:torch", "count": 32}, {"id": "minecraft:cooked_beef", "count": 8}]}, "goals": [
            ("torches", "minecraft:torch", CR("minecraft:torch", 16), 1, "Light the Way", "Craft 16 torches", "Éclaire le chemin", "Fabrique 16 torches"),
            ("coal", "minecraft:coal_ore", B("#minecraft:coal_ores", 12), 1, "Coal Miner", "Mine 12 coal ores", "Mineur de charbon", "Mine 12 minerais de charbon"),
            ("copper", "minecraft:copper_ore", B("#minecraft:copper_ores", 12), 1, "Copper Vein", "Mine 12 copper ores", "Filon de cuivre", "Mine 12 minerais de cuivre"),
        ]},
        {"reward": {"items": [stack("minecraft:iron_pickaxe", enchantments={"minecraft:efficiency": 2, "minecraft:unbreaking": 2})]}, "goals": [
            ("coal_2", "minecraft:coal_ore", B("#minecraft:coal_ores", 96), 1, "Coal Miner II", "Mine 96 coal ores", "Mineur de charbon II", "Mine 96 minerais de charbon"),
            ("iron", "minecraft:iron_ore", B("#minecraft:iron_ores", 32), 1, "Iron Vein", "Mine 32 iron ores", "Filon de fer", "Mine 32 minerais de fer"),
            ("deepslate", "minecraft:deepslate", B("minecraft:deepslate", 128), 1, "Into the Deep", "Mine 128 deepslate", "Dans les profondeurs", "Mine 128 blocs d'ardoise des abîmes"),
            ("lapis", "minecraft:lapis_ore", B("#minecraft:lapis_ores", 16), 1, "Blue Gold", "Mine 16 lapis lazuli ores", "L'or bleu", "Mine 16 minerais de lapis-lazuli"),
        ]},
        {"skip": 1, "reward": {"items": [{"id": "minecraft:diamond", "count": 3}, {"id": "minecraft:golden_apple", "count": 1}]}, "goals": [
            ("iron_2", "minecraft:iron_ore", B("#minecraft:iron_ores", 128), 1, "Iron Vein II", "Mine 128 iron ores", "Filon de fer II", "Mine 128 minerais de fer"),
            ("gold", "minecraft:gold_ore", B("#minecraft:gold_ores", 32), 1, "Gold Rush", "Mine 32 gold ores", "Ruée vers l'or", "Mine 32 minerais d'or"),
            ("redstone", "minecraft:redstone_ore", B("#minecraft:redstone_ores", 32), 1, "Red Dust", "Mine 32 redstone ores", "Poussière rouge", "Mine 32 minerais de redstone"),
            ("diamonds", "minecraft:diamond_ore", B("#minecraft:diamond_ores", 8), 1, "Diamond Hunter", "Mine 8 diamond ores", "Chasseur de diamants", "Mine 8 minerais de diamant"),
        ]},
        {"skip": 1, "reward": {"items": [stack("minecraft:diamond_pickaxe", enchantments={"minecraft:efficiency": 4, "minecraft:fortune": 3, "minecraft:unbreaking": 3})]}, "goals": [
            ("iron_3", "minecraft:iron_ore", B("#minecraft:iron_ores", 256), 1, "Iron Vein III", "Mine 256 iron ores", "Filon de fer III", "Mine 256 minerais de fer"),
            ("gold_2", "minecraft:gold_ore", B("#minecraft:gold_ores", 96), 1, "Gold Rush II", "Mine 96 gold ores", "Ruée vers l'or II", "Mine 96 minerais d'or"),
            ("diamonds_2", "minecraft:diamond_ore", B("#minecraft:diamond_ores", 32), 1, "Diamond Hunter II", "Mine 32 diamond ores", "Chasseur de diamants II", "Mine 32 minerais de diamant"),
            ("deepslate_2", "minecraft:deepslate", B("minecraft:deepslate", 1024), 1, "Into the Deep II", "Mine 1024 deepslate", "Dans les profondeurs II", "Mine 1024 blocs d'ardoise des abîmes"),
        ]},
    ],
}

COMBAT = {
    "id": "combat", "icon": "minecraft:iron_sword", "background": "adventure",
    "en": "Combat", "fr": "Combat",
    "tiers": [
        {"skip": 1, "reward": {"items": [{"id": "minecraft:arrow", "count": 32}, {"id": "minecraft:cooked_porkchop", "count": 8}]}, "goals": [
            ("zombies", "minecraft:rotten_flesh", K("minecraft:zombie", 10), 1, "Zombie Hunter", "Kill 10 zombies", "Chasseur de zombies", "Tue 10 zombies"),
            ("skeletons", "minecraft:bone", K("minecraft:skeleton", 10), 1, "Skeleton Hunter", "Kill 10 skeletons", "Chasseur de squelettes", "Tue 10 squelettes"),
            ("spiders", "minecraft:string", K("minecraft:spider", 10), 1, "Spider Hunter", "Kill 10 spiders", "Chasseur d'araignées", "Tue 10 araignées"),
            ("shield", "minecraft:shield", CR("minecraft:shield"), 1, "Shield Up", "Craft a shield", "Bouclier levé", "Fabrique un bouclier"),
        ]},
        {"reward": {"items": [stack("minecraft:iron_sword", enchantments={"minecraft:sharpness": 2, "minecraft:unbreaking": 2})]}, "goals": [
            ("zombies_2", "minecraft:rotten_flesh", K("minecraft:zombie", 30), 1, "Zombie Hunter II", "Kill 30 zombies", "Chasseur de zombies II", "Tue 30 zombies"),
            ("skeletons_2", "minecraft:bone", K("minecraft:skeleton", 30), 1, "Skeleton Hunter II", "Kill 30 skeletons", "Chasseur de squelettes II", "Tue 30 squelettes"),
            ("creepers", "minecraft:gunpowder", K("minecraft:creeper", 15), 1, "Defuser", "Kill 15 creepers", "Démineur", "Tue 15 creepers"),
            ("endermen", "minecraft:ender_pearl", K("minecraft:enderman", 5), 1, "Don't Look", "Kill 5 endermen", "Ne le regarde pas", "Tue 5 endermen"),
        ]},
        {"skip": 1, "reward": {"items": [{"id": "minecraft:golden_apple", "count": 3}, {"id": "minecraft:spectral_arrow", "count": 32}]}, "goals": [
            ("zombies_3", "minecraft:rotten_flesh", K("minecraft:zombie", 100), 1, "Zombie Hunter III", "Kill 100 zombies", "Chasseur de zombies III", "Tue 100 zombies"),
            ("skeletons_3", "minecraft:bone", K("minecraft:skeleton", 100), 1, "Skeleton Hunter III", "Kill 100 skeletons", "Chasseur de squelettes III", "Tue 100 squelettes"),
            ("creepers_2", "minecraft:gunpowder", K("minecraft:creeper", 50), 1, "Defuser II", "Kill 50 creepers", "Démineur II", "Tue 50 creepers"),
            ("sniper_duel", "minecraft:arrow", ADV("adventure/sniper_duel"), 1, "Sniper Duel", "Kill a skeleton from at least 50 blocks away", "Duel de snipers", "Tue un squelette à au moins 50 blocs"),
        ]},
        {"skip": 1, "reward": {"items": [stack("minecraft:diamond_sword", enchantments={"minecraft:sharpness": 4, "minecraft:looting": 2, "minecraft:unbreaking": 3})]}, "goals": [
            ("endermen_2", "minecraft:ender_pearl", K("minecraft:enderman", 50), 1, "Don't Look II", "Kill 50 endermen", "Ne le regarde pas II", "Tue 50 endermen"),
            ("breezes", "minecraft:breeze_rod", K("minecraft:breeze", 10), 1, "Calm the Storm", "Kill 10 breezes", "Calme la tempête", "Tue 10 breezes"),
            ("creepers_3", "minecraft:gunpowder", K("minecraft:creeper", 100), 1, "Defuser III", "Kill 100 creepers", "Démineur III", "Tue 100 creepers"),
            ("phantoms", "minecraft:phantom_membrane", K("minecraft:phantom", 20), 1, "Sleepless Nights", "Kill 20 phantoms", "Nuits blanches", "Tue 20 phantoms"),
        ]},
    ],
}

FARMING = {
    "id": "farming", "icon": "minecraft:wheat", "background": "husbandry",
    "en": "Farming", "fr": "Ferme",
    "tiers": [
        {"reward": {"items": [{"id": "minecraft:bone_meal", "count": 16}, {"id": "minecraft:bread", "count": 8}]}, "goals": [
            ("till", "minecraft:stone_hoe", U("#minecraft:hoes", 16), 1, "Tilling Time", "Till 16 blocks of dirt with a hoe", "Labourage", "Laboure 16 blocs de terre avec une houe"),
            ("plant_seeds", "minecraft:wheat_seeds", U("minecraft:wheat_seeds", 16), 1, "A Seedy Place", "Plant 16 wheat seeds", "Un endroit fertile", "Plante 16 graines de blé"),
            ("wheat", "minecraft:wheat", P("minecraft:wheat", 16), 1, "First Harvest", "Harvest 16 wheat", "Première récolte", "Récolte 16 blés"),
        ]},
        {"reward": {"items": [{"id": "minecraft:lead", "count": 2}, {"id": "minecraft:name_tag", "count": 2}]}, "goals": [
            ("carrots", "minecraft:carrot", P("minecraft:carrot", 32), 1, "What's Up, Doc?", "Harvest 32 carrots", "Quoi de neuf, docteur ?", "Récolte 32 carottes"),
            ("potatoes", "minecraft:potato", P("minecraft:potato", 32), 1, "Couch Potato", "Harvest 32 potatoes", "Patate de canapé", "Récolte 32 pommes de terre"),
            ("breeder", "minecraft:wheat", ST("animals_bred", 5), 1, "Breeder", "Breed 5 animals", "Éleveur", "Fais naître 5 animaux"),
            ("bread", "minecraft:bread", CR("minecraft:bread", 16), 1, "Baker", "Bake 16 bread", "Boulanger", "Fais 16 pains"),
        ]},
        {"skip": 1, "reward": {"items": [{"id": "minecraft:golden_carrot", "count": 16}, {"id": "minecraft:golden_apple", "count": 2}]}, "goals": [
            ("breeder_2", "minecraft:wheat", ST("animals_bred", 25), 1, "Breeder II", "Breed 25 animals", "Éleveur II", "Fais naître 25 animaux"),
            ("tame", "minecraft:bone", ADV("husbandry/tame_an_animal"), 1, "Best Friends Forever", "Tame an animal", "Meilleurs amis pour la vie", "Apprivoise un animal"),
            ("fishing", "minecraft:fishing_rod", ST("fish_caught", 10), 1, "Angler", "Catch 10 fish", "Pêcheur", "Pêche 10 poissons"),
            ("honey", "minecraft:honey_bottle", ADV("husbandry/safely_harvest_honey"), 1, "Bee Our Guest", "Harvest honey without angering bees", "Une abeille dans l'assiette", "Récolte du miel sans énerver les abeilles"),
        ]},
        {"skip": 2, "reward": {"items": [{"id": "minecraft:enchanted_golden_apple", "count": 1}]}, "goals": [
            ("breeder_3", "minecraft:wheat", ST("animals_bred", 75), 1, "Breeder III", "Breed 75 animals", "Éleveur III", "Fais naître 75 animaux"),
            ("golden_carrots", "minecraft:golden_carrot", CR("minecraft:golden_carrot", 32), 1, "Gourmet", "Craft 32 golden carrots", "Gourmet", "Fabrique 32 carottes dorées"),
            ("fishing_2", "minecraft:fishing_rod", ST("fish_caught", 50), 1, "Angler II", "Catch 50 fish", "Pêcheur II", "Pêche 50 poissons"),
            ("sniffer_egg", "minecraft:sniffer_egg", ADV("husbandry/obtain_sniffer_egg"), 1, "Smells Interesting", "Obtain a sniffer egg", "Ça sent bon", "Obtiens un œuf de renifleur"),
            ("two_by_two", "minecraft:golden_apple", ADV("husbandry/bred_all_animals"), 1, "Two by Two", "Breed every animal", "Deux par deux", "Fais se reproduire tous les animaux"),
        ]},
    ],
}

VILLAGES = {
    "id": "villages", "icon": "minecraft:emerald", "background": "adventure",
    "en": "Villages", "fr": "Villages",
    "tiers": [
        {"reward": {"items": [{"id": "minecraft:emerald", "count": 8}, {"id": "minecraft:bread", "count": 8}]}, "goals": [
            ("trade", "minecraft:emerald", ADV("adventure/trade"), 1, "What a Deal!", "Trade with a villager", "Quelle affaire !", "Fais un échange avec un villageois"),
            ("trader", "minecraft:emerald", ST("traded_with_villager", 10), 1, "Merchant", "Trade 10 times with villagers", "Marchand", "Fais 10 échanges avec des villageois"),
            ("bell", "minecraft:bell", ST("bell_ring", 1), 1, "Ring the Bell", "Ring a village bell", "Sonne la cloche", "Fais sonner la cloche d'un village"),
        ]},
        {"skip": 1, "reward": {"items": [{"id": "minecraft:emerald", "count": 16}, {"id": "minecraft:iron_block", "count": 2}]}, "goals": [
            ("trader_2", "minecraft:emerald", ST("traded_with_villager", 50), 1, "Merchant II", "Trade 50 times with villagers", "Marchand II", "Fais 50 échanges avec des villageois"),
            ("iron_golem", "minecraft:carved_pumpkin", ADV("adventure/summon_iron_golem"), 1, "Hired Help", "Build an iron golem", "Renfort", "Construis un golem de fer"),
            ("emeralds", "minecraft:emerald", H("minecraft:emerald", 64), 1, "Savings", "Carry 64 emeralds", "Économies", "Porte 64 émeraudes sur toi"),
            ("cure_zombie", "minecraft:golden_apple", ADV("story/cure_zombie_villager"), 1, "Zombie Doctor", "Cure a zombie villager", "Docteur zombie", "Soigne un villageois zombie"),
        ]},
        {"skip": 1, "reward": {"items": [{"id": "minecraft:emerald_block", "count": 4}, {"id": "minecraft:golden_apple", "count": 2}]}, "goals": [
            ("trader_3", "minecraft:emerald", ST("traded_with_villager", 150), 1, "Merchant III", "Trade 150 times with villagers", "Marchand III", "Fais 150 échanges avec des villageois"),
            ("pillagers", "minecraft:crossbow", K("minecraft:pillager", 20), 1, "Pillager Hunter", "Kill 20 pillagers", "Chasseur de pillards", "Tue 20 pillards"),
            ("voluntary_exile", "minecraft:white_banner", ADV("adventure/voluntary_exile"), 1, "Voluntary Exile", "Kill a raid captain", "Exil volontaire", "Tue un capitaine de pillards"),
            ("hero", "minecraft:white_banner", ADV("adventure/hero_of_the_village"), 1, "Hero of the Village", "Defend a village from a raid", "Héros du village", "Défends un village contre un raid"),
        ]},
        {"skip": 1, "reward": {"items": [{"id": "minecraft:totem_of_undying", "count": 1}, {"id": "minecraft:emerald_block", "count": 8}]}, "goals": [
            ("trader_4", "minecraft:emerald", ST("traded_with_villager", 400), 1, "Merchant IV", "Trade 400 times with villagers", "Marchand IV", "Fais 400 échanges avec des villageois"),
            ("pillagers_2", "minecraft:crossbow", K("minecraft:pillager", 60), 1, "Pillager Hunter II", "Kill 60 pillagers", "Chasseur de pillards II", "Tue 60 pillards"),
            ("ravager", "minecraft:saddle", K("minecraft:ravager", 1), 1, "Beast Slayer", "Kill a ravager", "Tueur de bêtes", "Tue un ravageur"),
            ("star_trader", "minecraft:emerald_block", ADV("adventure/trade_at_world_height"), 1, "Star Trader", "Trade with a villager at the build height limit", "Commerçant des étoiles", "Fais un échange avec un villageois à la hauteur maximale"),
        ]},
    ],
}

REDSTONE = {
    "id": "redstone", "icon": "minecraft:redstone", "background": "stone",
    "en": "Redstone", "fr": "Redstone",
    "tiers": [
        {"reward": {"items": [{"id": "minecraft:redstone", "count": 32}, {"id": "minecraft:quartz", "count": 8}]}, "goals": [
            ("redstone_torches", "minecraft:redstone_torch", CR("minecraft:redstone_torch", 8), 1, "Sparks", "Craft 8 redstone torches", "Étincelles", "Fabrique 8 torches de redstone"),
            ("repeaters", "minecraft:repeater", CR("minecraft:repeater", 4), 1, "Delay Line", "Craft 4 repeaters", "Ligne à retard", "Fabrique 4 répéteurs"),
            ("pistons", "minecraft:piston", CR("minecraft:piston", 4), 1, "Push It", "Craft 4 pistons", "Pousse-toi", "Fabrique 4 pistons"),
            ("lamps", "minecraft:redstone_lamp", CR("minecraft:redstone_lamp", 4), 1, "Light Show", "Craft 4 redstone lamps", "Spectacle de lumière", "Fabrique 4 lampes de redstone"),
        ]},
        {"reward": {"items": [{"id": "minecraft:slime_ball", "count": 16}, {"id": "minecraft:iron_ingot", "count": 16}]}, "goals": [
            ("sticky_pistons", "minecraft:sticky_piston", CR("minecraft:sticky_piston", 8), 1, "Sticky Situation", "Craft 8 sticky pistons", "Situation collante", "Fabrique 8 pistons collants"),
            ("observers", "minecraft:observer", CR("minecraft:observer", 8), 1, "Watchful Eye", "Craft 8 observers", "Œil attentif", "Fabrique 8 observateurs"),
            ("hoppers", "minecraft:hopper", CR("minecraft:hopper", 8), 1, "Item Logistics", "Craft 8 hoppers", "Logistique", "Fabrique 8 entonnoirs"),
            ("comparators", "minecraft:comparator", CR("minecraft:comparator", 4), 1, "Signal Strength", "Craft 4 comparators", "Force du signal", "Fabrique 4 comparateurs"),
        ]},
        {"skip": 1, "reward": {"items": [{"id": "minecraft:redstone_block", "count": 16}, {"id": "minecraft:slime_block", "count": 8}]}, "goals": [
            ("observers_2", "minecraft:observer", CR("minecraft:observer", 32), 1, "Watchful Eye II", "Craft 32 observers", "Œil attentif II", "Fabrique 32 observateurs"),
            ("hoppers_2", "minecraft:hopper", CR("minecraft:hopper", 32), 1, "Item Logistics II", "Craft 32 hoppers", "Logistique II", "Fabrique 32 entonnoirs"),
            ("crafters", "minecraft:crafter", CR("minecraft:crafter", 4), 1, "Automation", "Craft 4 crafters", "Automatisation", "Fabrique 4 fabricateurs"),
            ("lighten_up", "minecraft:copper_bulb", ADV("adventure/lighten_up"), 1, "Lighten Up", "Turn a copper bulb on after scraping it", "Allume-toi", "Allume une ampoule en cuivre après l'avoir grattée"),
        ]},
    ],
}

EXPLORATION = {
    "id": "exploration", "icon": "minecraft:compass", "background": "adventure",
    "en": "Exploration", "fr": "Exploration",
    "tiers": [
        {"skip": 1, "reward": {"items": [{"id": "minecraft:spyglass", "count": 1}, {"id": "minecraft:cooked_beef", "count": 16}]}, "goals": [
            ("map", "minecraft:map", CR("minecraft:map"), 1, "Cartographer", "Craft a map", "Cartographe", "Fabrique une carte"),
            ("desert", "minecraft:sand", VB("minecraft:desert"), 1, "Sea of Sand", "Visit a desert", "Mer de sable", "Visite un désert"),
            ("jungle", "minecraft:jungle_sapling", VB("#minecraft:is_jungle"), 1, "Into the Wild", "Visit a jungle", "En pleine nature", "Visite une jungle"),
            ("witches", "minecraft:cauldron", K("minecraft:witch", 3), 1, "Witch Hunt", "Kill 3 witches", "Chasse aux sorcières", "Tue 3 sorcières"),
        ]},
        {"skip": 1, "reward": {"items": [{"id": "minecraft:map", "count": 4}, {"id": "minecraft:ender_pearl", "count": 4}]}, "goals": [
            ("maps", "minecraft:map", CR("minecraft:map", 8), 1, "Cartographer II", "Craft 8 maps", "Cartographe II", "Fabrique 8 cartes"),
            ("badlands", "minecraft:terracotta", VB("#minecraft:is_badlands"), 1, "Red Canyons", "Visit the badlands", "Canyons rouges", "Visite des badlands"),
            ("cherry_grove", "minecraft:cherry_sapling", VB("minecraft:cherry_grove"), 1, "Cherry Blossom", "Visit a cherry grove", "Fleurs de cerisier", "Visite une cerisaie"),
            ("trial_chambers", "minecraft:trial_key", ADV("adventure/minecraft_trials_edition"), 1, "Minecraft: Trial(s) Edition", "Step into a trial chamber", "Minecraft : Édition d'épreuve(s)", "Entre dans une chambre des épreuves"),
            ("guardians", "minecraft:prismarine_shard", K("minecraft:guardian", 5), 1, "Ocean Monument", "Kill 5 guardians", "Monument sous-marin", "Tue 5 gardiens"),
        ]},
        {"skip": 1, "reward": {"items": [{"id": "minecraft:golden_apple", "count": 2}, {"id": "minecraft:sponge", "count": 4}]}, "goals": [
            ("treasure", "minecraft:heart_of_the_sea", P("minecraft:heart_of_the_sea"), 1, "X Marks the Spot", "Dig up a buried treasure and find a heart of the sea", "La croix marque l'endroit", "Déterre un trésor enfoui et trouve un cœur de la mer"),
            ("elder_guardian", "minecraft:wet_sponge", K("minecraft:elder_guardian"), 1, "Elder Slayer", "Kill an elder guardian", "Tueur d'ancien", "Tue un gardien ancien"),
            ("ice_spikes", "minecraft:packed_ice", VB("minecraft:ice_spikes"), 1, "Frozen Spires", "Visit an ice spikes biome", "Flèches de glace", "Visite un biome de pics de glace"),
            ("mushroom_fields", "minecraft:red_mushroom", VB("minecraft:mushroom_fields"), 1, "Mushroom Island", "Visit a mushroom field", "Île aux champignons", "Visite une île aux champignons"),
            ("archaeology", "minecraft:brush", ADV("adventure/salvage_sherd"), 1, "Respecting the Remnants", "Brush a suspicious block to obtain a pottery sherd", "Respect des vestiges", "Brosse un bloc suspect pour obtenir un tesson de poterie"),
        ]},
        {"skip": 1, "reward": {"items": [{"id": "minecraft:totem_of_undying", "count": 1}, {"id": "minecraft:diamond", "count": 8}]}, "goals": [
            ("allay", "minecraft:allay_spawn_egg", ADV("husbandry/allay_deliver_item_to_player"), 1, "You've Got a Friend in Me", "Free an allay and have it bring you an item", "Je suis ton ami", "Libère un allay et fais-lui t'apporter un objet"),
            ("mansion", "minecraft:totem_of_undying", K("minecraft:evoker"), 1, "Mansion Raider", "Kill an evoker, the master of woodland mansions", "Pilleur de manoir", "Tue un évocateur, maître des manoirs"),
            ("vexes", "minecraft:iron_sword", K("minecraft:vex", 5), 1, "Ghostbuster", "Kill 5 vexes", "Chasseur de fantômes", "Tue 5 vex"),
            ("lodestone", "minecraft:lodestone", ADV("adventure/use_lodestone"), 1, "Country Lode, Take Me Home", "Use a compass on a lodestone", "Magnétite, ramène-moi", "Utilise une boussole sur une magnétite"),
            ("adventuring_time", "minecraft:diamond_boots", ADV("adventure/adventuring_time"), 1, "Adventuring Time", "Discover every biome", "L'heure de l'aventure", "Découvre tous les biomes"),
        ]},
    ],
}

MAGIC = {
    "id": "magic", "icon": "minecraft:enchanting_table", "background": "stone",
    "en": "Magic", "fr": "Magie",
    "tiers": [
        {"reward": {"items": [{"id": "minecraft:lapis_lazuli", "count": 32}, {"id": "minecraft:experience_bottle", "count": 8}]}, "goals": [
            ("books", "minecraft:book", CR("minecraft:book", 8), 1, "Bookbinder", "Craft 8 books", "Relieur", "Fabrique 8 livres"),
            ("enchanting_table", "minecraft:enchanting_table", CR("minecraft:enchanting_table"), 1, "Enchanter's Desk", "Craft an enchanting table", "Bureau d'enchanteur", "Fabrique une table d'enchantement"),
            ("enchanter", "minecraft:enchanted_book", ST("enchant_item", 3), 1, "Enchanter", "Enchant 3 items", "Enchanteur", "Enchante 3 objets"),
        ]},
        {"reward": {"items": [{"id": "minecraft:experience_bottle", "count": 16}, {"id": "minecraft:blaze_rod", "count": 2}]}, "goals": [
            ("bookshelves", "minecraft:bookshelf", CR("minecraft:bookshelf", 15), 1, "Library", "Craft 15 bookshelves", "Bibliothèque", "Fabrique 15 bibliothèques"),
            ("enchanter_2", "minecraft:enchanted_book", ST("enchant_item", 8), 1, "Enchanter II", "Enchant 8 items", "Enchanteur II", "Enchante 8 objets"),
            ("anvil", "minecraft:anvil", CR("minecraft:anvil"), 1, "Blacksmith", "Craft an anvil", "Forgeron", "Fabrique une enclume"),
            ("brewing_stand", "minecraft:brewing_stand", CR("minecraft:brewing_stand"), 1, "Alchemist", "Craft a brewing stand", "Alchimiste", "Fabrique un alambic"),
        ]},
        {"skip": 1, "reward": {"items": [{"id": "minecraft:experience_bottle", "count": 32}, {"id": "minecraft:nether_wart", "count": 16}]}, "goals": [
            ("enchanter_3", "minecraft:enchanted_book", ST("enchant_item", 15), 1, "Enchanter III", "Enchant 15 items", "Enchanteur III", "Enchante 15 objets"),
            ("local_brewery", "minecraft:potion", BREW(3), 1, "Local Brewery", "Brew 3 potions", "Brasserie locale", "Prépare 3 potions"),
            ("swiftness", "minecraft:sugar", BREW(3, "swiftness"), 1, "Gotta Go Fast", "Brew 3 potions of Swiftness", "À toute vitesse", "Prépare 3 potions de rapidité"),
            ("healing", "minecraft:glistering_melon_slice", BREW(3, "healing"), 1, "First Aid", "Brew 3 potions of Healing", "Premiers secours", "Prépare 3 potions de soin"),
            ("anvil_use", "minecraft:anvil", ST("interact_with_anvil", 10), 1, "Blacksmith II", "Use an anvil 10 times", "Forgeron II", "Utilise une enclume 10 fois"),
        ]},
        {"skip": 1, "reward": {"items": [stack("minecraft:enchanted_book", stored_enchantments={"minecraft:mending": 1})]}, "goals": [
            ("enchanter_4", "minecraft:enchanted_book", ST("enchant_item", 30), 1, "Enchanter IV", "Enchant 30 items", "Enchanteur IV", "Enchante 30 objets"),
            ("fire_resistance", "minecraft:magma_cream", BREW(3, "fire_resistance"), 1, "Fireproof", "Brew 3 potions of Fire Resistance", "Ignifugé", "Prépare 3 potions de résistance au feu"),
            ("strength", "minecraft:blaze_powder", BREW(3, "strength"), 1, "Liquid Courage", "Brew 3 potions of Strength", "Courage liquide", "Prépare 3 potions de force"),
            ("invisibility", "minecraft:fermented_spider_eye", BREW(3, "invisibility"), 1, "Now You See Me", "Brew 3 potions of Invisibility", "Insaisissable", "Prépare 3 potions d'invisibilité"),
            ("full_beacon", "minecraft:beacon", ADV("nether/create_full_beacon"), 1, "Beaconator", "Bring a beacon to full power", "Balisator", "Alimente une balise au maximum"),
        ]},
    ],
}

NETHER = {
    "id": "nether", "icon": "minecraft:netherrack", "background": "nether",
    "en": "Nether", "fr": "Nether",
    "tiers": [
        {"reward": {"items": [{"id": "minecraft:gold_ingot", "count": 16}, potion("minecraft:long_fire_resistance")]}, "goals": [
            ("crimson_forest", "minecraft:crimson_fungus", VB("minecraft:crimson_forest"), 1, "Crimson Forest", "Visit a crimson forest", "Forêt écarlate", "Visite une forêt écarlate"),
            ("warped_forest", "minecraft:warped_fungus", VB("minecraft:warped_forest"), 1, "Warped Forest", "Visit a warped forest", "Forêt biscornue", "Visite une forêt biscornue"),
            ("quartz", "minecraft:nether_quartz_ore", B("minecraft:nether_quartz_ore", 32), 1, "Quartz Miner", "Mine 32 nether quartz ores", "Mineur de quartz", "Mine 32 minerais de quartz du Nether"),
            ("crying_obsidian", "minecraft:crying_obsidian", ADV("nether/obtain_crying_obsidian"), 1, "Who is Cutting Onions?", "Obtain crying obsidian", "Qui épluche des oignons ?", "Obtiens de l'obsidienne pleureuse"),
        ]},
        {"skip": 1, "reward": {"items": [{"id": "minecraft:golden_apple", "count": 3}, potion("minecraft:long_fire_resistance", 2)]}, "goals": [
            ("quartz_2", "minecraft:nether_quartz_ore", B("minecraft:nether_quartz_ore", 128), 1, "Quartz Miner II", "Mine 128 nether quartz ores", "Mineur de quartz II", "Mine 128 minerais de quartz du Nether"),
            ("bastion", "minecraft:gilded_blackstone", ADV("nether/find_bastion"), 1, "Those Were the Days", "Enter a bastion remnant", "C'était le bon temps", "Entre dans les vestiges d'un bastion"),
            ("strider", "minecraft:warped_fungus_on_a_stick", ADV("nether/ride_strider"), 1, "This Boat Has Legs", "Ride a strider with a warped fungus on a stick", "Ce bateau a des jambes", "Chevauche un arpenteur avec un champignon biscornu sur un bâton"),
            ("wither_skeletons", "minecraft:wither_skeleton_skull", K("minecraft:wither_skeleton", 10), 1, "Bone Breaker", "Kill 10 wither skeletons", "Briseur d'os", "Tue 10 wither squelettes"),
        ]},
        {"skip": 1, "reward": {"items": [{"id": "minecraft:netherite_upgrade_smithing_template", "count": 1}, {"id": "minecraft:netherite_scrap", "count": 4}]}, "goals": [
            ("ancient_debris", "minecraft:ancient_debris", B("minecraft:ancient_debris", 16), 1, "Hidden in the Depths", "Mine 16 ancient debris", "Caché dans les profondeurs", "Mine 16 débris antiques"),
            ("netherite", "minecraft:netherite_ingot", CR("minecraft:netherite_ingot", 4), 1, "Netherite Smith", "Craft 4 netherite ingots", "Forgeron de netherite", "Fabrique 4 lingots de netherite"),
            ("wither_skeletons_2", "minecraft:wither_skeleton_skull", K("minecraft:wither_skeleton", 40), 1, "Bone Breaker II", "Kill 40 wither skeletons", "Briseur d'os II", "Tue 40 wither squelettes"),
            ("return_to_sender", "minecraft:fire_charge", ADV("nether/return_to_sender"), 1, "Return to Sender", "Kill a ghast with its own fireball", "Retour à l'envoyeur", "Tue un ghast avec sa propre boule de feu"),
        ]},
    ],
}

END = {
    "id": "end", "icon": "minecraft:purpur_block", "background": "end",
    "en": "The End", "fr": "L'End",
    "tiers": [
        {"reward": {"items": [{"id": "minecraft:shulker_shell", "count": 4}, {"id": "minecraft:golden_apple", "count": 2}]}, "goals": [
            ("gateway", "minecraft:ender_pearl", ADV("end/enter_end_gateway"), 1, "Remote Getaway", "Escape the island through an End gateway", "Évasion à distance", "Quitte l'île par un portail de l'End"),
            ("end_city", "minecraft:purpur_block", ADV("end/find_end_city"), 1, "The City at the End of the Game", "Find an End city", "La cité au bout du jeu", "Trouve une cité de l'End"),
            ("shulkers", "minecraft:shulker_shell", K("minecraft:shulker", 10), 1, "Shell Hunter", "Kill 10 shulkers", "Chasseur de carapaces", "Tue 10 shulkers"),
        ]},
        {"skip": 1, "reward": {"items": [{"id": "minecraft:firework_rocket", "count": 64}, {"id": "minecraft:shulker_box", "count": 2}]}, "goals": [
            ("elytra", "minecraft:elytra", ADV("end/elytra"), 1, "Sky's the Limit", "Find an elytra", "Plus haut que le ciel", "Trouve des élytres"),
            ("shulkers_2", "minecraft:shulker_shell", K("minecraft:shulker", 40), 1, "Shell Hunter II", "Kill 40 shulkers", "Chasseur de carapaces II", "Tue 40 shulkers"),
            ("levitate", "minecraft:shulker_shell", ADV("end/levitate"), 1, "Great View From Up Here", "Levitate 50 blocks up from a shulker's attack", "Belle vue d'ici", "Lévite de 50 blocs grâce à un shulker"),
            ("respawn_dragon", "minecraft:end_crystal", ADV("end/respawn_dragon"), 1, "The End... Again...", "Respawn the Ender Dragon", "La fin... encore...", "Fais réapparaître le dragon de l'Ender"),
        ]},
    ],
}

WARDEN = {
    "id": "warden", "icon": "minecraft:sculk_shrieker", "background": "stone",
    "en": "The Warden", "fr": "Le Warden",
    "tiers": [
        {"skip": 1, "reward": {"items": [stack("minecraft:leather_leggings", enchantments={"minecraft:swift_sneak": 2}), {"id": "minecraft:white_wool", "count": 32}]}, "goals": [
            ("sculk", "minecraft:sculk", B("minecraft:sculk", 64), 1, "Silent Miner", "Mine 64 sculk", "Mineur silencieux", "Mine 64 blocs de sculk"),
            ("sensors", "minecraft:sculk_sensor", B("minecraft:sculk_sensor", 8), 1, "Disarmed", "Mine 8 sculk sensors", "Désamorcé", "Mine 8 capteurs sculk"),
            ("sneak", "minecraft:sculk_sensor", ADV("adventure/avoid_vibration"), 1, "Sneak 100", "Sneak near a sculk sensor or warden to stay undetected", "Furtif 100", "Avance accroupi près d'un capteur sculk ou du warden sans te faire repérer"),
            ("it_spreads", "minecraft:sculk_catalyst", ADV("adventure/kill_mob_near_sculk_catalyst"), 1, "It Spreads", "Kill a mob near a sculk catalyst", "Ça se propage", "Tue une créature près d'un catalyseur sculk"),
        ]},
        {"skip": 1, "reward": {"items": [{"id": "minecraft:golden_apple", "count": 4}, potion("minecraft:long_night_vision", 2)]}, "goals": [
            ("shriekers", "minecraft:sculk_shrieker", B("minecraft:sculk_shrieker", 8), 1, "Hush", "Mine 8 sculk shriekers", "Chut", "Mine 8 hurleurs sculk"),
            ("echo_shards", "minecraft:echo_shard", P("minecraft:echo_shard", 8), 1, "Echoes", "Find 8 echo shards in ancient cities", "Échos", "Trouve 8 éclats d'écho dans les cités antiques"),
            ("recovery_compass", "minecraft:recovery_compass", CR("minecraft:recovery_compass"), 1, "Lost and Found", "Craft a recovery compass", "Objets trouvés", "Fabrique une boussole de récupération"),
            ("catalysts", "minecraft:sculk_catalyst", B("minecraft:sculk_catalyst", 4), 1, "Contained", "Mine 4 sculk catalysts", "Confiné", "Mine 4 catalyseurs sculk"),
        ]},
        {"skip": 1, "reward": {"items": [stack("minecraft:netherite_chestplate", enchantments={"minecraft:protection": 4, "minecraft:unbreaking": 3})]}, "goals": [
            ("warden", "minecraft:sculk_shrieker", K("minecraft:warden"), 2, "Guardian of the Deep", "Kill the warden", "Gardien des abîmes", "Tue le warden"),
            ("echo_shards_2", "minecraft:echo_shard", P("minecraft:echo_shard", 24), 1, "Echoes II", "Find 24 echo shards", "Échos II", "Trouve 24 éclats d'écho"),
            ("disc_fragments", "minecraft:disc_fragment_5", P("minecraft:disc_fragment_5", 9), 1, "Broken Record", "Find 9 disc fragments", "Disque rayé", "Trouve 9 fragments de disque"),
        ]},
    ],
}

SECONDARY = [MINING, COMBAT, FARMING, VILLAGES, REDSTONE, EXPLORATION, MAGIC, NETHER, END, WARDEN]
SECONDARY_BY_ID = {c["id"]: c for c in SECONDARY}

# ---- main category -------------------------------------------------------------------------------

FIRE_RESISTANCE = {
    "run": "effect give @s minecraft:fire_resistance 300",
    "icon": {"id": "minecraft:potion", "components": {"minecraft:potion_contents": {"potion": "minecraft:fire_resistance"}}},
    "name": {"translate": "reward.minegoals.fire_resistance"},
    "description": {"translate": "reward.minegoals.fire_resistance.desc"},
}

PROGRESSION = {
    "id": "progression", "icon": "minecraft:crafting_table", "background": "adventure",
    "en": "Progression", "fr": "Progression",
    "tiers": [
        {"background": "adventure", "reward": items(("minecraft:bread", 8), ("minecraft:torch", 16)), "goals": [
            ("crafting_table", "minecraft:crafting_table", CR("minecraft:crafting_table"), 1, "First Table", "Craft a crafting table", "Premier établi", "Fabrique un établi"),
            ("wooden_pickaxe", "minecraft:wooden_pickaxe", CR("minecraft:wooden_pickaxe"), 1, "Getting Started", "Craft a wooden pickaxe", "Premiers pas", "Fabrique une pioche en bois", {"unlocks": "mining"}),
            ("furnace", "minecraft:furnace", CR("minecraft:furnace"), 1, "Hot Topic", "Craft a furnace", "Sujet brûlant", "Fabrique un four"),
        ]},
        {"background": "stone", "reward": items(("minecraft:iron_ingot", 6), ("minecraft:cooked_beef", 8)), "goals": [
            ("mining_1",),
            ("sword", "minecraft:stone_sword", CR("#minecraft:swords"), 1, "Armed", "Craft a sword", "Armé", "Fabrique une épée", {"unlocks": "combat"}),
            ("hoe", "minecraft:stone_hoe", CR("#minecraft:hoes"), 1, "Green Thumb", "Craft a hoe", "Main verte", "Fabrique une houe", {"unlocks": "farming"}),
            ("sleep", "minecraft:red_bed", ADV("adventure/sleep_in_bed"), 1, "Sweet Dreams", "Sleep in a bed", "Fais de beaux rêves", "Dors dans un lit"),
        ]},
        {"background": "stone", "reward": items(("minecraft:bucket", 2), ("minecraft:golden_apple", 1)), "goals": [
            ("smelt_iron", "minecraft:iron_ingot", CR("minecraft:iron_ingot"), 1, "Acquire Hardware", "Smelt an iron ingot", "Quincaillerie", "Fais fondre un lingot de fer"),
            ("iron_pickaxe", "minecraft:iron_pickaxe", ADV("story/iron_tools"), 1, "Isn't It Iron Pick", "Craft an iron pickaxe", "Pioche en fer", "Fabrique une pioche en fer"),
            ("iron_armor", "minecraft:iron_chestplate", ADV("story/obtain_armor"), 1, "Suit Up", "Wear a piece of iron armor", "Équipe-toi", "Porte une pièce d'armure en fer"),
            ("villager", "minecraft:emerald", ST("talked_to_villager"), 1, "Neighbours", "Talk to a villager", "Voisinage", "Parle à un villageois", {"unlocks": "villages"}),
            ("combat_1",),
            ("farming_1",),
        ]},
        {"background": "stone", "reward": items(("minecraft:flint_and_steel", 1), ("minecraft:leather", 8)), "goals": [
            ("diamonds", "minecraft:diamond", ADV("story/mine_diamond"), 1, "Diamonds!", "Find a diamond", "Des diamants !", "Trouve un diamant"),
            ("obsidian", "minecraft:obsidian", ADV("story/form_obsidian"), 1, "Ice Bucket Challenge", "Obtain a block of obsidian", "Défi du seau d'eau", "Obtiens un bloc d'obsidienne"),
            ("compass", "minecraft:compass", CR("minecraft:compass"), 1, "Finding North", "Craft a compass", "Cap au nord", "Fabrique une boussole", {"unlocks": "exploration"}),
            ("piston", "minecraft:piston", CR("minecraft:piston"), 1, "Moving Parts", "Craft a piston", "Pièces mobiles", "Fabrique un piston", {"unlocks": "redstone"}),
            ("mining_2",),
        ]},
        {"background": "adventure", "reward": {**items(("minecraft:arrow", 32)), "commands": [FIRE_RESISTANCE]}, "goals": [
            ("book", "minecraft:book", CR("minecraft:book"), 1, "Bookworm", "Craft a book", "Rat de bibliothèque", "Fabrique un livre", {"unlocks": "magic"}),
            ("enter_nether", "minecraft:netherrack", ADV("story/enter_the_nether"), 1, "We Need to Go Deeper", "Build a portal and enter the Nether", "Il faut creuser plus profond", "Construis un portail et entre dans le Nether", {"unlocks": "nether"}),
            ("combat_2",),
            ("farming_2",),
        ]},
        {"background": "nether", "reward": items(("minecraft:ender_pearl", 4), ("minecraft:golden_apple", 2)), "goals": [
            ("fortress", "minecraft:nether_bricks", ADV("nether/find_fortress"), 1, "A Terrible Fortress", "Find a Nether fortress", "Une terrible forteresse", "Trouve une forteresse du Nether"),
            ("blaze_rod", "minecraft:blaze_rod", ADV("nether/obtain_blaze_rod"), 1, "Into Fire", "Get a blaze rod", "Dans le feu", "Obtiens un bâton de blaze"),
            ("nether_1",),
            ("magic_1",),
        ]},
        {"background": "adventure", "reward": {"items": [stack("minecraft:bow", enchantments={"minecraft:power": 3, "minecraft:unbreaking": 2}),
                                                          {"id": "minecraft:arrow", "count": 64}]}, "goals": [
            ("ender_pearls", "minecraft:ender_pearl", P("minecraft:ender_pearl", 12), 1, "Pearl Collector", "Pick up 12 ender pearls", "Collecteur de perles", "Ramasse 12 perles de l'Ender"),
            ("eyes_of_ender", "minecraft:ender_eye", CR("minecraft:ender_eye", 12), 1, "Eyes of Ender", "Craft 12 eyes of ender", "Yeux de l'Ender", "Fabrique 12 yeux de l'Ender"),
            ("stronghold", "minecraft:end_portal_frame", ADV("story/follow_ender_eye"), 1, "Eye Spy", "Follow an eye of ender into a stronghold", "Œil pour œil", "Suis un œil de l'Ender jusqu'à un fort"),
            ("mining_3",),
            ("combat_3",),
            ("redstone_1",),
        ]},
        {"background": "end", "reward": {"items": [stack("minecraft:elytra", custom_name={"translate": "item.minegoals.dragon_wings", "color": "light_purple", "italic": False},
                                                         rarity="epic")]}, "goals": [
            ("enter_end", "minecraft:end_stone", ADV("story/enter_the_end"), 1, "The End?", "Enter the End portal", "La fin ?", "Entre dans le portail de l'End"),
            ("deep_dark", "minecraft:sculk", VB("minecraft:deep_dark"), 1, "Into the Dark", "Reach the deep dark", "Dans le noir", "Atteins les abîmes", {"unlocks": "warden"}),
            ("dragon", "minecraft:dragon_head", ADV("end/kill_dragon"), 3, "Free the End", "Slay the Ender Dragon", "Libère l'End", "Tue le dragon de l'Ender", {"unlocks": "end"}),
            ("nether_2",),
            ("magic_2",),
            ("farming_3",),
        ]},
        {"background": "end", "reward": {"items": [stack("minecraft:netherite_sword",
                                                         enchantments={"minecraft:sharpness": 5, "minecraft:unbreaking": 3, "minecraft:mending": 1},
                                                         custom_name={"translate": "item.minegoals.legend_blade", "color": "gold", "italic": False},
                                                         rarity="epic")]}, "goals": [
            ("wither", "minecraft:nether_star", K("minecraft:wither"), 3, "Withering Heights", "Kill the Wither", "Les Hauts de Hurlevent", "Tue le Wither"),
            ("end_1",),
            ("nether_3",),
            ("mining_4",),
            ("combat_4",),
            ("magic_3",),
        ]},
    ],
}

# ("mining_1",) is shorthand for TIER_GOAL("mining", 1).
for _tier in PROGRESSION["tiers"]:
    _tier["goals"] = [TIER_GOAL(g[0].rsplit("_", 1)[0], int(g[0].rsplit("_", 1)[1])) if len(g) == 1 else g
                      for g in _tier["goals"]]

CATEGORIES = [PROGRESSION] + SECONDARY


en = {
    "minegoals.screen.title": "MineGoals",
    "minegoals.screen.empty": "No goals loaded",
    "minegoals.screen.no_goals": "No goals in this tier",
    "minegoals.screen.tier": "Tier %s",
    "minegoals.screen.tier_short": "Tier %s",
    "minegoals.screen.completed": "Completed",
    "minegoals.screen.locked_tier": "Locked · reach tier %s",
    "minegoals.screen.locked_tier_tracked": "Locked · reach tier %s · progress already counts",
    "minegoals.screen.locked_claim": "Locked · claim the tier %s reward to unlock",
    "minegoals.screen.category_claim": "Claim the tier %s reward to continue",
    "minegoals.screen.tier_state.done": "Completed",
    "minegoals.screen.tier_state.claimable": "Completed",
    "minegoals.screen.tier_state.in_progress": "In progress",
    "minegoals.screen.tier_state.locked": "Locked",
    "minegoals.screen.locked_requires": "Locked · requires: %s",
    "minegoals.screen.rewards": "Rewards:",
    "minegoals.screen.reward_xp": "%s XP",
    "minegoals.screen.reward_special": "Special reward",
    "minegoals.screen.claim": "Claim",
    "minegoals.screen.claimed": "Claimed",
    "minegoals.screen.track": "Track",
    "minegoals.screen.tracked": "Tracked",
    "minegoals.screen.track_tier": "Track every goal of this tier",
    "minegoals.screen.untrack_tier": "Stop tracking the goals of this tier",
    "minegoals.screen.collect": "Collect",
    "minegoals.screen.pending": "%s item(s) waiting",
    "minegoals.screen.pending_title": "Rewards waiting for space",
    "minegoals.screen.pending_hint": "Collect puts what fits in your inventory",
    "minegoals.screen.claimable": "%s reward(s) to claim",
    "minegoals.screen.tier_reward": "Tier %s reward",
    "minegoals.screen.tier_hidden": "Reach this tier to reveal it",
    "minegoals.hud.tier": "Tier %s · %s/%s pts",
    "minegoals.hud.tier_hidden": "Tier %s · %s pts",
    "minegoals.hud.tier_max": "Tier %s · %s pts (max)",
    "minegoals.hud.none": "All goals done!",
    "minegoals.hud.claimable": "%s to claim",
    "minegoals.notify.goal_completed": "Goal completed!",
    "minegoals.notify.tier_reached": "Tier %s reached!",
    "minegoals.notify.claim_hint": "Claim your reward in the goals screen (G).",
    "minegoals.notify.category_unlocked": "New category unlocked!",
    "minegoals.notify.inventory_full": "Inventory full: %s item(s) kept for you. Collect them in the goals screen (G).",
    "minegoals.toast.tracked_completed": "Goal completed!",
    "minegoals.command.list.header": "%s · %s points",
    "minegoals.command.list.category": " · tier %s/%s · %s pts",
    "minegoals.command.unknown": "Unknown goal or category: %s",
    "minegoals.command.already_completed": "Goal %s is already completed",
    "minegoals.command.reset": "Reset %s for %s",
    "minegoals.command.complete": "Completed %s for %s",
    "minegoals.command.complete_all": "Completed %s goals (%s) for %s",
    "minegoals.command.unlock": "Unlocked category %s for %s",
    "minegoals.command.lock": "Locked category %s for %s",
    "reward.minegoals.fire_resistance": "Fire Resistance (5 min)",
    "reward.minegoals.fire_resistance.desc": "Shrug off blaze fireballs in the fortress",
    "key.minegoals.open": "Open goals",
    "key.minegoals.toggle_hud": "Toggle goals HUD",
    "key.minegoals.next_category": "Next category in goals HUD",
    "key.category.minegoals.main": "MineGoals",
    "item.minegoals.dragon_wings": "Dragon Wings",
    "item.minegoals.legend_blade": "Legend's Edge",
}
fr = {
    "minegoals.screen.title": "MineGoals",
    "minegoals.screen.empty": "Aucun objectif chargé",
    "minegoals.screen.no_goals": "Aucun objectif dans ce palier",
    "minegoals.screen.tier": "Palier %s",
    "minegoals.screen.tier_short": "Palier %s",
    "minegoals.screen.completed": "Terminé",
    "minegoals.screen.locked_tier": "Verrouillé · atteins le palier %s",
    "minegoals.screen.locked_tier_tracked": "Verrouillé · atteins le palier %s · la progression compte déjà",
    "minegoals.screen.locked_claim": "Verrouillé · récupère la récompense du palier %s pour débloquer",
    "minegoals.screen.category_claim": "Récupère la récompense du palier %s pour continuer",
    "minegoals.screen.tier_state.done": "Terminé",
    "minegoals.screen.tier_state.claimable": "Terminé",
    "minegoals.screen.tier_state.in_progress": "En cours",
    "minegoals.screen.tier_state.locked": "Verrouillé",
    "minegoals.screen.locked_requires": "Verrouillé · nécessite : %s",
    "minegoals.screen.rewards": "Récompenses :",
    "minegoals.screen.reward_xp": "%s XP",
    "minegoals.screen.reward_special": "Récompense spéciale",
    "minegoals.screen.claim": "Récupérer",
    "minegoals.screen.claimed": "Récupéré",
    "minegoals.screen.track": "Suivre",
    "minegoals.screen.tracked": "Suivi",
    "minegoals.screen.track_tier": "Suivre tous les objectifs de ce palier",
    "minegoals.screen.untrack_tier": "Ne plus suivre les objectifs de ce palier",
    "minegoals.screen.collect": "Récupérer",
    "minegoals.screen.pending": "%s objet(s) en attente",
    "minegoals.screen.pending_title": "Récompenses en attente de place",
    "minegoals.screen.pending_hint": "Récupérer met ce qui rentre dans ton inventaire",
    "minegoals.screen.claimable": "%s récompense(s) à récupérer",
    "minegoals.screen.tier_reward": "Récompense du palier %s",
    "minegoals.screen.tier_hidden": "Atteins ce palier pour le révéler",
    "minegoals.hud.tier": "Palier %s · %s/%s pts",
    "minegoals.hud.tier_hidden": "Palier %s · %s pts",
    "minegoals.hud.tier_max": "Palier %s · %s pts (max)",
    "minegoals.hud.none": "Tous les objectifs sont terminés !",
    "minegoals.hud.claimable": "%s à récupérer",
    "minegoals.notify.goal_completed": "Objectif terminé !",
    "minegoals.notify.tier_reached": "Palier %s atteint !",
    "minegoals.notify.claim_hint": "Récupère ta récompense dans l'écran des objectifs (G).",
    "minegoals.notify.category_unlocked": "Nouvelle catégorie débloquée !",
    "minegoals.notify.inventory_full": "Inventaire plein : %s objet(s) mis de côté. Récupère-les dans l'écran des objectifs (G).",
    "minegoals.toast.tracked_completed": "Objectif terminé !",
    "minegoals.command.list.header": "%s · %s points",
    "minegoals.command.list.category": " · palier %s/%s · %s pts",
    "minegoals.command.unknown": "Objectif ou catégorie inconnu : %s",
    "minegoals.command.already_completed": "L'objectif %s est déjà terminé",
    "minegoals.command.reset": "%s remis à zéro pour %s",
    "minegoals.command.complete": "%s terminé pour %s",
    "minegoals.command.complete_all": "%s objectifs terminés (%s) pour %s",
    "minegoals.command.unlock": "Catégorie %s débloquée pour %s",
    "minegoals.command.lock": "Catégorie %s verrouillée pour %s",
    "reward.minegoals.fire_resistance": "Résistance au feu (5 min)",
    "reward.minegoals.fire_resistance.desc": "Ignore les boules de feu des blazes dans la forteresse",
    "key.minegoals.open": "Ouvrir les objectifs",
    "key.minegoals.toggle_hud": "Afficher/masquer le HUD des objectifs",
    "key.minegoals.next_category": "Catégorie suivante dans le HUD",
    "key.category.minegoals.main": "MineGoals",
    "item.minegoals.dragon_wings": "Ailes du dragon",
    "item.minegoals.legend_blade": "Lame de légende",
}

# Start from a clean slate so removed goals do not linger.
for folder in ("category", "goal"):
    shutil.rmtree(os.path.join(data, folder), ignore_errors=True)

# Command reward that unlocks a secondary category, shown with the category's icon and name.
for cat in SECONDARY:
    key = f"reward.minegoals.unlock_{cat['id']}"
    en[key], en[key + ".desc"] = f"New category: {cat['en']}", f"Unlocks the {cat['en']} goals"
    fr[key], fr[key + ".desc"] = f"Nouvelle catégorie : {cat['fr']}", f"Débloque les objectifs de la catégorie {cat['fr']}"


def unlock_reward(category):
    return {
        "run": f"minegoals unlock @s minegoals:{category}",
        "icon": SECONDARY_BY_ID[category]["icon"],
        "name": {"translate": f"reward.minegoals.unlock_{category}"},
        "description": {"translate": f"reward.minegoals.unlock_{category}.desc"},
    }


total_goals = 0
for order, cat in enumerate(CATEGORIES):
    cid = cat["id"]
    main = cat is PROGRESSION
    cat_key = f"category.minegoals.{cid}"
    en[cat_key], fr[cat_key] = cat["en"], cat["fr"]

    tiers_json = []
    for t, tier_def in enumerate(cat["tiers"], start=1):
        goals = tier_def["goals"]
        # A tier needs all of its goals' points, minus "skip" points on secondary tiers with frustrating goals.
        assert not (main and "skip" in tier_def)
        tier = {"points": sum(goal[3] for goal in goals) - tier_def.get("skip", 0)}
        if "background" in tier_def:
            tier["background"] = bg(tier_def["background"])
        tier["rewards"] = {**tier_def["reward"], "experience": (25 if main else 50) * t}
        tiers_json.append(tier)

        for goal in goals:
            gid, icon, crit, points, en_t, en_d, fr_t, fr_d = goal[:8]
            extra = dict(goal[8]) if len(goal) > 8 else {}
            unlocks = extra.pop("unlocks", None)
            key = f"goal.minegoals.{cid}.{gid}"
            en[key], en[key + ".desc"] = en_t, en_d
            fr[key], fr[key + ".desc"] = fr_t, fr_d
            obj = {
                "category": f"minegoals:{cid}",
                "tier": t,
                "points": points,
                "title": {"translate": key},
                "description": {"translate": key + ".desc"},
                "icon": icon,
                "criterion": crit,
                "rewards": {"experience": 10 * points},
                # Progress counts before the tier (or the category) unlocks, so nothing has to be done twice.
                "track_before_unlock": True,
            }
            if unlocks:
                obj["rewards"]["commands"] = [unlock_reward(unlocks)]
            obj.update(extra)
            write(os.path.join(data, "goal", cid, gid + ".json"), obj)
            total_goals += 1

    write(os.path.join(data, "category", cid + ".json"), {
        "title": {"translate": cat_key},
        "icon": cat["icon"],
        "order": order,
        "tier_visibility": "next",
        # Secondary categories stay hidden until a Progression goal unlocks them.
        **({} if main else {"unlock": {"default": False}}),
        "background": bg(cat["background"]),
        "tiers": tiers_json,
    })

# Every secondary category must be unlocked by exactly one Progression goal.
unlocked = [g[8]["unlocks"] for t in PROGRESSION["tiers"] for g in t["goals"] if len(g) > 8 and "unlocks" in g[8]]
assert sorted(unlocked) == sorted(SECONDARY_BY_ID), unlocked

write(os.path.join(lang_dir, "en_us.json"), en)
write(os.path.join(lang_dir, "fr_fr.json"), fr)
print(f"{len(CATEGORIES)} categories, {total_goals} goals, {len(en)} lang keys")
for cat in CATEGORIES:
    with open(os.path.join(data, "category", cat["id"] + ".json"), encoding="utf-8") as f:
        print(cat["id"], [t["points"] for t in json.load(f)["tiers"]])
