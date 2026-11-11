"""Generates MineGoals example content: categories, goals and en_us/fr_fr lang files.

Usage (from the project root): python tools/generate_content.py .
The script deletes and rewrites data/minegoals/minegoals/{category,goal} and the two lang files.
Goals live in a sub-folder per category, so their ids look like minegoals:road_to_end/first_table.
"""
import json
import math
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
def H(item, n=1): return {"type": "minegoals:hold_item", "item": item, "count": n}
def VB(biome): return {"type": "minegoals:visit_biome", "biome": biome}
def VD(dim): return {"type": "minegoals:visit_dimension", "dimension": dim}
def ADV(path): return {"type": "minegoals:advancement", "advancement": "minecraft:" + path}
def ST(stat, n=1): return {"type": "minegoals:custom_stat", "stat": "minecraft:" + stat, "count": n}
def items(*pairs): return {"items": [{"id": i, "count": c} for i, c in pairs]}


# A goal: (id, icon, criterion, en title, en description, fr title, fr description[, extra])
# A category: dict with tiers = list of (tier reward, [goals]).

ROAD = {
    "id": "road_to_end", "icon": "minecraft:ender_eye", "order": 0, "visibility": "next", "ratio": 1.0,
    "en": "Road to the End", "fr": "En route vers l'End",
    "tiers": [
        (None, [
            ("lumberjack", "minecraft:oak_log", B("#minecraft:logs", 16), "Lumberjack", "Chop 16 logs", "Bûcheron", "Coupe 16 bûches"),
            ("first_table", "minecraft:crafting_table", CR("minecraft:crafting_table"), "First Table", "Craft a crafting table", "Premier établi", "Fabrique un établi"),
            ("wooden_pickaxe", "minecraft:wooden_pickaxe", CR("minecraft:wooden_pickaxe"), "Getting Started", "Craft a wooden pickaxe", "Premiers pas", "Fabrique une pioche en bois"),
        ]),
        (items(("minecraft:bread", 8)), [
            ("stone_age", "minecraft:cobblestone", ADV("story/mine_stone"), "Stone Age", "Mine stone with your new pickaxe", "L'âge de pierre", "Mine de la pierre avec ta nouvelle pioche"),
            ("better_tools", "minecraft:stone_pickaxe", ADV("story/upgrade_tools"), "Getting an Upgrade", "Craft a stone pickaxe", "Amélioration", "Fabrique une pioche en pierre"),
            ("furnace", "minecraft:furnace", CR("minecraft:furnace"), "Smelter", "Craft a furnace", "Fondeur", "Fabrique un four"),
            ("torches", "minecraft:torch", CR("minecraft:torch", 16), "Light Bringer", "Craft 16 torches", "Porteur de lumière", "Fabrique 16 torches"),
        ]),
        (items(("minecraft:white_bed", 1)), [
            ("first_night", "minecraft:red_bed", ADV("adventure/sleep_in_bed"), "Sweet Dreams", "Sleep in a bed", "Fais de beaux rêves", "Dors dans un lit"),
            ("night_guard", "minecraft:rotten_flesh", K("minecraft:zombie", 5), "Night Guard", "Kill 5 zombies", "Garde de nuit", "Tue 5 zombies"),
            ("coal_miner", "minecraft:coal", B("#minecraft:coal_ores", 16), "Coal Miner", "Mine 16 coal ores", "Mineur de charbon", "Mine 16 minerais de charbon"),
        ]),
        (items(("minecraft:iron_ingot", 8)), [
            ("acquire_hardware", "minecraft:iron_ingot", ADV("story/smelt_iron"), "Acquire Hardware", "Smelt an iron ingot", "Quincaillerie", "Fais fondre un lingot de fer"),
            ("iron_pickaxe", "minecraft:iron_pickaxe", ADV("story/iron_tools"), "Isn't It Iron Pick", "Craft an iron pickaxe", "Pioche en fer", "Fabrique une pioche en fer"),
            ("suit_up", "minecraft:iron_chestplate", ADV("story/obtain_armor"), "Suit Up", "Wear a piece of iron armor", "Équipe-toi", "Porte une pièce d'armure en fer"),
            ("shield", "minecraft:shield", CR("minecraft:shield"), "Shield Up", "Craft a shield", "Bouclier levé", "Fabrique un bouclier"),
        ]),
        (items(("minecraft:golden_apple", 1)), [
            ("diamonds", "minecraft:diamond", ADV("story/mine_diamond"), "Diamonds!", "Find a diamond", "Des diamants !", "Trouve un diamant"),
            ("hot_stuff", "minecraft:lava_bucket", ADV("story/lava_bucket"), "Hot Stuff", "Fill a bucket with lava", "Ça chauffe", "Remplis un seau de lave"),
            ("ice_bucket", "minecraft:obsidian", ADV("story/form_obsidian"), "Ice Bucket Challenge", "Obtain a block of obsidian", "Défi du seau d'eau", "Obtiens un bloc d'obsidienne"),
        ]),
        (items(("minecraft:flint_and_steel", 1), ("minecraft:cooked_beef", 16)), [
            ("we_need_to_go_deeper", "minecraft:netherrack", ADV("story/enter_the_nether"), "We Need to Go Deeper", "Build a portal and enter the Nether", "Il faut creuser plus profond", "Construis un portail et entre dans le Nether"),
            ("fortress", "minecraft:nether_bricks", ADV("nether/find_fortress"), "A Terrible Fortress", "Find a Nether fortress", "Une terrible forteresse", "Trouve une forteresse du Nether"),
        ]),
        (items(("minecraft:arrow", 32)), [
            ("into_fire", "minecraft:blaze_rod", ADV("nether/obtain_blaze_rod"), "Into Fire", "Get a blaze rod", "Dans le feu", "Obtiens un bâton de blaze"),
            ("blaze_hunter", "minecraft:blaze_powder", K("minecraft:blaze", 8), "Blaze Hunter", "Kill 8 blazes", "Chasseur de blazes", "Tue 8 blazes"),
            ("blaze_powder", "minecraft:blaze_powder", CR("minecraft:blaze_powder", 12), "Fuel for the Eyes", "Craft 12 blaze powder", "Carburant pour les yeux", "Fabrique 12 poudres de blaze"),
        ]),
        (items(("minecraft:ender_pearl", 2)), [
            ("ender_hunter", "minecraft:ender_pearl", K("minecraft:enderman", 12), "Ender Hunter", "Kill 12 endermen", "Chasseur d'Endermen", "Tue 12 endermen"),
            ("pearl_collector", "minecraft:ender_pearl", P("minecraft:ender_pearl", 12), "Pearl Collector", "Pick up 12 ender pearls", "Collecteur de perles", "Ramasse 12 perles de l'Ender"),
            ("eyes_of_ender", "minecraft:ender_eye", CR("minecraft:ender_eye", 12), "Eyes of Ender", "Craft 12 eyes of ender", "Yeux de l'Ender", "Fabrique 12 yeux de l'Ender"),
        ]),
        (items(("minecraft:golden_apple", 2)), [
            ("eye_spy", "minecraft:ender_eye", ADV("story/follow_ender_eye"), "Eye Spy", "Follow an eye of ender into a stronghold", "Œil pour œil", "Suis un œil de l'Ender jusqu'à un fort"),
            ("silverfish", "minecraft:stone_bricks", K("minecraft:silverfish", 5), "Pest Control", "Kill 5 silverfish (they hide in stronghold walls)", "Dératisation", "Tue 5 poissons d'argent (ils se cachent dans les murs du fort)"),
        ]),
        (items(("minecraft:arrow", 64), ("minecraft:cooked_beef", 32)), [
            ("bow", "minecraft:bow", CR("minecraft:bow"), "Archer", "Craft a bow", "Archer", "Fabrique un arc"),
            ("quiver", "minecraft:arrow", H("minecraft:arrow", 64), "Full Quiver", "Carry 64 arrows", "Carquois plein", "Porte 64 flèches sur toi"),
            ("blocks", "minecraft:cobblestone", H("minecraft:cobblestone", 128), "Pillar Supplies", "Carry 128 cobblestone", "Provisions de blocs", "Porte 128 pierres taillées sur toi"),
            ("water_bucket", "minecraft:water_bucket", H("minecraft:water_bucket", 1), "Safety Bucket", "Carry a water bucket", "Seau de secours", "Porte un seau d'eau sur toi"),
        ]),
        (None, [
            ("the_end", "minecraft:end_stone", ADV("story/enter_the_end"), "The End?", "Enter the End portal", "La fin ?", "Entre dans le portail de l'End"),
            ("end_stone", "minecraft:end_stone", B("minecraft:end_stone", 16), "Island Souvenir", "Mine 16 end stone", "Souvenir de l'île", "Mine 16 blocs de pierre de l'End"),
        ]),
        (items(("minecraft:elytra", 1)), [
            ("free_the_end", "minecraft:dragon_head", ADV("end/kill_dragon"), "Free the End", "Slay the Ender Dragon", "Libère l'End", "Tue le dragon de l'Ender"),
            ("next_generation", "minecraft:dragon_egg", ADV("end/dragon_egg"), "The Next Generation", "Hold the dragon egg", "La génération suivante", "Tiens l'œuf de dragon"),
        ]),
    ],
}

