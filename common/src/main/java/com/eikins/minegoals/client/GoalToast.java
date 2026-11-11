package com.eikins.minegoals.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** MineGoals toast ("Category unlocked", "Goal completed"...), drawn on the vanilla advancement toast background. */
public class GoalToast implements Toast {

    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/advancement");
    private static final long DISPLAY_MS = 5000L;

    private final Component title;
    private final Component message;
    private final ItemStack icon;
    private Visibility visibility = Visibility.HIDE;

    public GoalToast(Component title, Component message, ItemStack icon) {
        this.title = title;
        this.message = message;
        this.icon = icon;
    }

    @Override
    public Visibility getWantedVisibility() {
        return this.visibility;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        double displayTime = DISPLAY_MS * manager.getNotificationDisplayTimeMultiplier();
        this.visibility = fullyVisibleForMs < displayTime ? Visibility.SHOW : Visibility.HIDE;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, this.width(), this.height());
        graphics.item(this.icon, 8, 8);
        GoalRender.clippedText(graphics, font, this.title, 30, 7, this.width() - 34, GoalRender.GOLD);
        GoalRender.clippedText(graphics, font, this.message, 30, 18, this.width() - 34, GoalRender.WHITE);
    }
}
