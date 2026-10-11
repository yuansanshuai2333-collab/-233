package com.nonid.affinity.client;

import com.nonid.affinity.net.AnimationChoicePayload;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Lets the player pick which installed animation to play with the aimed creature.
 *
 * <p>Entries come from the server, which resolves what actually matches. Rows whose conditions are
 * unmet are shown but disabled, so it is obvious that the animation exists yet cannot start here.
 */
@Environment(EnvType.CLIENT)
public final class AnimationChoiceScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int WIDTH = 320;

    private final Screen parent;
    private final String mobName;
    private final List<String[]> rows;
    private int scrollOffset;

    public AnimationChoiceScreen(Screen parent, String mobName, List<String[]> rows) {
        super(Text.translatable("gui.animation.title", mobName));
        this.parent = parent;
        this.mobName = mobName;
        this.rows = rows;
    }

    private int visibleRows() {
        return Math.max(1, (this.height - 80) / ROW_HEIGHT);
    }

    private int maxScroll() {
        return Math.max(0, this.rows.size() - visibleRows());
    }

    @Override
    protected void init() {
        int x = (this.width - WIDTH) / 2;
        int end = Math.min(this.rows.size(), this.scrollOffset + visibleRows());
        for (int index = this.scrollOffset; index < end; index++) {
            String[] row = this.rows.get(index);
            String id = row[0];
            String display = row.length > 1 && !row[1].isBlank() ? row[1] : id;
            boolean eligible = row.length <= 2 || Boolean.parseBoolean(row[2]);
            ButtonWidget button = ButtonWidget.builder(
                    Text.translatable(eligible ? "gui.animation.entry" : "gui.animation.entry.unavailable",
                            display), b -> {
                        ClientPlayNetworking.send(new AnimationChoicePayload(id));
                        close();
                    }).dimensions(x, 34 + (index - this.scrollOffset) * ROW_HEIGHT, WIDTH, 20).build();
            button.active = eligible;
            addDrawableChild(button);
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), b -> close())
                .dimensions(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0.0) {
            int next = Math.max(0, Math.min(maxScroll(),
                    this.scrollOffset + (verticalAmount > 0.0 ? -1 : 1)));
            if (next != this.scrollOffset) {
                this.scrollOffset = next;
                clearAndInit();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 16, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("gui.animation.count", this.rows.size()).formatted(Formatting.GRAY),
                this.width / 2, 26, 0xFFAAAAAA);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