OCEANS = {
    "id": "oceans", "icon": "minecraft:heart_of_the_sea", "order": 1, "visibility": "visible", "ratio": 0.75,
    "en": "Oceans", "fr": "Océans", "tier_items": ["minecraft:cod", "minecraft:prismarine_shard", "minecraft:nautilus_shell", "minecraft:heart_of_the_sea"],
    "tiers": [
        (None, [
            ("angler", "minecraft:fishing_rod", ST("fish_caught", 10), "Angler", "Catch 10 fish", "Pêcheur", "Pêche 10 poissons"),
            ("open_sea", "minecraft:oak_boat", VB("#minecraft:is_ocean"), "Open Sea", "Reach an ocean", "Le grand large", "Atteins un océan"),
            ("kelp_farmer", "minecraft:kelp", P("minecraft:kelp", 32), "Kelp Farmer", "Pick up 32 kelp", "Cultivateur d'algues", "Ramasse 32 algues"),
        ]),
        (None, [
            ("tactical_fishing", "minecraft:cod_bucket", ADV("husbandry/tactical_fishing"), "Tactical Fishing", "Catch a fish with a bucket", "Pêche tactique", "Attrape un poisson avec un seau"),
            ("drowned_hunter", "minecraft:trident", K("minecraft:drowned", 10), "Drowned Hunter", "Kill 10 drowned", "Chasseur de noyés", "Tue 10 noyés"),
            ("coral_reef", "minecraft:tube_coral", VB("minecraft:warm_ocean"), "Coral Reef", "Swim in a warm ocean", "Récif de corail", "Nage dans un océan chaud"),
            ("cutest_predator", "minecraft:axolotl_bucket", ADV("husbandry/axolotl_in_a_bucket"), "The Cutest Predator", "Catch an axolotl in a bucket", "Le plus mignon des prédateurs", "Attrape un axolotl dans un seau"),
        ]),
        (None, [
            ("monument_guardians", "minecraft:prismarine_shard", K("minecraft:guardian", 10), "Monument Raider", "Kill 10 guardians", "Pilleur de monument", "Tue 10 gardiens"),
            ("elder_guardian", "minecraft:prismarine_crystals", K("minecraft:elder_guardian", 1), "Elder Slayer", "Kill an elder guardian", "Tueur d'ancien", "Tue un gardien ancien"),
            ("sponges", "minecraft:wet_sponge", B("minecraft:wet_sponge", 5), "Sponge Collector", "Mine 5 wet sponges", "Collecteur d'éponges", "Mine 5 éponges mouillées"),
        ]),
        (None, [
            ("trident", "minecraft:trident", ADV("adventure/throw_trident"), "A Throwaway Joke", "Throw a trident at something", "Une blague jetable", "Lance un trident sur quelque chose"),
            ("conduit", "minecraft:conduit", CR("minecraft:conduit"), "Power of the Sea", "Craft a conduit", "Puissance de la mer", "Fabrique un conduit"),
        ]),
    ],
}

