package com.eikins.minegoals.data;

import com.eikins.minegoals.Constants;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Server reload listener reading {@code minegoals/category} and {@code minegoals/goal} from datapacks.
 * Both folders are read together so goals can be validated against categories. Files are parsed with registry
 * access, so item rewards can use data components that point at datapack registries (enchantments, trims...).
 */
public class GoalDataLoader extends SimplePreparableReloadListener<GoalDefinitions> {

    public static final Identifier ID = Constants.id("goals");

    private static final FileToIdConverter CATEGORIES = FileToIdConverter.json("minegoals/category");
    private static final FileToIdConverter GOALS = FileToIdConverter.json("minegoals/goal");

    private final DynamicOps<JsonElement> ops;

    public GoalDataLoader(HolderLookup.Provider registries) {
        this.ops = registries.createSerializationContext(JsonOps.INSTANCE);
    }

    @Override
    protected GoalDefinitions prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, Category> categories = load(manager, CATEGORIES, Category.CODEC, this.ops);
        Map<Identifier, Goal> goals = load(manager, GOALS, Goal.CODEC, this.ops);
        return GoalDefinitions.validated(categories, goals);
    }

    @Override
    protected void apply(GoalDefinitions definitions, ResourceManager manager, ProfilerFiller profiler) {
        GoalDefinitions.setServer(definitions);
        Constants.LOG.info("Loaded {} goal categories and {} goals",
                definitions.categories().size(), definitions.goals().size());
    }

    private static <T> Map<Identifier, T> load(ResourceManager manager, FileToIdConverter lister, Codec<T> codec,
                                              DynamicOps<JsonElement> ops) {
        Map<Identifier, T> result = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : lister.listMatchingResources(manager).entrySet()) {
            Identifier id = lister.fileToId(entry.getKey());
            try (Reader reader = entry.getValue().openAsReader()) {
                codec.parse(ops, StrictJsonParser.parse(reader))
                        .ifSuccess(value -> result.put(id, value))
                        .ifError(error -> Constants.LOG.error("Couldn't parse {} ({}): {}", id, entry.getKey(), error.message()));
            } catch (JsonParseException | IllegalArgumentException | IOException e) {
                Constants.LOG.error("Couldn't read {} ({})", id, entry.getKey(), e);
            }
        }
        return result;
    }
}
