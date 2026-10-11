package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nonid.affinity.service.VillagerFreeTrades.WindowShape;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which kind of answer an open trade window gets, and in which order that is decided.
 *
 * <p>This exists because the first version of the rule deadlocked every freshly opened window and no test caught it:
 * the rule lived inside a method that needs a live server and a real villager, so nothing exercised it. The bug was an
 * ordering mistake -- the method returned "still coming up" whenever a window had never produced a verdict, while the
 * only code that marked a window as having produced one sat AFTER that return. A fresh window therefore waited for a
 * verdict that only a verdict could unlock.
 *
 * <p>The rule, restated so it cannot be got backwards again:
 *
 * <ul>
 *   <li>readiness is decided from what is present <em>now</em>; a previous success is never a precondition for the
 *       first success;</li>
 *   <li>the window's history is consulted only to choose the words for a window that <em>cannot</em> be judged -- it
 *       is what separates "still being built" from "worked, then broke";</li>
 *   <li>an unready window is "still coming up", never a refusal.</li>
 * </ul>
 */
class WindowShapeTest {

    @Test
    @DisplayName("a brand-new window whose data is already present is judgeable immediately, not waiting")
    void aFreshWindowCanBeJudgedImmediately() {
        // The deadlock, stated as a test: everResolved is false -- this is the first chance to judge -- and yet the
        // answer must be READY, because everything needed is right there. Requiring a previous success here is what
        // made the first quote impossible.
        assertEquals(WindowShape.READY, VillagerFreeTrades.classify(false, true, true, true),
                "the first quote must not depend on a previous quote having succeeded");
    }

    @Test
    @DisplayName("a brand-new window with nothing ready yet is waiting, not unavailable")
    void aFreshWindowWithNothingReadyWaits() {
        // Every shape of "the game has not finished opening this window": no customer registered yet, no trade list
        // built yet, no recipe named yet. None of them may be reported as a refusal -- that is the defect reported
        // from play. The arguments are consistent, because these facts are not independent: a window cannot name a
        // recipe when there is no list to name it from.
        //
        // Asserted through isWaiting() rather than against one enum constant, because two of the five outcomes mean
        // "still coming": the never-judged window, and a working window that has not named a recipe yet.
        assertTrue(VillagerFreeTrades.classify(false, false, true, true).isWaiting());
        assertTrue(VillagerFreeTrades.classify(false, true, false, false).isWaiting());
        assertTrue(VillagerFreeTrades.classify(false, true, true, false).isWaiting());
    }

    @Test
    @DisplayName("no outcome that is a refusal is ever reported as waiting")
    void refusalsAreNeverWaiting() {
        // The converse, so the two cannot drift: a window that cannot be traded with must not be described as
        // "still coming", or the player waits for something that is never going to arrive.
        assertFalse(VillagerFreeTrades.classify(true, false, true, true).isWaiting(),
                "a villager that lost its customer has stopped working");
        assertFalse(VillagerFreeTrades.classify(true, true, false, false).isWaiting(),
                "an empty trade list on a window that had one is a refusal, not a wait");
        assertFalse(VillagerFreeTrades.classify(false, true, true, true).isWaiting(),
                "a ready window is not waiting: it must be judged now, on its first chance");
    }

    @Test
    @DisplayName("a window that stops working after being judged is unavailable, not waiting again")
    void aWindowThatBreaksAfterWorkingIsUnavailable() {
        // The over-correction that followed: treating every missing condition as "not initialised yet" left a villager
        // that had died, been removed, or taken another customer showing a reassuring "quote updating" line for ever.
        assertEquals(WindowShape.OBJECT_UNAVAILABLE, VillagerFreeTrades.classify(true, false, true, true));
        assertEquals(WindowShape.NO_OFFERS, VillagerFreeTrades.classify(true, true, false, true));
        assertNotEquals(WindowShape.WAITING, VillagerFreeTrades.classify(true, false, true, true));
        assertNotEquals(WindowShape.WAITING, VillagerFreeTrades.classify(true, true, false, true));
    }

    @Test
    @DisplayName("a named recipe is required, and its absence is always waiting because there is nothing to refuse")
    void anUnnamedRecipeIsAlwaysWaiting() {
        // Whether or not the window has been judged before: a window that names no recipe has nothing to report, and
        // the next status will name one. Withdrawing it would tell the player a trade is unavailable for no reason.
        assertEquals(WindowShape.NO_RECIPE, VillagerFreeTrades.classify(false, true, true, false));
        assertEquals(WindowShape.NO_RECIPE, VillagerFreeTrades.classify(true, true, true, false));
    }

    @Test
    @DisplayName("readiness outranks the history in the only direction that matters")
    void readinessIsDecidedFromThePresentNotThePast() {
        // Whatever the history says, present readiness is what allows a verdict; and whenever a verdict is possible
        // the shape is READY rather than any kind of waiting.
        assertEquals(WindowShape.READY, VillagerFreeTrades.classify(true, true, true, true));
        assertEquals(WindowShape.READY, VillagerFreeTrades.classify(false, true, true, true));
        // And the history only changes the answer when a verdict is NOT possible.
        assertNotEquals(VillagerFreeTrades.classify(false, false, true, true),
                VillagerFreeTrades.classify(true, false, true, true));
    }
}
