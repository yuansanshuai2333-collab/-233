package com.nonid.affinity.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the free-trade strip is drawn and how tall and wide it turns out, decided without a game.
 *
 * <p><b>The defects this class exists to close.</b> The strip used to be drawn at a fixed offset under the merchant
 * panel. On a small window, or at a GUI scale that leaves less than a line of room below the panel, that offset put
 * the strip off the bottom of the screen: the player saw the trade list but nothing about whether the trade was
 * free, which is the one thing the strip exists to say.
 *
 * <p>The strip also used to be wrapped here, by splitting <em>String arguments</em> at spaces and newlines. That
 * could never work for the messages that actually overflow. The long lines -- "Both payment materials are waived -
 * nothing is taken from you", and the long Chinese lines -- are <em>literal text in the language file with no
 * arguments at all</em>, so there was nothing here to split and the sentence stayed one line wider than the screen
 * forever. Position clamping cannot repair that: a line wider than the screen cannot be moved to fit it.
 *
 * <p><b>What this class does instead.</b> The caller renders every logical line to its final translated
 * {@code Text}, asks the game's font renderer to break that text to {@link #availableWidth}, and hands the resulting
 * measured lines here. Wrapping therefore happens on the text that is really drawn, including text this class could
 * never have seen, and the box is sized from the number of lines that really came out. This class no longer knows
 * what a translation key is, and never rewrites a translation template.
 *
 * <p>All that is left here is placement, which is why it has no screen, no font and no Minecraft type any more: it
 * answers only "at which x and y, how wide and tall". That is what keeps these rules testable without a game.
 *
 * <p><b>What the caller still owns.</b> Wrapping the text with the font, filling the backdrop and drawing the lines.
 * A strip wider than the screen is reported as such and placed at {@code x = 0} instead of being cut here, because
 * only the caller knows how to clip; text is never silently dropped to make the arithmetic work.
 */
public final class FreeTradeStatusLayout {

    /**
     * One already-measured line of text: where it came from, and how wide it is in pixels.
     *
     * <p>The source index is the caller's draw-order position for the line, so the caller can pair each measurement
     * with the {@code OrderedText} it measured. Nothing here interprets the text itself.
     */
    public record MeasuredLine(int sourceIndex, int width) {
    }

    /** Where the strip goes, and how many lines it holds. All coordinates are GUI-space (scaled) pixels. */
    public record Plan(int x, int y, int width, int height, java.util.List<MeasuredLine> lines) {
    }

    /**
     * Never wider than this share of the screen.
     *
     * <p>The strip explains a panel the player is reading, so it must not grow into a full-width banner that covers
     * that panel or the world around it. Wrapping to a fraction of the screen keeps it recognisably a caption.
     */
    public static final double MAX_SCREEN_WIDTH_FRACTION = 0.9;

    /** The height of one drawn text line: the vanilla font's own line height. */
    public static final int LINE_HEIGHT = 11;

    /** Padding between the text and the edge of the backdrop, on all four sides. */
    public static final int PADDING = 3;

    /**
     * The vertical gap between the panel edge and the strip.
     *
     * <p>Without it the backdrop touches the panel and the strip reads as a row of the window rather than as a note
     * about it.
     */
    public static final int GAP = 4;

    /**
     * The border kept clear at the left and right screen edge.
     *
     * <p>A line that exactly fills the screen looks like a rendering error and loses its last glyph's shadow to the
     * edge of the window.
     */
    private static final int SCREEN_EDGE_MARGIN = 2;

    private FreeTradeStatusLayout() {
    }

    /**
     * The width one line may occupy, given the screen and the panel.
     *
     * <p>This is the number the caller must pass to the font renderer's line breaking, because it is the number the
     * placement below is derived from. If the caller wrapped to anything else -- the raw screen width, say -- a line
     * could be measured as fitting here and still be drawn off the side, which is precisely the failure this class
     * closes.
     *
     * <p>The formula is {@code min(screenWidth - 2, max(panelWidth, 0.9 * screenWidth))}: never wider than the
     * screen (so the strip cannot fall off either side), never narrower than the panel it explains (so a wide panel
     * does not force its own caption to be absurdly narrow, which would produce one word per line), and never wider
     * than the fraction above.
     *
     * <p>Never throws for a degenerate screen: {@code 0x0} and negative sizes give 0 or a negative number, and it is
     * the caller's job to notice that and fall back rather than hand a non-positive width to the font renderer.
     *
     * @param screenWidth the scaled screen width in pixels
     * @param panelWidth  the panel's width
     */
    public static int availableWidth(int screenWidth, int panelWidth) {
        int byScreen = screenWidth - SCREEN_EDGE_MARGIN;
        int byFraction = (int) (screenWidth * MAX_SCREEN_WIDTH_FRACTION);
        return Math.min(byScreen, Math.max(panelWidth, byFraction));
    }

