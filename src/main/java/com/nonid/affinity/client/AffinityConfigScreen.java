package com.nonid.affinity.client;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.config.AffinityConfig;
import com.nonid.affinity.config.RequestTimingInput;
import net.minecraft.client.gui.widget.TextFieldWidget;
import java.lang.reflect.Field;
import java.util.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

/** Settings presentation only. Edits stay in a copy until the existing save path succeeds. */
@Environment(EnvType.CLIENT)
public final class AffinityConfigScreen extends Screen {
    /** Options shown, in display order. Anything not listed is hidden. */
    private static final List<String> VISIBLE = Arrays.asList(
            "familiarThreshold",
            "controlThreshold",
            "friendThreshold",
            "maxAffinity",
            "initialAffinity",
            "affinityPerMating",
            "bondGrowthPercent",
            "affinityBonusPlayerInitiated",
            "autoTameOnFriend",
            "affinityLossOnReject",
            "affinityLossOnEscape",
            "nonStruggleScreen",
            "affinityLossOnTimeout",
            "forceMatingOnReject",
            "forcedMatingEnergyRatio",
            "requestTimeoutTicks",
            "requestCooldownTicks",
            "notifyOnAffinityChange",
            "friendlyMobsDefendPlayer",
            "friendlyDefendRadius",
            "companionFollowStartDistance",
            "companionFollowStopDistance",
            "companionFollowSpeed",
            "flyingCompanionRecall",
            "preventFriendDamage",
            "companionWaitRadius",
            "companionAttackRadius",
            "companionAttackLeashRadius",
            "companionEventMaxAgeTicks",
            "companionEngageStallTicks",
            "companionStallSuppressTicks",
            "companionReleaseSuppressTicks",
            "companionReturnTicks",
            "hudEnabled",
            "reunionReviveSound",
            "villagerDiscountMaxPercent",
            "offspringAffinityPercentOfParent",
            "offspringBefriended");

    private static final Map<String, int[]> RANGES = new HashMap<>();

    /**
     * Ranges for tick-count options, which are {@code long} in the config.
     *
     * <p>Kept separate from {@link #RANGES} because an int slider cannot express a window longer than
     * about 24 in-game hours without overflowing its own message formatting.
     */
    private static final Map<String, long[]> LONG_RANGES = new HashMap<>();

    static {
        RANGES.put("familiarThreshold", new int[]{1, 500});
        RANGES.put("controlThreshold", new int[]{1, 500});
        RANGES.put("friendThreshold", new int[]{1, 500});
        RANGES.put("maxAffinity", new int[]{10, 500});
        RANGES.put("initialAffinity", new int[]{0, 200});
        RANGES.put("affinityPerMating", new int[]{0, 100});
        RANGES.put("bondGrowthPercent", new int[]{25, 400});
        RANGES.put("affinityBonusPlayerInitiated", new int[]{0, 100});
        RANGES.put("affinityLossOnReject", new int[]{0, 100});
        RANGES.put("affinityLossOnEscape", new int[]{0, 100});
        RANGES.put("affinityLossOnTimeout", new int[]{0, 100});
        RANGES.put("requestEnergyThreshold", new int[]{0, 200});
        RANGES.put("offspringAffinityPercentOfParent", new int[]{0, 100});
        // A share of the offer's base price, so the ceiling is the whole price (never below the one-item floor
        // the discount calculation enforces).
        RANGES.put("villagerDiscountMaxPercent", new int[]{0, 100});
        RANGES.put("rejectionArousalCooldownTicks", new int[]{0, 12000});

        // Mirrors AffinityConfig.sanitize() for each field: a slider that can leave the legal range
        // would write a value the config then silently corrects on the next load.
        LONG_RANGES.put("companionEventMaxAgeTicks", new long[]{20L, 1200L});
        LONG_RANGES.put("companionEngageStallTicks", new long[]{20L, 2400L});
        LONG_RANGES.put("companionStallSuppressTicks", new long[]{20L, 2400L});
        LONG_RANGES.put("companionReleaseSuppressTicks", new long[]{20L, 2400L});
        LONG_RANGES.put("companionReturnTicks", new long[]{0L, 1200L});
    }

    private static final Map<String, double[]> DOUBLE_RANGES = new HashMap<>();

