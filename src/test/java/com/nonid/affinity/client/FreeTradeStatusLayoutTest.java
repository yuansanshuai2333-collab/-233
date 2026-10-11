package com.nonid.affinity.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nonid.affinity.client.FreeTradeStatusLayout.MeasuredLine;
import com.nonid.affinity.client.FreeTradeStatusLayout.Plan;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where the free-trade strip lands and how big its backdrop turns out.
 *
 * <p>The rules pinned here are the ones the player can lose a sentence to: a strip drawn off the bottom of a short
 * window, a line running off the side of a small one, and -- the case the strip was actually failing -- a sentence
 * wider than the screen being silently treated as if it fitted.
 *
 * <p>Every case is checked in GUI pixels against measured lines built by hand, because this class no longer wraps
 * anything: the wrapping is the font renderer's job and is decided by the caller's own test, so a width here can be
 * counted directly. Every test carries a {@link DisplayName} naming the user-visible failure it prevents.
 */
class FreeTradeStatusLayoutTest {

    /** The lines worth drawing, from widths that play the part of the font renderer's measurements. */
    private static List<MeasuredLine> measured(int... widths) {
        List<MeasuredLine> lines = new java.util.ArrayList<>(widths.length);
        for (int index = 0; index < widths.length; index++) {
            lines.add(new MeasuredLine(index, widths[index]));
        }
        return lines;
    }

    /** The same, plus a null entry: what a caller can really hand over, and what the layout must ignore. */
    private static List<MeasuredLine> measuredWithNull(int width) {
        return Arrays.asList(new MeasuredLine(0, width), null);
    }

    private static Plan plan(List<MeasuredLine> lines, int screenWidth, int screenHeight,
                             int panelX, int panelY, int panelWidth, int panelHeight) {
        return FreeTradeStatusLayout.plan(lines, screenWidth, screenHeight, panelX, panelY, panelWidth,
                panelHeight);
    }

    /** The usual test window: a 320x240 screen with a merchant panel filling the top left of it. */
    private static Plan planOnNormalScreen(List<MeasuredLine> lines) {
        return plan(lines, 320, 240, 0, 0, 176, 166);
    }

    @Test
    @DisplayName("the wrap width follows the screen and the panel, so a line is folded to what is really available")
    void availableWidthFollowsTheFormula() {
        // max(176, 0.9 * 320 = 288) = 288, and min(320 - 2 = 318, 288) = 288.
        assertEquals(288, FreeTradeStatusLayout.availableWidth(320, 176),
                "the panel is narrower than the fraction, so the fraction decides");
        assertEquals(318, FreeTradeStatusLayout.availableWidth(320, 400),
                "a panel wider than the screen is capped at the screen less its edge margin, never by the panel");
        assertEquals(288, FreeTradeStatusLayout.availableWidth(320, 0),
                "a panel with no width at all must not narrow the strip down to nothing");
    }

    @Test
    @DisplayName("wrapping never leaves the screen's edge, so no line is drawn off either side of the window")
    void availableWidthNeverLeavesTheScreen() {
        for (int screen = 40; screen <= 2000; screen += 37) {
            for (int panel : new int[] {0, 10, 100, 176, 500, screen, screen + 500}) {
                int available = FreeTradeStatusLayout.availableWidth(screen, panel);
                int byFraction = (int) (screen * FreeTradeStatusLayout.MAX_SCREEN_WIDTH_FRACTION);

                assertTrue(available <= screen - 2,
                        "a wrap width of " + available + " on a " + screen + " px screen would let a line run off "
                                + "the side, which is how a sentence gets cut in the middle and read as another one");
                assertTrue(available <= Math.max(panel, byFraction),
                        "a wrap width wider than both the panel and the screen fraction would let the strip grow "
                                + "into a banner that covers the very panel it explains");
            }
        }
    }

    @Test
    @DisplayName("a panel narrower than the screen fraction does not force its own caption into one word per line")
    void availableWidthDoesNotFollowANarrowPanelDown() {
        // 0.9 * 320 = 288, so either 288, or the screen's edge margin when the panel is the wider of the two.
        assertEquals(288, FreeTradeStatusLayout.availableWidth(320, 10));
        assertEquals(288, FreeTradeStatusLayout.availableWidth(320, 100));
        assertEquals(318, FreeTradeStatusLayout.availableWidth(320, 318),
                "a panel as wide as the screen still leaves the edge margin, not the panel's own width");
    }

    @Test
    @DisplayName("a window with no pixels cannot make the wrap width throw or become a width the font would reject")
    void availableWidthSurvivesADegenerateScreen() {
        int noScreen = FreeTradeStatusLayout.availableWidth(0, 0);
        int negativeScreen = FreeTradeStatusLayout.availableWidth(-5, -5);
        int negativePanel = FreeTradeStatusLayout.availableWidth(-5, 100);

        assertTrue(noScreen <= 0, "a 0x0 window has no room for text, and the caller must be able to see that");
        assertTrue(negativeScreen <= 0, "a negative window is reported, never used to ask the font for a width");
        assertTrue(negativePanel <= 0, "a negative screen stays negative even when the panel is not");
    }

