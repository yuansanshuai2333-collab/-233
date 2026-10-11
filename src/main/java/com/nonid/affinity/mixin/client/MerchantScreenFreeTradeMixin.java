package com.nonid.affinity.mixin.client;

import com.nonid.affinity.client.FreeTradeStatusLayout;
import com.nonid.affinity.client.FreeTradeStatusText;
import com.nonid.affinity.client.FreeTradeWindowClient;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the close-friend courtesy under the trade window.
 *
 * <p><b>Why under the window and not inside it.</b> The free case has to say three separate things -- that the
 * trade is free, that BOTH payment materials are waived, and how many remain -- and the merchant panel has no free
 * space for them: the trade list occupies the whole left column, the price details the middle, and the player's
 * backpack the bottom. Text drawn over any of those covers items or the price it is trying to explain. The strip
 * below the panel is empty in vanilla, is not clickable, and is where the player is already looking.
 *
 * <p><b>Where exactly it goes is not decided here.</b> A fixed offset below the panel is the defect this class used
 * to have: at a large GUI scale, or on a short window, that offset is off the bottom of the screen and the player
 * sees no statement at all about the trade. Wrapping and placement come from {@link FreeTradeStatusLayout}, which is
 * game-free and therefore testable; this class only measures with the real font, fills the backdrop and draws the
 * lines it is given.
 *
 * <p><b>Why the wrapping is the font's job and not ours.</b> Every line is rendered to its final translated
 * {@link Text} first and then broken by {@link TextRenderer#wrapLines}, so the text that is folded is the text that
 * is really drawn. The lines that actually overflow -- "Both payment materials are waived - nothing is taken from
 * you", and the long Chinese lines -- are literal text in the language file with <em>no arguments at all</em>, so
 * splitting this mod's own arguments at spaces could never break them and such a sentence stayed one line wider
 * than the screen for good. Handing the translated text to the font also means the language file's own fixed text is
 * used as written rather than being re-implemented here.
 *
 * <p><b>Why the geometry is shadowed here and the font is not.</b> The four panel fields are declared by
 * {@code HandledScreen}, the direct superclass of this target, and Mixin resolves a shadowed field up the
 * hierarchy. The font and the screen size are not shadowed because a mixin accessor -- which only ever sees the
 * members of the class it targets -- could not locate {@code Screen}'s members two levels up, and that failure
 * aborted this whole mixin during startup. Those two are read from {@link MinecraftClient} instead, which holds the
 * very font the screen draws with and reports the same scaled size the screen is laid out against.
 *
 * <p>The strip is drawn in the colour of the state it describes and only behind its own lines, so it neither dims
 * nor hides anything the window itself shows. It is skipped entirely for a window this client has no state for,
 * which is what keeps it from appearing over a wandering trader or over another mod's merchant screen.
 *
 * <p>Nothing here decides anything: the status comes from the store, which only holds answers the server sent for
 * this window, this opening and this recipe.
 */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenFreeTradeMixin {

    /**
     * The backdrop: black at three-quarter opacity.
     *
     * <p>Semi-transparent on purpose. An opaque strip would be a second panel over a window that already fills the
     * screen, and the text has a shadow, so it stays readable over whatever is behind it.
     */
    private static final int BACKDROP_COLOR = 0xC0000000;

    @Inject(method = "renderMain", at = @At("TAIL"))
    private void noncreatureaffinity$drawFreeTradeInfo(DrawContext context, int mouseX, int mouseY,
                                                        float delta, CallbackInfo ci) {
        if (!FreeTradeWindowClient.hasWindow()) {
            return;
        }
        MerchantScreen screen = (MerchantScreen) (Object) this;
        MerchantScreenHandler handler = screen.getScreenHandler();
        FreeTradeStatusText.Panel panel = FreeTradeStatusText.panelFor(FreeTradeWindowClient.statusFor(handler));
        if (panel == null || panel.lines().isEmpty()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        if (textRenderer == null) {
            return;
        }
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        // The panel's geometry comes from the accessor on the class that declares it. See HandledScreenAccess for
        // why it is not read from the trade screen itself.
        HandledScreenAccess geometry = (HandledScreenAccess) this;
        int panelX = geometry.noncreatureaffinity$panelX();
        int panelY = geometry.noncreatureaffinity$panelY();
        int panelWidth = geometry.noncreatureaffinity$panelWidth();
        int panelHeight = geometry.noncreatureaffinity$panelHeight();

        // The panel's own width is the number the layout would place a line inside, so it is also the number the
        // font must wrap to. Wrapping to anything else would let a line here be judged to fit and then be drawn off
        // the side of the strip.
        int wrapWidth = noncreatureaffinity$wrapWidth(
                FreeTradeStatusLayout.availableWidth(screenWidth, panelWidth));
        List<OrderedText> orderedTexts = new ArrayList<>();
        for (FreeTradeStatusText.Line line : panel.lines()) {
            // Each line becomes its final translated text first, so the font's line breaking runs over the words
            // the player will really read -- including text that lives in the language file and has no arguments at
            // all, which no amount of splitting on this side could ever break.
            Text text = Text.translatable(line.key(), line.args());
            for (OrderedText wrapped : textRenderer.wrapLines(text, wrapWidth)) {
                // A single logical line may arrive as several: they are flattened in order, because the layout
                // counts lines and the draw loop walks them by index.
                orderedTexts.add(wrapped);
            }
        }
        if (orderedTexts.isEmpty()) {
            // Every line translated to nothing: an empty backdrop would be a floating black rectangle over the
            // world, so nothing is drawn at all.
            return;
        }

        TextRenderer font = textRenderer;
        FreeTradeStatusLayout.WrappedLines wrapped = new FreeTradeStatusLayout.WrappedLines() {
            @Override
            public int size() {
                return orderedTexts.size();
            }

            @Override
            public int widthOf(int index) {
                // Measured with the same font, and in the same order, as the lines that will be drawn: measuring one
                // way and drawing another is how a strip ends up wider than the box behind it.
                return font.getWidth(orderedTexts.get(index));
            }
        };
        List<FreeTradeStatusLayout.MeasuredLine> measured = FreeTradeStatusLayout.measure(wrapped);
        FreeTradeStatusLayout.Plan plan = FreeTradeStatusLayout.plan(measured, screenWidth, screenHeight,
                panelX, panelY, panelWidth, panelHeight);
        if (plan.lines().isEmpty()) {
            return;
        }

        context.fill(plan.x(), plan.y(), plan.x() + plan.width(), plan.y() + plan.height(), BACKDROP_COLOR);
        int textY = plan.y() + FreeTradeStatusLayout.PADDING;
        for (int index = 0; index < measured.size(); index++) {
            // Each line is centred by hand rather than left-aligned: the lines differ in length, and centring is
            // what makes the block read as one statement instead of three unrelated notes. There is no centred
            // overload for OrderedText, so the x is computed here from the width the font just reported.
            int lineX = plan.x() + FreeTradeStatusLayout.PADDING
                    + Math.max(0, (plan.width() - 2 * FreeTradeStatusLayout.PADDING - measured.get(index).width()) / 2);
            context.drawText(textRenderer, orderedTexts.get(index), lineX, textY, panel.color(), true);
            textY += FreeTradeStatusLayout.LINE_HEIGHT;
        }
    }

    /**
     * A width the font renderer will accept.
     *
     * <p>Line breaking takes a width to break to, and a zero or negative one is not a width at all; handing it over
     * would be asking the font to fold a sentence into nothing. On a degenerate screen -- a window minimised to no
     * pixels -- the scaled screen width is the best remaining answer, and one pixel is the last resort so that the
     * call still has a positive width to work with. The text stays complete and over-wide in that case, and the
     * layout reports it as over-wide so the caller clips it, rather than the sentence being silently dropped.
     */
    private int noncreatureaffinity$wrapWidth(int available) {
        if (available >= 1) {
            return available;
        }
        return Math.max(1, MinecraftClient.getInstance().getWindow().getScaledWidth());
    }
}
