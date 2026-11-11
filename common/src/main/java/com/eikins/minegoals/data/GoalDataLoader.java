package com.eikins.minegoals.data;

import com.eikins.minegoals.Constants;
import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
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
 * Both folders are read together so goals can be validated against categories.
 */
public class GoalDataLoader extends SimplePreparableReloadListener<GoalDefinitions> {

    public static final Identifier ID = Constants.id("goals");

    private static final FileToIdConverter CATEGORIES = FileToIdConverter.json("minegoals/category");
    private static final FileToIdConverter GOALS = FileToIdConverter.json("minegoals/goal");

    @Override
    protected GoalDefinitions prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, Category> categories = load(manager, CATEGORIES, Category.CODEC);
        Map<Identifier, Goal> goals = load(manager, GOALS, Goal.CODEC);
        return GoalDefinitions.validated(categories, goals);
    }

    @Override
    protected void apply(GoalDefinitions definitions, ResourceManager manager, ProfilerFiller profiler) {
        GoalDefinitions.setServer(definitions);
        Constants.LOG.info("Loaded {} goal categories and {} goals",
                definitions.categories().size(), definitions.goals().size());
    }

    private static <T> Map<Identifier, T> load(ResourceManager manager, FileToIdConverter lister, Codec<T> codec) {
        Map<Identifier, T> result = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : lister.listMatchingResources(manager).entrySet()) {
            Identifier id = lister.fileToId(entry.getKey());
            try (Reader reader = entry.getValue().openAsReader()) {
                codec.parse(JsonOps.INSTANCE, StrictJsonParser.parse(reader))
                        .ifSuccess(value -> result.put(id, value))
                        .ifError(error -> Constants.LOG.error("Couldn't parse {} ({}): {}", id, entry.getKey(), error.message()));
            } catch (JsonParseException | IllegalArgumentException | IOException e) {
                Constants.LOG.error("Couldn't read {} ({})", id, entry.getKey(), e);
            }
        }
        return result;
    }
}