FARMS = {
    "id": "farms", "icon": "minecraft:wheat", "order": 2, "visibility": "visible", "ratio": 0.75,
    "en": "Farms & Animals", "fr": "Fermes et animaux", "tier_items": ["minecraft:bone_meal", "minecraft:lead", "minecraft:golden_carrot", "minecraft:enchanted_golden_apple"],
    "tiers": [
        (None, [
            ("seedy_place", "minecraft:wheat_seeds", ADV("husbandry/plant_seed"), "A Seedy Place", "Plant a seed", "Un endroit fertile", "Plante une graine"),
            ("harvest", "minecraft:wheat", P("minecraft:wheat", 32), "Harvest", "Pick up 32 wheat", "Moisson", "Ramasse 32 blés"),
            ("parrots_and_bats", "minecraft:egg", ADV("husbandry/breed_an_animal"), "The Parrots and the Bats", "Breed two animals together", "Les perroquets et les chauves-souris", "Fais se reproduire deux animaux"),
        ]),
        (None, [
            ("rancher", "minecraft:hay_block", ST("animals_bred", 20), "Rancher", "Breed 20 animals", "Éleveur", "Fais naître 20 animaux"),
            ("best_friends", "minecraft:bone", ADV("husbandry/tame_an_animal"), "Best Friends Forever", "Tame an animal", "Meilleurs amis pour la vie", "Apprivoise un animal"),
            ("baker", "minecraft:cake", CR("minecraft:cake"), "Baker", "Bake a cake", "Pâtissier", "Prépare un gâteau"),
            ("bee_our_guest", "minecraft:honey_bottle", ADV("husbandry/safely_harvest_honey"), "Bee Our Guest", "Harvest honey without angering bees", "Une abeille dans l'assiette", "Récolte du miel sans énerver les abeilles"),
        ]),
        (None, [
            ("golden_carrots", "minecraft:golden_carrot", CR("minecraft:golden_carrot", 16), "Gourmet", "Craft 16 golden carrots", "Gourmet", "Fabrique 16 carottes dorées"),
            ("total_beelocation", "minecraft:bee_nest", ADV("husbandry/silk_touch_nest"), "Total Beelocation", "Move a bee nest with Silk Touch", "Délocalisation", "Déplace un nid d'abeilles avec Toucher de soie"),
            ("sniffer_egg", "minecraft:sniffer_egg", ADV("husbandry/obtain_sniffer_egg"), "Smells Interesting", "Obtain a sniffer egg", "Ça sent bon", "Obtiens un œuf de renifleur"),
        ]),
        (None, [
            ("two_by_two", "minecraft:golden_apple", ADV("husbandry/bred_all_animals"), "Two by Two", "Breed every animal", "Deux par deux", "Fais se reproduire tous les animaux"),
            ("balanced_diet", "minecraft:apple", ADV("husbandry/balanced_diet"), "A Balanced Diet", "Eat everything edible", "Une alimentation équilibrée", "Mange tout ce qui se mange"),
        ]),
    ],
}

VILLAGES = {
    "id": "villages", "icon": "minecraft:emerald", "order": 3, "visibility": "visible", "ratio": 0.75,
    "en": "Villages & Trading", "fr": "Villages et commerce", "tier_items": ["minecraft:emerald", "minecraft:bell", "minecraft:totem_of_undying", "minecraft:emerald_block"],
    "tiers": [
        (None, [
            ("small_talk", "minecraft:emerald", ST("talked_to_villager", 1), "Small Talk", "Talk to a villager", "Papotage", "Parle à un villageois"),
            ("whats_the_deal", "minecraft:emerald", ADV("adventure/trade"), "What a Deal!", "Trade with a villager", "Quelle affaire !", "Fais un échange avec un villageois"),
            ("ring_the_bell", "minecraft:bell", ST("bell_ring", 1), "Ring the Bell", "Ring a village bell", "Sonne la cloche", "Fais sonner la cloche d'un village"),
        ]),
        (None, [
            ("merchant", "minecraft:emerald", ST("traded_with_villager", 20), "Merchant", "Trade 20 times with villagers", "Marchand", "Fais 20 échanges avec des villageois"),
            ("hired_help", "minecraft:carved_pumpkin", ADV("adventure/summon_iron_golem"), "Hired Help", "Build an iron golem", "Main-d'œuvre", "Construis un golem de fer"),
            ("doctor", "minecraft:golden_apple", ADV("story/cure_zombie_villager"), "Zombie Doctor", "Cure a zombie villager", "Docteur zombie", "Guéris un zombie villageois"),
        ]),
        (None, [
            ("voluntary_exile", "minecraft:white_banner", ADV("adventure/voluntary_exile"), "Voluntary Exile", "Kill a raid captain", "Exil volontaire", "Tue un capitaine de raid"),
            ("pillager_hunter", "minecraft:crossbow", K("minecraft:pillager", 15), "Pillager Hunter", "Kill 15 pillagers", "Chasseur de pillards", "Tue 15 pillards"),
            ("hero", "minecraft:totem_of_undying", ADV("adventure/hero_of_the_village"), "Hero of the Village", "Defend a village from a raid", "Héros du village", "Défends un village contre un raid"),
        ]),
        (None, [
            ("tycoon", "minecraft:emerald_block", ST("traded_with_villager", 100), "Tycoon", "Trade 100 times with villagers", "Magnat", "Fais 100 échanges avec des villageois"),
            ("evoker", "minecraft:totem_of_undying", K("minecraft:evoker", 1), "Evoker Slayer", "Kill an evoker", "Tueur d'évocateur", "Tue un évocateur"),
        ]),
    ],
}