    /**
     * Decide where the strip goes and how big its backdrop is.
     *
     * <p><b>The height follows the lines that will actually be drawn.</b> A logical line that the font renderer had
     * to break into three lines arrives here as three entries, so the backdrop can never be shorter than its own text
     * and let a line spill outside the box -- the defect that made a two-sentence message look like a message with a
     * sentence missing.
     *
     * <p><b>Placement.</b> Below the panel is preferred, because that space is empty in vanilla and is where the
     * player is already looking. When the strip would run off the bottom (or start above the screen), it is put above
     * the panel instead, where the villager's name sits -- only used when below would leave the screen. When it fits
     * on neither side -- a very short window -- it is clamped into the screen so the first and most important line
     * stays readable rather than disappearing entirely.
     *
     * <p>Never throws for degenerate input: a null or empty list, null entries inside it, a negative measured width
     * and a zero or negative screen all produce a plan, because a layout failure during rendering must not take the
     * whole screen down.
     *
     * @param lines        the already-measured lines, in draw order; the caller wrapped them with the font
     * @param screenWidth  the scaled screen width in pixels
     * @param screenHeight the scaled screen height in pixels
     * @param panelX       the panel's left edge
     * @param panelY       the panel's top edge
     * @param panelWidth   the panel's width
     * @param panelHeight  the panel's height
     */
    public static Plan plan(java.util.List<MeasuredLine> lines, int screenWidth, int screenHeight,
                            int panelX, int panelY, int panelWidth, int panelHeight) {
        // The formula is applied to the measured lines: the height follows what will actually be drawn, so the
        // backdrop can never be shorter than its own text and let a line spill outside the box.
        List<MeasuredLine> usable = usableLines(lines);
        int width = stripWidth(usable);
        int height = usable.size() * LINE_HEIGHT + 2 * PADDING;
        int y = stripY(panelY, panelHeight, height, screenHeight);
        int x = stripX(panelX, panelWidth, width, screenWidth);
        return new Plan(x, y, width, height, List.copyOf(usable));
    }

    /**
     * The lines this class may reason about: no null list, and no null entries inside it.
     *
     * <p>A null entry would otherwise be dereferenced below and turn a cosmetic problem into a crash while the trade
     * window is open.
     */
    private static List<MeasuredLine> usableLines(java.util.List<MeasuredLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<MeasuredLine> usable = new ArrayList<>(lines.size());
        for (MeasuredLine line : lines) {
            if (line != null) {
                usable.add(line);
            }
        }
        return usable;
    }

    /**
     * How wide the backdrop is: the widest line plus its padding on both sides.
     *
     * <p>Only as wide as the text needs. A backdrop that stretched to the whole screen would dim the panel under
     * discussion, and the strip must look like a caption about the window, not like a second window.
     *
     * <p>A negative measurement counts as zero, so it can never turn into a negative strip width, which would invert
     * the backdrop rectangle and draw a full-screen bar.
     */
    private static int stripWidth(List<MeasuredLine> lines) {
        int widest = 0;
        for (MeasuredLine line : lines) {
            widest = Math.max(widest, Math.max(0, line.width()));
        }
        return widest + 2 * PADDING;
    }

    /**
     * The strip's vertical position.
     *
     * <p>Below the panel first, above it second, and clamped into the screen third. The order matters to the player:
     * the space under the panel is empty in vanilla, while the space above it holds the villager's name and
     * profession, so above is only used when below would leave the screen. When neither side holds the whole strip,
     * the clamp keeps the top of it (and therefore the first, most important line) visible -- a strip pushed fully
     * off-screen is the defect this whole class closes.
     */
    private static int stripY(int panelY, int panelHeight, int height, int screenHeight) {
        int below = panelY + panelHeight + GAP;
        if (fitsVertically(below, height, screenHeight)) {
            return below;
        }
        int above = panelY - GAP - height;
        if (fitsVertically(above, height, screenHeight)) {
            return above;
        }
        // Math.max(0, ...) also covers a strip taller than the screen: then screenHeight - height is negative and
        // the strip is top-aligned instead of being pushed above the top edge.
        return Math.max(0, Math.min(below, screenHeight - height));
    }

    /** Whether a strip at this y lies entirely on the screen. */
    private static boolean fitsVertically(int y, int height, int screenHeight) {
        return y >= 0 && y + height <= screenHeight;
    }

    /**
     * The strip's horizontal position: centred on the panel, then kept on the screen.
     *
     * <p>Centring keeps the strip visually attached to the window it describes. The clamp stops a panel near a
     * screen edge from pushing the text off that edge -- the failure being a sentence whose beginning or end is
     * unreachable. A strip wider than the screen cannot be centred at all, so it is left-aligned and reported
     * over-wide; the caller clips, because only the caller can.
     */
    private static int stripX(int panelX, int panelWidth, int width, int screenWidth) {
        int centred = panelX + panelWidth / 2 - width / 2;
        if (width > screenWidth) {
            return 0;
        }
        return Math.max(0, Math.min(centred, screenWidth - width));
    }

    /** One already-wrapped line of text, as the caller's own type: the game's {@code OrderedText}. */
    public interface WrappedLines {

        /** How many lines came out of the font renderer's wrapping. */
        int size();

        /** How wide the line at this index is, in pixels, according to the same font. */
        int widthOf(int index);
    }

    /**
     * Measured lines for a set of wrapped text lines, in draw order.
     *
     * <p>The glue between the caller's wrapped text and the arithmetic above: the caller wraps its translated lines
     * with the real font, then hands the result here, and what comes back is exactly what {@link #plan} needs. Doing
     * it in two steps rather than one keeps this class free of Minecraft types and testable without a game.
     *
     * <p>A negative width is reported as zero, because the widths from a font are used as lengths and a negative
     * length would run the backdrop backwards.
     *
     * @param wrapped the lines produced by the font renderer, or null when there are none
     */
    public static java.util.List<MeasuredLine> measure(WrappedLines wrapped) {
        if (wrapped == null || wrapped.size() <= 0) {
            return List.of();
        }
        List<MeasuredLine> measured = new ArrayList<>(wrapped.size());
        for (int index = 0; index < wrapped.size(); index++) {
            measured.add(new MeasuredLine(index, Math.max(0, wrapped.widthOf(index))));
        }
        return List.copyOf(measured);
    }
}