    @Test
    @DisplayName("each wrapped line keeps its identity and its width, so a line cannot be drawn with another's width")
    void measureMapsIndicesAndWidthsInOrder() {
        List<MeasuredLine> measured = FreeTradeStatusLayout.measure(new FreeTradeStatusLayout.WrappedLines() {
            @Override
            public int size() {
                return 3;
            }

            @Override
            public int widthOf(int index) {
                return new int[] {40, 0, 120}[index];
            }
        });

        assertEquals(3, measured.size());
        assertEquals(new MeasuredLine(0, 40), measured.get(0));
        assertEquals(new MeasuredLine(1, 0), measured.get(1));
        assertEquals(new MeasuredLine(2, 120), measured.get(2));
    }

    @Test
    @DisplayName("a negative measurement counts as zero, so the backdrop can never be drawn backwards")
    void measureClampsNegativeWidthsToZero() {
        List<MeasuredLine> measured = FreeTradeStatusLayout.measure(new FreeTradeStatusLayout.WrappedLines() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public int widthOf(int index) {
                return -50;
            }
        });

        assertEquals(List.of(new MeasuredLine(0, 0)), measured);
    }

    @Test
    @DisplayName("nothing to measure is an empty result, not a crash while the trade window is open")
    void measureSurvivesNoLines() {
        assertTrue(FreeTradeStatusLayout.measure(null).isEmpty());
        assertTrue(FreeTradeStatusLayout.measure(new FreeTradeStatusLayout.WrappedLines() {
            @Override
            public int size() {
                return 0;
            }

            @Override
            public int widthOf(int index) {
                throw new AssertionError("no line may be measured when there are none");
            }
        }).isEmpty());
    }

    @Test
    @DisplayName("a short status is drawn below the panel, where the player is already looking")
    void shortStripGoesBelowThePanel() {
        Plan plan = planOnNormalScreen(measured(28));

        assertEquals(1, plan.lines().size());
        assertEquals(170, plan.y(), "4 px below a panel that ends at 166");
        assertEquals(1 * FreeTradeStatusLayout.LINE_HEIGHT + 2 * FreeTradeStatusLayout.PADDING, plan.height(),
                "one line of 11 px plus 3 px of padding on each side");
        assertEquals(28 + 2 * FreeTradeStatusLayout.PADDING, plan.width(),
                "28 px of text plus the same padding on both sides");
        assertEquals(71, plan.x(), "centred on a 176 px wide panel");
    }

    @Test
    @DisplayName("a status that would run off the bottom is moved above the panel instead of disappearing")
    void stripThatDoesNotFitBelowGoesAbove() {
        // The panel ends at 196 and the screen is only 210 tall: the strip below it would end at 217.
        Plan plan = plan(measured(28), 320, 210, 72, 30, 176, 166);

        assertEquals(9, plan.y(), "30 - 4 - 17: above the panel, the only side where the whole strip fits");
        assertTrue(plan.y() < 30, "the strip must clear the panel it describes");
        assertTrue(plan.y() + plan.height() <= 210, "the strip must stay on the screen");
    }

    @Test
    @DisplayName("a status that fits on neither side of the panel is clamped on screen, not pushed off it")
    void stripThatFitsNowhereIsClampedIntoTheScreen() {
        // There is room below the panel, but the window is too short to hold the strip there, and there is no room
        // above the panel either.
        Plan plan = plan(measured(28), 320, 178, 72, 0, 176, 166);

        assertEquals(161, plan.y(), "clamped to screenHeight - height, so the whole strip is still visible");
        assertTrue(plan.y() >= 0, "the strip never starts above the screen");
        assertTrue(plan.y() + plan.height() <= 178, "the strip never extends past the bottom of the screen");
    }

    @Test
    @DisplayName("a strip taller than the whole window is top-aligned so its first line is still readable")
    void stripTallerThanTheScreenIsTopAligned() {
        // 25 wrapped lines: 25 * 11 + 6 = 281 px of strip on a 200 px tall screen. screenHeight - height is
        // negative here, and a negative y would put the first line above the top edge where it cannot be read.
        Plan plan = plan(measured(new int[25]), 320, 200, 72, 0, 176, 166);

        assertEquals(25, plan.lines().size());
        assertEquals(0, plan.y(), "a strip taller than the screen starts at the top instead of above it");
        assertTrue(plan.y() >= 0);
    }

    @Test
    @DisplayName("the backdrop is exactly as wide as the widest line, so it never dims the panel it explains")
    void widthIsTheWidestLinePlusPadding() {
        Plan plan = planOnNormalScreen(measured(40, 120, 75));

        assertEquals(120 + 2 * FreeTradeStatusLayout.PADDING, plan.width(),
                "the widest line plus PADDING on both sides, not the sum of the lines");
    }

    @Test
    @DisplayName("the backdrop grows with every wrapped line, so no line is drawn outside its own box")
    void heightFollowsTheWrappedLineCount() {
        Plan one = planOnNormalScreen(measured(40));
        Plan four = planOnNormalScreen(measured(40, 90, 30, 55));

        assertEquals(1 * FreeTradeStatusLayout.LINE_HEIGHT + 2 * FreeTradeStatusLayout.PADDING, one.height());
        assertEquals(4 * FreeTradeStatusLayout.LINE_HEIGHT + 2 * FreeTradeStatusLayout.PADDING, four.height(),
                "one logical sentence folded into four lines needs a box four lines tall");
        assertEquals(4, four.lines().size(), "one entry per line that will actually be drawn");
    }

    @Test
    @DisplayName("a status on a panel near a screen edge is slid back on screen instead of being cut off")
    void stripIsClampedToTheScreenEdges() {
        Plan left = plan(measured(28), 320, 240, 0, 0, 10, 166);
        Plan right = plan(measured(28), 320, 240, 310, 0, 10, 166);

        assertEquals(0, left.x(), "centring on a panel at the left edge would start the text left of 0");
        assertEquals(320 - right.width(), right.x(), "centring on a panel at the right edge would end past 320");
        assertEquals(320, right.x() + right.width());
    }

    @Test
    @DisplayName("a strip centred on a panel in the middle of the screen stays centred on it")
    void stripIsCentredOnThePanel() {
        Plan plan = plan(measured(100), 320, 240, 60, 0, 200, 166);

        assertEquals(60 + 200 / 2 - plan.width() / 2, plan.x(),
                "the strip hangs off the middle of the window it describes, not off the middle of the screen");
    }

    @Test
    @DisplayName("a sentence wider than the screen is reported at its true width and left-aligned, so the caller can "
            + "clip it rather than losing it")
    void overWideLineIsReportedWholeAndLeftAligned() {
        // The real failure: "Both payment materials are waived - nothing is taken from you" is literal language-file
        // text with no arguments, so the old argument-splitting wrapper could never break it and it stayed one line
        // wider than the screen for good. Nothing here may narrow it: the statement's own words are the whole point.
        Plan plan = planOnNormalScreen(measured(400));

        assertEquals(1, plan.lines().size(), "the sentence stays whole rather than being dropped");
        assertEquals(400 + 2 * FreeTradeStatusLayout.PADDING, plan.width(),
                "the reported width is the real width, never silently narrowed to fit the screen");
        assertTrue(plan.width() > 320, "an over-wide strip must say so, because the caller is the one who clips");
        assertEquals(0, plan.x(), "an over-wide strip cannot be centred, so it starts at the left edge");
        assertFalse(plan.lines().isEmpty(), "a strip with nothing in it would be a floating black rectangle");
    }

    @Test
    @DisplayName("no lines at all is an empty plan, not a crash and not an empty black box")
    void emptyInputYieldsAnEmptyPlan() {
        Plan noLines = plan(List.<MeasuredLine>of(), 320, 240, 0, 0, 176, 166);
        Plan nullLines = plan(null, 320, 240, 0, 0, 176, 166);

        assertTrue(noLines.lines().isEmpty(), "the caller must be able to skip the backdrop entirely");
        assertTrue(nullLines.lines().isEmpty());
    }

    @Test
    @DisplayName("a null entry in the list is skipped, so one bad line cannot crash the trade window")
    void nullEntryIsSkipped() {
        Plan plan = planOnNormalScreen(measuredWithNull(40));

        assertEquals(1, plan.lines().size(), "the usable line is still drawn");
        assertEquals(new MeasuredLine(0, 40), plan.lines().get(0));
        assertEquals(40 + 2 * FreeTradeStatusLayout.PADDING, plan.width());
    }

    @Test
    @DisplayName("a degenerate screen size or a negative measurement cannot throw while the trade window is open")
    void degenerateInputDoesNotThrow() {
        Plan zeroScreen = plan(measured(28), 0, 0, 0, 0, 0, 0);
        Plan negativeScreen = plan(measured(28), -5, -5, -1, -1, -5, -5);
        Plan negativeWidth = plan(measured(-50), 320, 240, 0, 0, 176, 166);

        assertEquals(1, zeroScreen.lines().size(), "the text is still reported even when it cannot be placed");
        assertEquals(0, zeroScreen.x(), "a strip wider than a zero-width screen cannot be centred anywhere");
        assertEquals(0, zeroScreen.y(), "a strip taller than the screen is top-aligned");
        assertEquals(0, negativeScreen.x(), "a negative screen is clamped, never used as a coordinate");
        assertEquals(0, negativeScreen.y());
        assertEquals(2 * FreeTradeStatusLayout.PADDING, negativeWidth.width(),
                "a negative measurement counts as no text, not as a negative-width backdrop");
        assertEquals(1, negativeWidth.lines().size(), "but the line is still reported");
    }
}
