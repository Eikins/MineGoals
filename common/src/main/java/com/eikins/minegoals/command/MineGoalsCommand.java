package com.eikins.minegoals.command;

import com.eikins.minegoals.data.Category;
import com.eikins.minegoals.data.Goal;
import com.eikins.minegoals.data.GoalDefinitions;
import com.eikins.minegoals.progress.GoalProgressData;
import com.eikins.minegoals.progress.GoalTracker;
import com.eikins.minegoals.progress.PlayerGoalProgress;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.stream.Stream;

/** {@code /minegoals list|reset|complete} for operators. */
public final class MineGoalsCommand {

    private static final SuggestionProvider<CommandSourceStack> GOALS = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(GoalDefinitions.server().goals().keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> GOALS_AND_CATEGORIES = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(Stream.concat(
                    GoalDefinitions.server().categories().keySet().stream(),
                    GoalDefinitions.server().goals().keySet().stream()).toList(), builder);

    private MineGoalsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("minegoals")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> list(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reset(ctx, null))
                                .then(Commands.argument("target", IdentifierArgument.id())
                                        .suggests(GOALS_AND_CATEGORIES)
                                        .executes(ctx -> reset(ctx, IdentifierArgument.getId(ctx, "target"))))))
                .then(Commands.literal("complete")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("goal", IdentifierArgument.id())
                                        .suggests(GOALS)
                                        .executes(MineGoalsCommand::complete)))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        GoalDefinitions defs = GoalDefinitions.server();
        PlayerGoalProgress progress = GoalProgressData.get(ctx.getSource().getServer()).get(player.getUUID());
        CommandSourceStack source = ctx.getSource();

        source.sendSuccess(() -> Component.translatable("minegoals.command.list.header",
                player.getDisplayName(), progress.level(defs), progress.totalPoints(defs)).withStyle(ChatFormatting.GOLD), false);
        for (var catEntry : defs.categories().entrySet()) {
            Identifier categoryId = catEntry.getKey();
            Category category = catEntry.getValue();
            source.sendSuccess(() -> Component.empty()
                    .append(category.title().copy().withStyle(ChatFormatting.YELLOW))
                    .append(Component.translatable("minegoals.command.list.category",
                            progress.tier(defs, categoryId), category.tiers().size(),
                            progress.categoryPoints(defs, categoryId))), false);
            for (Identifier goalId : defs.goalsIn(categoryId)) {
                Goal goal = defs.goals().get(goalId);
                String state = progress.isCompleted(goalId) ? "✔" : progress.isUnlocked(defs, goalId) ? "…" : "🔒";
                ChatFormatting color = progress.isCompleted(goalId) ? ChatFormatting.GREEN
                        : progress.isUnlocked(defs, goalId) ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY;
                source.sendSuccess(() -> Component.literal("  " + state + " ").withStyle(color)
                        .append(goal.title())
                        .append(Component.literal(" " + progress.count(goalId) + "/" + goal.count()
                                + " [" + goalId + "]").withStyle(ChatFormatting.GRAY)), false);
            }
        }
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, Identifier target) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        GoalDefinitions defs = GoalDefinitions.server();
        GoalProgressData data = GoalProgressData.get(ctx.getSource().getServer());
        PlayerGoalProgress progress = data.get(player.getUUID());

        if (target == null) {
            progress.resetAll();
        } else if (defs.categories().containsKey(target)) {
            progress.resetCategory(defs, target);
        } else if (defs.goals().containsKey(target)) {
            progress.resetGoal(target);
        } else {
            ctx.getSource().sendFailure(Component.translatable("minegoals.command.unknown", target.toString()));
            return 0;
        }
        data.setDirty();
        GoalTracker.refresh(player);
        String what = target == null ? "*" : target.toString();
        ctx.getSource().sendSuccess(() -> Component.translatable("minegoals.command.reset", what, player.getDisplayName()), true);
        return 1;
    }

    private static int complete(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Identifier goal = IdentifierArgument.getId(ctx, "goal");
        if (!GoalDefinitions.server().goals().containsKey(goal)) {
            ctx.getSource().sendFailure(Component.translatable("minegoals.command.unknown", goal.toString()));
            return 0;
        }
        if (!GoalTracker.forceComplete(player, goal)) {
            ctx.getSource().sendFailure(Component.translatable("minegoals.command.already_completed", goal.toString()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("minegoals.command.complete", goal.toString(), player.getDisplayName()), true);
        return 1;
    }
}