MAGIC = {
    "id": "magic", "icon": "minecraft:enchanting_table", "order": 4, "visibility": "visible", "ratio": 0.75,
    "en": "Magic", "fr": "Magie", "tier_items": ["minecraft:lapis_lazuli", "minecraft:experience_bottle", "minecraft:blaze_rod", "minecraft:nether_star"],
    "tiers": [
        (None, [
            ("library", "minecraft:bookshelf", CR("minecraft:bookshelf", 15), "Librarian", "Craft 15 bookshelves", "Bibliothécaire", "Fabrique 15 bibliothèques"),
            ("enchanting_table", "minecraft:enchanting_table", CR("minecraft:enchanting_table"), "Spell Book", "Craft an enchanting table", "Grimoire", "Fabrique une table d'enchantement"),
            ("enchanter", "minecraft:enchanted_book", ADV("story/enchant_item"), "Enchanter", "Enchant an item", "Enchanteur", "Enchante un objet"),
        ]),
        (None, [
            ("arcanist", "minecraft:enchanted_book", ST("enchant_item", 10), "Arcanist", "Enchant 10 items", "Arcaniste", "Enchante 10 objets"),
            ("anvil", "minecraft:anvil", ST("interact_with_anvil", 5), "Blacksmith", "Use an anvil 5 times", "Forgeron", "Utilise une enclume 5 fois"),
            ("brewing_stand", "minecraft:brewing_stand", CR("minecraft:brewing_stand"), "Alchemist's Lab", "Craft a brewing stand", "Laboratoire d'alchimiste", "Fabrique un alambic"),
        ]),
        (None, [
            ("local_brewery", "minecraft:potion", ADV("nether/brew_potion"), "Local Brewery", "Brew a potion", "Brasserie locale", "Prépare une potion"),
            ("potion_master", "minecraft:splash_potion", ST("interact_with_brewingstand", 20), "Potion Master", "Use a brewing stand 20 times", "Maître des potions", "Utilise un alambic 20 fois"),
            ("chiseled_bookshelf", "minecraft:chiseled_bookshelf", ADV("adventure/read_power_of_chiseled_bookshelf"), "The Power of Books", "Read a chiseled bookshelf's signal with a comparator", "Le pouvoir des livres", "Lis le signal d'une bibliothèque sculptée avec un comparateur"),
        ]),
        (None, [
            ("bring_home_the_beacon", "minecraft:beacon", ADV("nether/create_beacon"), "Bring Home the Beacon", "Build and place a beacon", "Ramène la balise", "Construis et pose une balise"),
            ("beaconator", "minecraft:beacon", ADV("nether/create_full_beacon"), "Beaconator", "Power a beacon to full strength", "Balisateur", "Alimente une balise à pleine puissance"),
        ]),
    ],
}

CAVES = {
    "id": "caves", "icon": "minecraft:sculk_catalyst", "order": 5, "visibility": "next", "ratio": 0.75,
    "en": "Caves & Depths", "fr": "Grottes et profondeurs", "tier_items": ["minecraft:torch", "minecraft:amethyst_shard", "minecraft:echo_shard", "minecraft:trial_key"],
    "tiers": [
        (None, [
            ("deepslate", "minecraft:deepslate", B("minecraft:deepslate", 128), "Deep Digger", "Mine 128 deepslate", "Creuseur des profondeurs", "Mine 128 blocs d'ardoise des abîmes"),
            ("dripstone_caves", "minecraft:pointed_dripstone", VB("minecraft:dripstone_caves"), "Stalactites", "Explore dripstone caves", "Stalactites", "Explore des grottes de spéléothèmes"),
            ("lush_caves", "minecraft:glow_berries", VB("minecraft:lush_caves"), "Hidden Garden", "Explore lush caves", "Jardin caché", "Explore des grottes luxuriantes"),
        ]),
        (None, [
            ("geode", "minecraft:amethyst_shard", P("minecraft:amethyst_shard", 16), "Geode Hunter", "Pick up 16 amethyst shards", "Chasseur de géodes", "Ramasse 16 éclats d'améthyste"),
            ("deep_diamonds", "minecraft:diamond_ore", B("#minecraft:diamond_ores", 8), "Deep Diamonds", "Mine 8 diamond ores", "Diamants des profondeurs", "Mine 8 minerais de diamant"),
            ("deep_dark", "minecraft:sculk", VB("minecraft:deep_dark"), "The Deep Dark", "Enter the deep dark", "Les abysses", "Entre dans les abysses"),
        ]),
        (None, [
            ("sneak_100", "minecraft:sculk_sensor", ADV("adventure/avoid_vibration"), "Sneak 100", "Sneak past a sculk sensor or warden", "Discrétion 100", "Passe en douce près d'un capteur sculk ou d'un Warden"),
            ("it_spreads", "minecraft:sculk_catalyst", ADV("adventure/kill_mob_near_sculk_catalyst"), "It Spreads", "Kill a mob near a sculk catalyst", "Ça se propage", "Tue un monstre près d'un catalyseur sculk"),
            ("recovery_compass", "minecraft:recovery_compass", CR("minecraft:recovery_compass"), "Lost and Found", "Craft a recovery compass", "Objets trouvés", "Fabrique une boussole de rappel"),
        ]),
        (None, [
            ("trial_chambers", "minecraft:trial_spawner", ADV("adventure/minecraft_trials_edition"), "Minecraft: Trial(s) Edition", "Step into a trial chamber", "Minecraft : édition épreuves", "Entre dans une chambre des épreuves"),
            ("under_lock_and_key", "minecraft:vault", ADV("adventure/under_lock_and_key"), "Under Lock and Key", "Unlock a vault with a trial key", "Sous clé", "Ouvre un coffre-fort avec une clé des épreuves"),
            ("warden", "minecraft:echo_shard", K("minecraft:warden", 1), "Silence", "Defeat a warden", "Silence", "Vaincs un Warden"),
        ]),
    ],
}