    static {
        DOUBLE_RANGES.put("requestChance", new double[]{0.0, 1.0});
        DOUBLE_RANGES.put("requestRange", new double[]{1.0, 32.0});
        DOUBLE_RANGES.put("friendlyDefendRadius", new double[]{1.0, 48.0});
        DOUBLE_RANGES.put("forcedMatingEnergyRatio", new double[]{0.0, 1.0});
        DOUBLE_RANGES.put("companionFollowStartDistance", new double[]{1.0, 64.0});
        DOUBLE_RANGES.put("companionFollowStopDistance", new double[]{0.5, 32.0});
        DOUBLE_RANGES.put("companionFollowSpeed", new double[]{0.1, 3.0});
        DOUBLE_RANGES.put("companionWaitRadius", new double[]{1.0, 32.0});
        DOUBLE_RANGES.put("companionAttackRadius", new double[]{4.0, 64.0});
        DOUBLE_RANGES.put("companionAttackLeashRadius", new double[]{4.0, 128.0});
    }

    private final Screen parent;
    private final AffinityConfig config;
    private final AffinityConfig defaults = new AffinityConfig();
    private final List<Row> rows = new ArrayList<>();
    private final Map<Integer, List<ClickableWidget>> widgetsByRow = new LinkedHashMap<>();
    private final Map<Integer, Integer> rowTops = new HashMap<>();
    private final Map<Integer, Integer> rowHeights = new HashMap<>();
    private final Map<String, Integer> scrollPositions = new HashMap<>();
    private static final List<String> CATEGORIES = List.of("general", "interaction", "companion", "display");
    private String category = "general";
    private int scrollOffset, contentHeight, listTop, listBottom, left, panelWidth, top, panelHeight;
    private int labelX, labelWidth, controlX, controlWidth, resetX, footerY;
    private ScrollBar scrollBar;
    private boolean saveFailed;
    private final Set<String> invalidTiming = new HashSet<>();

    public AffinityConfigScreen(Screen parent) {
        super(Text.translatable("screen.noncreatureaffinity.config.title"));
        this.parent = parent;
        this.config = NonCreatureAffinity.getConfig().copy();
        collectRows();
    }

    private void collectRows() {
        List<Field> fields = new ArrayList<>();
        for (Field field : AffinityConfig.class.getFields()) {
            if (!VISIBLE.contains(field.getName())) {
                continue;
            }
            Class<?> type = field.getType();
            if (type == int.class || type == long.class || type == double.class
                    || type == boolean.class) {
                fields.add(field);
            }
        }
        fields.sort(Comparator.comparingInt(field -> VISIBLE.indexOf(field.getName())));
        for (Field field : fields) {
            String name = field.getName();
            this.rows.add(new Row(field, name,
                    Text.translatable("config.noncreatureaffinity.option." + name),
                    Text.translatable("config.noncreatureaffinity.option." + name + ".desc")));
        }
    }



    private static String categoryOf(String name) {
        if (name.equals("reunionReviveSound") || name.equals("hudEnabled") || name.equals("notifyOnAffinityChange")) return "display";
        if (name.startsWith("companion") || name.startsWith("friendly")
                || name.equals("flyingCompanionRecall") || name.equals("preventFriendDamage")) return "companion";
        if (name.startsWith("request") || name.startsWith("affinityLoss") || name.startsWith("force")
                || name.startsWith("rejection") || name.equals("nonStruggleScreen")
                || name.equals("affinityPerMating") || name.equals("affinityBonusPlayerInitiated")
                || name.equals("bondGrowthPercent")) return "interaction";
        return "general";
    }