NETHER = {
    "id": "nether", "icon": "minecraft:netherite_ingot", "order": 6, "visibility": "visible", "ratio": 0.75,
    "en": "Advanced Nether", "fr": "Nether avancé", "tier_items": ["minecraft:gold_ingot", "minecraft:crying_obsidian", "minecraft:netherite_scrap", "minecraft:netherite_upgrade_smithing_template"],
    "tiers": [
        (None, [
            ("crimson_forest", "minecraft:crimson_fungus", VB("minecraft:crimson_forest"), "Crimson Forest", "Visit a crimson forest", "Forêt écarlate", "Visite une forêt écarlate"),
            ("warped_forest", "minecraft:warped_fungus", VB("minecraft:warped_forest"), "Warped Forest", "Visit a warped forest", "Forêt biscornue", "Visite une forêt biscornue"),
            ("oh_shiny", "minecraft:gold_ingot", ADV("nether/distract_piglin"), "Oh Shiny", "Distract a piglin with gold", "Oh, ça brille", "Distrais un piglin avec de l'or"),
            ("crying_obsidian", "minecraft:crying_obsidian", ADV("nether/obtain_crying_obsidian"), "Who Is Cutting Onions?", "Obtain crying obsidian", "Qui coupe des oignons ?", "Obtiens de l'obsidienne pleureuse"),
        ]),
        (None, [
            ("bastion", "minecraft:polished_blackstone_bricks", ADV("nether/find_bastion"), "Those Were the Days", "Enter a bastion remnant", "C'était le bon temps", "Entre dans les vestiges d'un bastion"),
            ("war_pigs", "minecraft:chest", ADV("nether/loot_bastion"), "War Pigs", "Loot a bastion chest", "Cochons de guerre", "Pille un coffre de bastion"),
            ("strider", "minecraft:warped_fungus_on_a_stick", ADV("nether/ride_strider"), "This Boat Has Legs", "Ride a strider", "Ce bateau a des jambes", "Chevauche un arpenteur"),
            ("respawn_anchor", "minecraft:respawn_anchor", ADV("nether/charge_respawn_anchor"), "Not Quite \"Nine\" Lives", "Charge a respawn anchor", "Pas tout à fait neuf vies", "Charge une ancre de réapparition"),
        ]),
        (None, [
            ("hidden_depths", "minecraft:ancient_debris", ADV("nether/obtain_ancient_debris"), "Hidden in the Depths", "Obtain ancient debris", "Caché dans les profondeurs", "Obtiens des débris antiques"),
            ("netherite_ingot", "minecraft:netherite_ingot", CR("minecraft:netherite_ingot"), "Netherite", "Craft a netherite ingot", "Netherite", "Fabrique un lingot de netherite"),
            ("return_to_sender", "minecraft:fire_charge", ADV("nether/return_to_sender"), "Return to Sender", "Kill a ghast with its own fireball", "Retour à l'envoyeur", "Tue un ghast avec sa propre boule de feu"),
        ]),
        (None, [
            ("cover_me_in_debris", "minecraft:netherite_chestplate", ADV("nether/netherite_armor"), "Cover Me in Debris", "Wear a full set of netherite armor", "Couvert de débris", "Porte une armure complète en netherite"),
            ("spooky_scary_skeleton", "minecraft:wither_skeleton_skull", ADV("nether/get_wither_skull"), "Spooky Scary Skeleton", "Obtain a wither skeleton skull", "Squelette effrayant", "Obtiens un crâne de wither squelette"),
            ("wither", "minecraft:nether_star", K("minecraft:wither", 1), "Withering Heights", "Defeat the Wither", "Les Hauts du Wither", "Vaincs le Wither"),
        ]),
    ],
}

WORLD = {
    "id": "world", "icon": "minecraft:filled_map", "order": 7, "visibility": "visible", "ratio": 0.75,
    "en": "World Exploration", "fr": "Exploration du monde", "tier_items": ["minecraft:map", "minecraft:spyglass", "minecraft:lodestone", "minecraft:elytra"],
    "tiers": [
        (None, [
            ("forest", "minecraft:oak_sapling", VB("#minecraft:is_forest"), "Into the Woods", "Visit a forest", "Dans les bois", "Visite une forêt"),
            ("desert", "minecraft:sand", VB("minecraft:desert"), "Dune Walker", "Visit a desert", "Marcheur des dunes", "Visite un désert"),
            ("mountain", "minecraft:snowball", VB("#minecraft:is_mountain"), "Mountaineer", "Climb a mountain", "Alpiniste", "Escalade une montagne"),
            ("cartographer", "minecraft:map", CR("minecraft:map"), "Cartographer", "Craft a map", "Cartographe", "Fabrique une carte"),
        ]),
        (None, [
            ("jungle", "minecraft:jungle_sapling", VB("#minecraft:is_jungle"), "Jungle Explorer", "Visit a jungle", "Explorateur de jungle", "Visite une jungle"),
            ("mushroom_fields", "minecraft:red_mushroom", VB("minecraft:mushroom_fields"), "Mushroom Island", "Find a mushroom island", "Île aux champignons", "Trouve une île aux champignons"),
            ("cherry_grove", "minecraft:cherry_sapling", VB("minecraft:cherry_grove"), "Cherry Blossom", "Visit a cherry grove", "Fleurs de cerisier", "Visite un bosquet de cerisiers"),
            ("badlands", "minecraft:terracotta", VB("#minecraft:is_badlands"), "Badlands", "Visit the badlands", "Badlands", "Visite des badlands"),
        ]),
        (None, [
            ("respecting_remnants", "minecraft:brush", ADV("adventure/salvage_sherd"), "Respecting the Remnants", "Brush a suspicious block to get a pottery sherd", "Respect des vestiges", "Brosse un bloc suspect pour obtenir un tesson"),
            ("country_lode", "minecraft:lodestone", ADV("adventure/use_lodestone"), "Country Lode, Take Me Home", "Use a compass on a lodestone", "Magnétite, ramène-moi", "Utilise une boussole sur une magnétite"),
            ("ice_spikes", "minecraft:packed_ice", VB("minecraft:ice_spikes"), "Frozen Spikes", "Visit an ice spikes biome", "Pics gelés", "Visite un biome de pics de glace"),
            ("sound_of_music", "minecraft:jukebox", ADV("adventure/play_jukebox_in_meadows"), "Sound of Music", "Play music in a meadow", "La mélodie du bonheur", "Joue de la musique dans une prairie"),
        ]),
        (None, [
            ("end_city", "minecraft:purpur_block", ADV("end/find_end_city"), "The City at the End of the Game", "Find an End city", "La cité au bout du jeu", "Trouve une cité de l'End"),
            ("sky_is_the_limit", "minecraft:elytra", ADV("end/elytra"), "Sky's the Limit", "Find an elytra", "Le ciel pour limite", "Trouve des élytres"),
            ("adventuring_time", "minecraft:diamond_boots", ADV("adventure/adventuring_time"), "Adventuring Time", "Discover every biome", "L'heure de l'aventure", "Découvre tous les biomes"),
        ]),
    ],
}

COMBAT = {
    "id": "combat", "icon": "minecraft:iron_sword", "order": 8, "visibility": "visible", "ratio": 0.75,
    "en": "Combat", "fr": "Combat", "tier_items": ["minecraft:arrow", "minecraft:shield", "minecraft:crossbow", "minecraft:totem_of_undying"],
    "tiers": [
        (None, [
            ("zombies", "minecraft:rotten_flesh", K("minecraft:zombie", 20), "Zombie Slayer", "Kill 20 zombies", "Tueur de zombies", "Tue 20 zombies"),
            ("skeletons", "minecraft:bone", K("minecraft:skeleton", 20), "Bone Collector", "Kill 20 skeletons", "Collecteur d'os", "Tue 20 squelettes"),
            ("spiders", "minecraft:string", K("minecraft:spider", 10), "Spider Squasher", "Kill 10 spiders", "Écraseur d'araignées", "Tue 10 araignées"),
            ("creepers", "minecraft:gunpowder", K("minecraft:creeper", 10), "Bomb Squad", "Kill 10 creepers", "Démineur", "Tue 10 creepers"),
        ]),
        (None, [
            ("sniper_duel", "minecraft:bow", ADV("adventure/sniper_duel"), "Sniper Duel", "Kill a skeleton from 50 blocks away", "Duel de snipers", "Tue un squelette à plus de 50 blocs"),
            ("not_today", "minecraft:shield", ADV("story/deflect_arrow"), "Not Today, Thank You", "Block a projectile with a shield", "Pas aujourd'hui, merci", "Bloque un projectile avec un bouclier"),
            ("witches", "minecraft:glass_bottle", K("minecraft:witch", 3), "Witch Hunter", "Kill 3 witches", "Chasseur de sorcières", "Tue 3 sorcières"),
            ("phantoms", "minecraft:phantom_membrane", K("minecraft:phantom", 5), "Insomnia Cure", "Kill 5 phantoms", "Remède contre l'insomnie", "Tue 5 fantômes"),
        ]),
        (None, [
            ("ol_betsy", "minecraft:crossbow", ADV("adventure/ol_betsy"), "Ol' Betsy", "Shoot a crossbow", "La vieille Betsy", "Tire à l'arbalète"),
            ("bullseye", "minecraft:target", ADV("adventure/bullseye"), "Bullseye", "Hit the bullseye of a target from 30 blocks", "Dans le mille", "Touche le centre d'une cible à plus de 30 blocs"),
            ("ravager", "minecraft:saddle", K("minecraft:ravager", 1), "Beast Tamer", "Kill a ravager", "Dompteur de bêtes", "Tue un ravageur"),
            ("breezes", "minecraft:breeze_rod", K("minecraft:breeze", 5), "Breeze Breaker", "Kill 5 breezes", "Briseur de brises", "Tue 5 brises"),
        ]),
        (None, [
            ("monsters_hunted", "minecraft:diamond_sword", ADV("adventure/kill_all_mobs"), "Monsters Hunted", "Kill one of every hostile monster", "Monstres chassés", "Tue un exemplaire de chaque monstre hostile"),
            ("over_overkill", "minecraft:mace", ADV("adventure/overoverkill"), "Over-Overkill", "Deal 50 hearts of damage with a mace", "Sur-surpuissance", "Inflige 50 cœurs de dégâts avec une masse"),
            ("postmortal", "minecraft:totem_of_undying", ADV("adventure/totem_of_undying"), "Postmortal", "Use a totem of undying to cheat death", "Post-mortem", "Utilise un totem d'immortalité pour tromper la mort"),
        ]),
    ],
}