    private Text ui(String key, Object... args) { return Text.translatable("settings.noncreatureaffinity." + key, args); }
    private boolean editable(Row row) {
        return client == null || client.world == null || client.getServer() != null || row.name().equals("hudEnabled") || row.name().equals("reunionReviveSound");
    }
    private Object read(Row row, AffinityConfig from) {
        try { return row.field().get(from); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    private String valueLabel(Row row, AffinityConfig from) {
        Object value = read(row, from);
        if (RequestTimingInput.supports(row.name())) return RequestTimingInput.seconds((Integer)value);
        if (value instanceof Boolean b) return Text.translatable("config.noncreatureaffinity." + (b ? "on" : "off")).getString();
        if (value instanceof Double d) return String.format(Locale.ROOT, "%.2f", d);
        return value.toString();
    }
    private int maxScroll() { return Math.max(0, contentHeight - (listBottom - listTop)); }

    @Override protected void init() {
        widgetsByRow.clear(); rowTops.clear(); rowHeights.clear();
        panelWidth = Math.min(640, width < 400 ? width - 24 : width * 9 / 10);
        panelHeight = Math.min(480, height - 36);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        labelX = left + 20;
        int available = panelWidth - 64;
        controlWidth = Math.max(70, Math.min(180, available * 40 / 100));
        resetX = left + panelWidth - 48;
        controlX = resetX - 10 - controlWidth;
        labelWidth = Math.max(50, controlX - labelX - 12);
        int tabColumns = Math.max(1, Math.min(4, (panelWidth - 48) / 90));
        int tabWidth = (panelWidth - 48) / tabColumns;
        int tabRows = (CATEGORIES.size() + tabColumns - 1) / tabColumns;
        for (int i = 0; i < CATEGORIES.size(); i++) {
            String name = CATEGORIES.get(i);
            addDrawableChild(new SkinButton(left + 18 + (i % tabColumns) * tabWidth, top + 36 + (i / tabColumns) * 25,
                    tabWidth - 5, 24, ui(name), b -> selectCategory(name), "tab", name));
        }
        listTop = top + 42 + tabRows * 25;
        footerY = top + panelHeight - 43;
        listBottom = Math.min(footerY - 22, top + panelHeight - Math.min(83, panelHeight / 4) - 8);
        contentHeight = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (!categoryOf(row.name()).equals(category)) continue;
            int rowHeight = Math.min(listBottom - listTop, Math.max(54,
                    20 + labelLines(row).size() * 10 + descriptionLines(row).size() * 10));
            if (row.name().equals("reunionReviveSound")) rowHeight = Math.min(listBottom - listTop, Math.max(74, rowHeight));
            rowTops.put(i, contentHeight); rowHeights.put(i, rowHeight); contentHeight += rowHeight;
            ClickableWidget control = createControl(row, controlX, 0, controlWidth);
            SkinButton reset = new SkinButton(resetX, 0, 20, 20, ui("reset"), b -> resetRow(row), "reset", row.name());
            widgetsByRow.put(i, List.of(control, reset));
            addDrawableChild(control); addDrawableChild(reset);
        }
        scrollOffset = Math.min(scrollPositions.getOrDefault(category, scrollOffset), maxScroll());
        scrollBar = addDrawableChild(new ScrollBar(left + panelWidth - 24, listTop, listBottom - listTop));
        int buttonWidth = Math.max(65, (panelWidth - 105) / 2);
        addDrawableChild(new SkinButton(labelX, footerY, buttonWidth, 22, Text.translatable("gui.cancel"),
                b -> { if (client != null) client.setScreen(parent); }, "button", ""));
        addDrawableChild(new SkinButton(labelX + buttonWidth + 10, footerY, buttonWidth, 22, Text.translatable("gui.done"),
                b -> close(), "button", ""));
        layoutWidgets();
    }

    private void selectCategory(String next) {
        if (category.equals(next)) return;
        scrollPositions.put(category, scrollOffset); category = next;
        scrollOffset = scrollPositions.getOrDefault(next, 0); clearAndInit();
    }
    private void layoutWidgets() {
        for (var entry : widgetsByRow.entrySet()) {
            int i = entry.getKey(), y = listTop + rowTops.get(i) - scrollOffset;
            int h = rowHeights.get(i);
            for (ClickableWidget widget : entry.getValue()) {
                widget.setY(y + Math.max(6, (h - widget.getHeight()) / 2));
                widget.visible = widget.getY() >= listTop && widget.getY() + widget.getHeight() <= listBottom;
                widget.active = editable(rows.get(i));
                if (widget instanceof TextFieldWidget field) field.setEditable(widget.active);
            }
            entry.getValue().get(1).active &= !Objects.equals(read(rows.get(i), config), read(rows.get(i), defaults));
        }
        if (scrollBar != null) { scrollBar.visible = maxScroll() > 0; scrollBar.active = scrollBar.visible; }
    }
    private void scrollTo(int value) {
        scrollOffset = Math.clamp(value, 0, maxScroll()); scrollPositions.put(category, scrollOffset); layoutWidgets();
        // Hidden controls must not keep keyboard input after wheel scrolling.
        if (getFocused() instanceof ClickableWidget w && !w.visible) setFocused(null);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0) { scrollTo(scrollOffset - (int)Math.round(vertical * 30)); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_PAGE_DOWN || input.key() == GLFW.GLFW_KEY_PAGE_UP) {
            scrollTo(scrollOffset + (input.key() == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * (listBottom - listTop)); return true;
        }
        if (input.key() == GLFW.GLFW_KEY_HOME || input.key() == GLFW.GLFW_KEY_END) {
            scrollTo(input.key() == GLFW.GLFW_KEY_HOME ? 0 : maxScroll()); return true;
        }
        return super.keyPressed(input);
    }
    private void resetRow(Row row) {
        invalidTiming.remove(row.name());
        try { row.field().set(config, read(row, defaults)); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        scrollPositions.put(category, scrollOffset); clearAndInit();
        var replacement = widgetsByRow.get(rows.indexOf(row));
        if (replacement != null) setFocused(replacement.get(0));
    }
    private ClickableWidget createControl(Row row, int x, int y, int width) {
        if (RequestTimingInput.supports(row.name())) {
            TextFieldWidget input = new TextFieldWidget(textRenderer, x, y, width, 20, row.label());
            input.setMaxLength(16);
            input.setText(valueLabel(row, config));
            invalidTiming.remove(row.name());
            input.setChangedListener(value -> {
                var ticks = RequestTimingInput.ticks(row.name(), value);
                if (ticks.isPresent()) {
                    try { row.field().setInt(config, ticks.getAsInt()); }
                    catch (IllegalAccessException e) { throw new IllegalStateException(e); }
                    invalidTiming.remove(row.name());
                } else invalidTiming.add(row.name());
                input.setEditableColor(ticks.isPresent() ? 0xFFF6DEEA : 0xFFFF7777);
                layoutWidgets();
            });
            return input;
        }
        if (row.field().getType() == boolean.class) {
            boolean sound = row.name().equals("reunionReviveSound");
            int buttonWidth = sound ? Math.min(100, width) : 50;
            int buttonHeight = sound ? Math.min(60, rowHeights.get(rows.indexOf(row)) - 12) : 20;
            return new SkinButton(x + width - buttonWidth, y, buttonWidth, buttonHeight, Text.literal(valueLabel(row, config)), b -> {
                try { row.field().setBoolean(config, !row.field().getBoolean(config)); }
                catch (IllegalAccessException e) { throw new IllegalStateException(e); }
                b.setMessage(Text.literal(valueLabel(row, config))); layoutWidgets();
            }, "toggle", row.name());
        }
        if (row.field().getType() == int.class) return new IntSlider(x, y, width, row);
        if (row.field().getType() == long.class) return new LongSlider(x, y, width, row);
        return new DoubleSlider(x, y, width, row);
    }

    @Override public void renderBackground(DrawContext c, int x, int y, float delta) {
        // Draw the tint once in render(), before the frame and controls. Do not blur the world.
    }
    @Override public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        // Keep the running world visible (60% transparency); menus without a world retain the solid theme.
        c.fill(0, 0, width, height, client != null && client.world != null ? 0x661B1420 : 0xFF1B1420);
        SettingsSkin.frame(c, left, top, panelWidth, panelHeight);
        c.getMatrices().pushMatrix();
        c.getMatrices().translate(labelX, top + 14);
        float titleScale = Math.min(1.5f, (panelWidth - 70f) / Math.max(1, textRenderer.getWidth(title)));
        c.getMatrices().scale(titleScale, titleScale);
        c.drawText(textRenderer, title, 0, 0, 0xFFF6DEEA, false);
        c.getMatrices().popMatrix();
        super.render(c, mouseX, mouseY, delta);
        c.enableScissor(labelX - 2, listTop, resetX + 22, listBottom);
        for (var entry : rowTops.entrySet()) {
            int i = entry.getKey(), y = listTop + entry.getValue() - scrollOffset;
            if (y >= listBottom || y + rowHeights.get(i) <= listTop) continue;
            Row row = rows.get(i);
            int textY = y + 7;
            for (OrderedText line : labelLines(row)) {
                c.drawText(textRenderer, line, labelX, textY, 0xFFF6DEEA, false); textY += 10;
            }
            textY += 5;
            for (OrderedText line : descriptionLines(row)) {
                c.drawText(textRenderer, line, labelX, textY, 0xFFBFA1B6, false); textY += 10;
            }
            c.fill(labelX, y + rowHeights.get(i) - 1, resetX + 20, y + rowHeights.get(i), 0xFF493046);
        }
        c.disableScissor();
        Text hint = !invalidTiming.isEmpty() ? Text.translatable("config.noncreatureaffinity.invalid_request_seconds")
                : saveFailed ? Text.translatable("config.noncreatureaffinity.save_failed")
                : client != null && client.world != null && client.getServer() == null
                    ? Text.translatable("config.noncreatureaffinity.server_managed") : ui("hint");
        c.drawText(textRenderer, textRenderer.trimToWidth(hint.getString(), Math.max(60, panelWidth - 110)), labelX, footerY - 12,
                saveFailed || !invalidTiming.isEmpty() ? 0xFFFF7777 : 0xFFBFA1B6, false);
        drawTooltip(c, mouseX, mouseY);
    }
    private List<OrderedText> labelLines(Row row) { return limitedLines(row.label(), 2); }
    private List<OrderedText> descriptionLines(Row row) {
        return limitedLines(row.description(), Math.max(1, Math.min(3,
                (listBottom - listTop - 16) / 10 - labelLines(row).size())));
    }
    private List<OrderedText> limitedLines(Text text, int maxLines) {
        List<OrderedText> full = textRenderer.wrapLines(text, labelWidth);
        if (full.size() <= maxLines) return full;
        List<OrderedText> result = new ArrayList<>(full.subList(0, maxLines));
        OrderedText last = result.get(result.size() - 1);
        StringBuilder plain = new StringBuilder();
        last.accept((index, style, point) -> { plain.appendCodePoint(point); return true; });
        result.set(result.size() - 1, Text.literal(textRenderer.trimToWidth(plain.toString(), labelWidth - textRenderer.getWidth("…")) + "…").asOrderedText());
        return result;
    }
    private void drawTooltip(DrawContext c, int mouseX, int mouseY) {
        for (var entry : widgetsByRow.entrySet()) {
            Row row = rows.get(entry.getKey());
            for (int k = 0; k < entry.getValue().size(); k++) {
                ClickableWidget w = entry.getValue().get(k);
                // Clicking retains keyboard focus, but must never pin a mouse tooltip after leaving.
                if (!w.visible || !w.isMouseOver(mouseX, mouseY)) continue;
                List<OrderedText> lines = new ArrayList<>();
                int textWidth = Math.min(250, width - 40);
                lines.addAll(textRenderer.wrapLines(k == 1 ? ui("reset") : row.label(), textWidth));
                lines.addAll(textRenderer.wrapLines(row.description(), textWidth));
                lines.addAll(textRenderer.wrapLines(ui("current", valueLabel(row, config)), textWidth));
                lines.addAll(textRenderer.wrapLines(ui("default", valueLabel(row, defaults)), textWidth));
                if (!editable(row)) lines.addAll(textRenderer.wrapLines(ui("locked"), textWidth));
                int tw = lines.stream().mapToInt(textRenderer::getWidth).max().orElse(100) + 20;
                int th = lines.size() * 10 + 18;
                int tx = Math.clamp(w.getX() - tw + w.getWidth(), 4, Math.max(4, width - tw - 4));
                int ty = w.getY() + w.getHeight() + 5;
                if (ty + th > height - 4) ty = Math.max(4, w.getY() - th - 5);
                SettingsSkin.nine(c, "widgets/tooltip/panel", tx, ty, tw, th, 160, 52, 12, 6);
                int yy = ty + 9;
                for (OrderedText line : lines) { c.drawText(textRenderer, line, tx + 10, yy, 0xFFFFF7E5, false); yy += 10; }
                return;
            }
        }
    }

    private final class SkinButton extends ClickableWidget {
        private final String kind, option;
        private long pressedUntil; private final java.util.function.Consumer<SkinButton> action;
        SkinButton(int x, int y, int w, int h, Text text, java.util.function.Consumer<SkinButton> action, String kind, String option) {
            super(x, y, w, h, text); this.action = action; this.kind = kind; this.option = option;
        }
        @Override protected net.minecraft.text.MutableText getNarrationMessage() {
            if (kind.equals("toggle") || kind.equals("reset")) return Text.translatable("config.noncreatureaffinity.option." + option)
                    .append(": ").append(getMessage());
            return super.getNarrationMessage();
        }
        @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) { appendDefaultNarrations(builder); }
        @Override public boolean keyPressed(KeyInput key) { if (active && (key.key() == GLFW.GLFW_KEY_ENTER || key.key() == GLFW.GLFW_KEY_SPACE || key.key() == GLFW.GLFW_KEY_KP_ENTER)) { pressedUntil = System.currentTimeMillis() + 120; action.accept(this); return true; } return false; }
        @Override public void onClick(Click click, boolean doubled) { pressedUntil = System.currentTimeMillis() + 120; action.accept(this); }
        @Override protected void renderWidget(DrawContext c, int mx, int my, float delta) {
            if (kind.equals("button")) {
                String state = !active ? "disabled" : System.currentTimeMillis() < pressedUntil ? "pressed" : isHovered() ? "hover" : isFocused() ? "focus" : "default";
                SettingsSkin.nine(c, "widgets/button/" + state, getX(), getY(), width, height, 500, 90, 38, 9);
            } else if (kind.equals("tab")) {
                SettingsSkin.nine(c, "widgets/tabs/" + (category.equals(option) ? "selected" : "unselected"), getX(), getY(), width, height, 84, 30, 10, 6);
            } else if (kind.equals("reset")) {
                SettingsSkin.image(c, "widgets/reset/default", getX(), getY(), width, height, 36, 36);
            } else {
                boolean on = rows.stream().filter(r -> r.name().equals(option)).findFirst().map(r -> (Boolean)read(r, config)).orElse(false);
                if (option.equals("reunionReviveSound")) {
                    // Use the supplied pictures unchanged, with their original aspect ratios.
                    int sourceWidth = on ? 1937 : 821, sourceHeight = on ? 1280 : 805;
                    int imageBoxWidth = width - 20;
                    double scale = Math.min((double) imageBoxWidth / sourceWidth, (double) height / sourceHeight);
                    int iw = Math.max(1, (int)Math.round(sourceWidth * scale));
                    int ih = Math.max(1, (int)Math.round(sourceHeight * scale));
                    SettingsSkin.image(c, "widgets/reunion_sound/" + (on ? "on" : "off"),
                            getX() + (imageBoxWidth - iw) / 2, getY() + (height - ih) / 2, iw, ih, sourceWidth, sourceHeight);
                    c.drawText(textRenderer, getMessage(), getX() + imageBoxWidth + 3, getY() + (height - 8) / 2, 0xFFFFF7E5, false);
                } else SettingsSkin.image(c, "widgets/toggle/" + (on ? "on" : "off"), getX(), getY(), width, height, 70, 28);
            }
            if (!kind.equals("button")) {
                if (!active) c.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, 0x661B1420);
                else if (System.currentTimeMillis() < pressedUntil || isHovered() || isFocused())
                    c.fill(getX() + 3, getY() + 3, getX() + width - 3, getY() + height - 3,
                            System.currentTimeMillis() < pressedUntil ? 0x44FFFFFF : 0x22FFFFFF);
            }
            if (kind.equals("button") || kind.equals("tab")) c.drawCenteredTextWithShadow(textRenderer, getMessage(), getX() + width / 2, getY() + (height - 8) / 2 - 1, active ? 0xFFFFF7E5 : 0xFFAA8B9E);
        }
    }

    private abstract class SkinSlider extends SliderWidget {
        private final Row skinRow;
        SkinSlider(int x, int y, int w, Row row) { super(x, y, w, 20, Text.empty(), 0); skinRow = row; }
        @Override public void renderWidget(DrawContext c, int mx, int my, float delta) {
            int start = getX() + 5, end = getX() + width - 5;
            SettingsSkin.horizontal(c, "widgets/slider/track", getX(), getY(), width, 20, 180, 30, 12, 8);
            SettingsSkin.image(c, "widgets/slider/fill", start, getY() + 8, (int)((end - start) * value), 3, 1, 5);
            SettingsSkin.horizontal(c, "widgets/slider/decorations", getX(), getY(), width, 20, 180, 30, 12, 8);
            SettingsSkin.image(c, "widgets/slider/thumb", start + (int)((end - start) * value) - 4, getY() + 1, 8, 16, 14, 24);
            c.drawCenteredTextWithShadow(textRenderer, getMessage(), getX() + width / 2, getY() - 9, active ? 0xFFFFF7E5 : 0xFFAA8B9E);
            if (!active) c.fill(getX(), getY(), getX() + width, getY() + height, 0x661B1420);
            else if (isFocused() || isHovered()) c.fill(getX(), getY() + 19, getX() + width, getY() + 20, 0xFFF8B4CE);
        }
        private void fromMouse(Click click) {
            value = Math.clamp((click.x() - getX() - 5) / (width - 10.0), 0.0, 1.0);
            applyValue(); updateMessage(); layoutWidgets();
        }
        @Override public void onClick(Click click, boolean doubled) { fromMouse(click); }
        @Override protected void onDrag(Click click, double dx, double dy) { fromMouse(click); }
        @Override public boolean keyPressed(KeyInput key) {
            if (key.key() == GLFW.GLFW_KEY_LEFT || key.key() == GLFW.GLFW_KEY_RIGHT) {
                double step;
                if (skinRow.field().getType() == int.class) { var r = RANGES.get(skinRow.name()); step = 1.0 / (r[1] - r[0]); }
                else if (skinRow.field().getType() == long.class) { var r = LONG_RANGES.get(skinRow.name()); step = 1.0 / (r[1] - r[0]); }
                else { var r = DOUBLE_RANGES.get(skinRow.name()); step = 0.01 / (r[1] - r[0]); }
                value = Math.clamp(value + (key.key() == GLFW.GLFW_KEY_RIGHT ? step : -step), 0.0, 1.0);
                applyValue(); updateMessage(); layoutWidgets(); return true;
            }
            return super.keyPressed(key);
        }
        @Override protected net.minecraft.text.MutableText getNarrationMessage() { return skinRow.label().copy().append(": ").append(getMessage()); }
    }
    private final class ScrollBar extends ClickableWidget {
        ScrollBar(int x, int y, int height) { super(x, y, 14, height, Text.translatable("gui.scroll")); }
        private int thumbHeight() { return Math.max(18, (height - 8) * (listBottom - listTop) / Math.max(1, contentHeight)); }
        private int travel() { return Math.max(1, height - 8 - thumbHeight()); }
        private int thumbY() { return getY() + 4 + (maxScroll() == 0 ? 0 : travel() * scrollOffset / maxScroll()); }
        private double grab;
        @Override protected void renderWidget(DrawContext c, int mx, int my, float delta) {
            SettingsSkin.scrollTrack(c, getX(), getY(), height);
            SettingsSkin.scrollThumb(c, getX() + 4, thumbY(), thumbHeight());
            if (isFocused() || isHovered()) c.fill(getX() + 6, thumbY() + 4, getX() + 8, thumbY() + thumbHeight() - 4, 0x33FFFFFF);
        }
        @Override public void onClick(Click click, boolean doubled) {
            grab = click.y() >= thumbY() && click.y() <= thumbY() + thumbHeight() ? click.y() - thumbY() : thumbHeight() / 2.0;
            onDrag(click, 0, 0);
        }
        @Override protected void onDrag(Click click, double dx, double dy) { scrollTo((int)Math.round((click.y() - getY() - 4 - grab) / travel() * maxScroll())); }
        @Override public boolean keyPressed(KeyInput key) {
            if (key.key() == GLFW.GLFW_KEY_UP || key.key() == GLFW.GLFW_KEY_DOWN) {
                scrollTo(scrollOffset + (key.key() == GLFW.GLFW_KEY_DOWN ? 20 : -20)); return true;
            } return false;
        }
        @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) { appendDefaultNarrations(builder); }
    }

    @Override
    public void close() {
        if (!invalidTiming.isEmpty()) return;
        this.config.sanitize();
        if (!this.config.trySave()) {
            this.saveFailed = true;
            return;
        }
        this.saveFailed = false;
        if (this.client != null) {
            if (this.client.getServer() != null) {
                this.client.getServer().execute(() -> NonCreatureAffinity.applyConfig(this.config));
            } else {
                NonCreatureAffinity.applyConfig(this.config);
            }
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private record Row(Field field, String name, Text label, Text description) {
    }



    /** Keep the handle on its track without rewriting a valid file-only value outside the UI range. */
    static double sliderPosition(double current, double min, double max) {
        return Math.max(0.0, Math.min(1.0, (current - min) / (max - min)));
    }

    /** Slider for int fields. */
    private final class IntSlider extends SkinSlider {
        private final Row row;
        private final int min;
        private final int max;

        IntSlider(int x, int y, int width, Row row) {
            super(x, y, width, row);
            this.row = row;
            int[] range = RANGES.getOrDefault(row.name(), new int[]{0, 200});
            this.min = range[0];
            this.max = Math.max(range[0] + 1, range[1]);
            int current = readInt(this.min);
            this.value = sliderPosition(current, this.min, this.max);
            updateMessage();
        }

        private int readInt(int fallback) {
            try {
                return this.row.field().getInt(AffinityConfigScreen.this.config);
            } catch (IllegalAccessException exception) {
                return fallback;
            }
        }

        private int currentValue() {
            return this.min + (int) Math.round(this.value * (this.max - this.min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(String.valueOf(readInt(currentValue()))).formatted(Formatting.YELLOW));
        }

        @Override
        protected void applyValue() {
            try {
                this.row.field().setInt(AffinityConfigScreen.this.config, currentValue());
            } catch (IllegalAccessException ignored) {
                // Unreachable.
            }
        }
    }

    /** Slider for long fields, used by the tick-count windows. */
    private final class LongSlider extends SkinSlider {
        private final Row row;
        private final long min;
        private final long max;

        LongSlider(int x, int y, int width, Row row) {
            super(x, y, width, row);
            this.row = row;
            long[] range = LONG_RANGES.getOrDefault(row.name(), new long[]{0L, 1200L});
            this.min = range[0];
            this.max = Math.max(range[0] + 1L, range[1]);
            long current = readLong(this.min);
            this.value = sliderPosition(current, this.min, this.max);
            updateMessage();
        }

        private long readLong(long fallback) {
            try {
                return this.row.field().getLong(AffinityConfigScreen.this.config);
            } catch (IllegalAccessException exception) {
                return fallback;
            }
        }

        private long currentValue() {
            return this.min + Math.round(this.value * (double) (this.max - this.min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(String.valueOf(readLong(currentValue()))).formatted(Formatting.GOLD));
        }

        @Override
        protected void applyValue() {
            try {
                this.row.field().setLong(AffinityConfigScreen.this.config, currentValue());
            } catch (IllegalAccessException ignored) {
                // Unreachable.
            }
        }
    }

    /** Slider for double fields. */
    private final class DoubleSlider extends SkinSlider {
        private final Row row;
        private final double min;
        private final double max;

        DoubleSlider(int x, int y, int width, Row row) {
            super(x, y, width, row);
            this.row = row;
            double[] range = DOUBLE_RANGES.getOrDefault(row.name(), new double[]{0.0, 64.0});
            this.min = range[0];
            this.max = Math.max(range[0] + 0.01, range[1]);
            double current = readDouble(this.min);
            this.value = sliderPosition(current, this.min, this.max);
            updateMessage();
        }

        private double readDouble(double fallback) {
            try {
                return this.row.field().getDouble(AffinityConfigScreen.this.config);
            } catch (IllegalAccessException exception) {
                return fallback;
            }
        }

        private double currentValue() {
            return this.min + this.value * (this.max - this.min);
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(String.format("%.2f", readDouble(currentValue()))).formatted(Formatting.AQUA));
        }

        @Override
        protected void applyValue() {
            try {
                this.row.field().setDouble(AffinityConfigScreen.this.config, currentValue());
            } catch (IllegalAccessException ignored) {
                // Unreachable.
            }
        }
    }
}