# Item rewards for Road to the End goals, on top of their XP: each one helps with the next steps.
ROAD_ITEMS = {
    "lumberjack": [("minecraft:apple", 3)],
    "first_table": [("minecraft:stick", 8)],
    "wooden_pickaxe": [("minecraft:torch", 4)],
    "stone_age": [("minecraft:coal", 8)],
    "better_tools": [("minecraft:stone_sword", 1)],
    "furnace": [("minecraft:cooked_porkchop", 4)],
    "torches": [("minecraft:coal", 8)],
    "first_night": [("minecraft:bread", 4)],
    "night_guard": [("minecraft:iron_ingot", 2)],
    "coal_miner": [("minecraft:torch", 16)],
    "acquire_hardware": [("minecraft:iron_ingot", 4)],
    "iron_pickaxe": [("minecraft:bread", 8)],
    "suit_up": [("minecraft:iron_ingot", 6)],
    "shield": [("minecraft:arrow", 16)],
    "diamonds": [("minecraft:diamond", 1)],
    "hot_stuff": [("minecraft:bucket", 1)],
    "ice_bucket": [("minecraft:obsidian", 4)],
    "we_need_to_go_deeper": [("minecraft:gold_ingot", 4)],
    "fortress": [("minecraft:golden_carrot", 8)],
    "into_fire": [("minecraft:golden_apple", 1)],
    "blaze_hunter": [("minecraft:blaze_rod", 2)],
    "blaze_powder": [("minecraft:ender_pearl", 2)],
    "ender_hunter": [("minecraft:ender_pearl", 2)],
    "pearl_collector": [("minecraft:carved_pumpkin", 1)],
    "eyes_of_ender": [("minecraft:ender_eye", 2)],
    "eye_spy": [("minecraft:iron_ingot", 8)],
    "silverfish": [("minecraft:torch", 32)],
    "bow": [("minecraft:arrow", 32)],
    "quiver": [("minecraft:spectral_arrow", 16)],
    "blocks": [("minecraft:cobblestone", 64)],
    "water_bucket": [("minecraft:golden_apple", 1)],
    "the_end": [("minecraft:golden_apple", 2)],
    "end_stone": [("minecraft:arrow", 32)],
    "free_the_end": [("minecraft:experience_bottle", 16)],
    "next_generation": [("minecraft:totem_of_undying", 1)],
}

CATEGORIES = [ROAD, OCEANS, FARMS, VILLAGES, MAGIC, CAVES, NETHER, WORLD, COMBAT]

en = {
    "minegoals.screen.title": "MineGoals",
    "minegoals.screen.level": "Level %s · %s pts",
    "minegoals.screen.empty": "No goals loaded",
    "minegoals.screen.no_goals": "No goals in this tier",
    "minegoals.screen.tier": "Tier %s",
    "minegoals.screen.tier_short": "Tier %s",
    "minegoals.screen.tier_threshold": "Requires %s points",
    "minegoals.screen.category_progress": "%s / %s points to the next tier",
    "minegoals.screen.category_points": "%s points",
    "minegoals.screen.category_max": "%s points · all tiers reached",
    "minegoals.screen.completed": "Completed",
    "minegoals.screen.locked_tier": "Locked · reach tier %s",
    "minegoals.screen.locked_tier_tracked": "Locked · reach tier %s · progress already counts",
    "minegoals.screen.locked_requires": "Locked · requires: %s",
    "minegoals.screen.rewards": "Rewards:",
    "minegoals.screen.reward_xp": "%s XP",
    "minegoals.screen.reward_special": "Special reward",
    "minegoals.screen.claim": "Claim",
    "minegoals.screen.claimed": "Claimed",
    "minegoals.screen.claimable": "%s reward(s) to claim",
    "minegoals.screen.tier_reward": "Tier %s reward",
    "minegoals.screen.tier_hidden": "Reach this tier to reveal it",
    "minegoals.hud.level": "Level %s",
    "minegoals.hud.tier": "Tier %s · %s/%s pts",
    "minegoals.hud.tier_hidden": "Tier %s · %s pts",
    "minegoals.hud.tier_max": "Tier %s · %s pts (max)",
    "minegoals.hud.none": "All goals done!",
    "minegoals.hud.claimable": "%s to claim",
    "minegoals.notify.goal_completed": "Goal completed!",
    "minegoals.notify.tier_reached": "Tier %s reached!",
    "minegoals.notify.claim_hint": "Claim your reward in the goals screen (G).",
    "minegoals.command.list.header": "%s · level %s · %s points",
    "minegoals.command.list.category": " · tier %s/%s · %s pts",
    "minegoals.command.unknown": "Unknown goal or category: %s",
    "minegoals.command.already_completed": "Goal %s is already completed",
    "minegoals.command.reset": "Reset %s for %s",
    "minegoals.command.complete": "Completed %s for %s",
    "key.minegoals.open": "Open goals",
    "key.minegoals.toggle_hud": "Toggle goals HUD",
    "key.minegoals.next_category": "Next category in goals HUD",
    "key.category.minegoals.main": "MineGoals",
}
fr = {
    "minegoals.screen.title": "MineGoals",
    "minegoals.screen.level": "Niveau %s · %s pts",
    "minegoals.screen.empty": "Aucun objectif chargé",
    "minegoals.screen.no_goals": "Aucun objectif dans ce palier",
    "minegoals.screen.tier": "Palier %s",
    "minegoals.screen.tier_short": "Palier %s",
    "minegoals.screen.tier_threshold": "Nécessite %s points",
    "minegoals.screen.category_progress": "%s / %s points vers le palier suivant",
    "minegoals.screen.category_points": "%s points",
    "minegoals.screen.category_max": "%s points · tous les paliers atteints",
    "minegoals.screen.completed": "Terminé",
    "minegoals.screen.locked_tier": "Verrouillé · atteins le palier %s",
    "minegoals.screen.locked_tier_tracked": "Verrouillé · atteins le palier %s · la progression compte déjà",
    "minegoals.screen.locked_requires": "Verrouillé · nécessite : %s",
    "minegoals.screen.rewards": "Récompenses :",
    "minegoals.screen.reward_xp": "%s XP",
    "minegoals.screen.reward_special": "Récompense spéciale",
    "minegoals.screen.claim": "Récupérer",
    "minegoals.screen.claimed": "Récupéré",
    "minegoals.screen.claimable": "%s récompense(s) à récupérer",
    "minegoals.screen.tier_reward": "Récompense du palier %s",
    "minegoals.screen.tier_hidden": "Atteins ce palier pour le révéler",
    "minegoals.hud.level": "Niveau %s",
    "minegoals.hud.tier": "Palier %s · %s/%s pts",
    "minegoals.hud.tier_hidden": "Palier %s · %s pts",
    "minegoals.hud.tier_max": "Palier %s · %s pts (max)",
    "minegoals.hud.none": "Tous les objectifs sont terminés !",
    "minegoals.hud.claimable": "%s à récupérer",
    "minegoals.notify.goal_completed": "Objectif terminé !",
    "minegoals.notify.tier_reached": "Palier %s atteint !",
    "minegoals.notify.claim_hint": "Récupère ta récompense dans l'écran des objectifs (G).",
    "minegoals.command.list.header": "%s · niveau %s · %s points",
    "minegoals.command.list.category": " · palier %s/%s · %s pts",
    "minegoals.command.unknown": "Objectif ou catégorie inconnu : %s",
    "minegoals.command.already_completed": "L'objectif %s est déjà terminé",
    "minegoals.command.reset": "%s remis à zéro pour %s",
    "minegoals.command.complete": "%s terminé pour %s",
    "key.minegoals.open": "Ouvrir les objectifs",
    "key.minegoals.toggle_hud": "Afficher/masquer le HUD des objectifs",
    "key.minegoals.next_category": "Catégorie suivante dans le HUD",
    "key.category.minegoals.main": "MineGoals",
}

# Start from a clean slate so removed goals do not linger.
for folder in ("category", "goal"):
    shutil.rmtree(os.path.join(data, folder), ignore_errors=True)

total_goals = 0
for cat in CATEGORIES:
    cid = cat["id"]
    road = cat["ratio"] >= 1.0
    cat_key = f"category.minegoals.{cid}"
    en[cat_key], fr[cat_key] = cat["en"], cat["fr"]

    tiers_json = []
    cumulative = 0
    previous_threshold = -1
    for t, (tier_reward, goals) in enumerate(cat["tiers"], start=1):
        # Threshold: points of all earlier goals (road) or 75% of them, rounded down to 5 (themes).
        if t == 1:
            threshold = 0
        elif road:
            threshold = cumulative
        else:
            threshold = max(previous_threshold + 5, int(math.floor(cumulative * cat["ratio"] / 5.0)) * 5)
        previous_threshold = threshold

        if tier_reward is None and not road and t > 1:
            tier_reward = {"items": [{"id": cat["tier_items"][t - 1], "count": 4 if t < 4 else 1}], "experience": 50 * t}
        elif tier_reward is not None and t > 1:
            tier_reward = {**tier_reward, "experience": 25 * t}
        tier = {"points": threshold}
        if tier_reward:
            tier["rewards"] = tier_reward
        tiers_json.append(tier)

        points = 10 + 5 * (t - 1) if road else 10 * t
        for goal in goals:
            gid, icon, crit, en_t, en_d, fr_t, fr_d = goal[:7]
            extra = goal[7] if len(goal) > 7 else {}
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
                "rewards": {"experience": points},
            }
            if road and gid in ROAD_ITEMS:
                obj["rewards"]["items"] = [{"id": i, "count": c} for i, c in ROAD_ITEMS[gid]]
            # Themed categories count progress before a tier unlocks; the road stays step by step.
            if not road:
                obj["track_before_unlock"] = True
            obj.update(extra)
            write(os.path.join(data, "goal", cid, gid + ".json"), obj)
            total_goals += 1
            cumulative += points

    write(os.path.join(data, "category", cid + ".json"), {
        "title": {"translate": cat_key},
        "icon": cat["icon"],
        "order": cat["order"],
        "tier_visibility": cat["visibility"],
        "tiers": tiers_json,
    })

write(os.path.join(lang_dir, "en_us.json"), en)
write(os.path.join(lang_dir, "fr_fr.json"), fr)
print(f"{len(CATEGORIES)} categories, {total_goals} goals, {len(en)} lang keys")
for cat in CATEGORIES:
    with open(os.path.join(data, "category", cat["id"] + ".json"), encoding="utf-8") as f:
        print(cat["id"], [t["points"] for t in json.load(f)["tiers"]])
