package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wiring checks for "a creature asks, the player accepts, and it cannot start".
 *
 * <p>The behaviour lives in two places that cannot be executed without a live server and a real animation engine, so
 * it is pinned at the source level here. Three separate claims are checked, each of which has been wrong in this
 * project's history before:
 *
 * <ul>
 *   <li>the failure reason is actually observed, rather than a generic message being shown;</li>
 *   <li>the "no animation" gate is applied on BOTH request entry points, not just one;</li>
 *   <li>accepting and then failing costs no affinity -- the only two paths that charge are the timeout and an
 *       explicit rejection.</li>
 * </ul>
 */
class RequestFeedbackWiringTest {

    private static final Path SERVICE = Path.of("src/main/java/com/nonid/affinity/service");
    private static final Path NON = Path.of("src/main/java/com/nonid/affinity/non");
    private static final Path RESOURCES = Path.of("src/main/resources");

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException error) {
            throw new AssertionError("cannot read " + path.toAbsolutePath(), error);
        }
    }

    /** The body of one method, from its signature to the first line that closes it at the same indent. */
    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start > 0, "method not found: " + signature);
        int end = source.indexOf("\n    }", start);
        assertTrue(end > start, "could not find the end of: " + signature);
        return source.substring(start, end);
    }

    @Test
    @DisplayName("an accepted request that cannot start is explained by its real reason")
    void anAcceptedRequestIsExplainedByItsReason() {
        String source = read(SERVICE.resolve("RequestSystem.java"));
        String accept = methodBody(source, "public static boolean accept(ServerPlayerEntity player, " +
                "@Nullable MobEntity specific, long token)");

        assertTrue(accept.contains("InteractionStartFeedback.begin()"),
                "the start must be observed, or there is no reason to report");
        assertTrue(accept.contains("feedback.reason()"),
                "and the observed reason must be the one reported, not a fixed guess");

        String report = methodBody(source, "private static void reportStartFailure(");
        assertTrue(report.contains("interaction_failure."),
                "the player must be told the specific reason, using the same message family as the manual menu");
        assertTrue(report.contains("reason.name().toLowerCase"),
                "the reason maps to its own message key rather than collapsing to one sentence");
        assertTrue(report.contains("GameplayFeedback.send(player,message, true)") && report.contains("GameplayFeedback.send(player,message, false)"),
                "both original fallback sends must route through source-aware feedback; new clients coalesce, commands/old clients retain text");
        assertTrue(report.contains("LOGGER.warn"),
                "and it must be logged with the creature, so a report can be matched to a time");
    }

    @Test
    @DisplayName("a generic 'cannot start now' is no longer the answer to an acceptance that failed")
    void theGenericMessageIsNoLongerUsedForAcceptance() {
        String accept = methodBody(read(SERVICE.resolve("RequestSystem.java")),
                "public static boolean accept(ServerPlayerEntity player, @Nullable MobEntity specific, long token)");
        assertFalse(accept.contains("\"cannot_start_now\""),
                "the old generic message described every failure as 'maybe it is busy', which hid whether the "
                        + "creature had no animation, no space, or the wrong ground");
    }

    @Test
    @DisplayName("accepting and then failing costs no affinity")
    void aFailedAcceptanceCostsNothing() {
        String accept = methodBody(read(SERVICE.resolve("RequestSystem.java")),
                "public static boolean accept(ServerPlayerEntity player, @Nullable MobEntity specific, long token)");
        assertFalse(accept.contains("addAffinity"),
                "a failed start is the game's doing, not the player's: accepting must never cost affinity");
        assertFalse(accept.contains("recordRejection"),
                "and an acceptance is not a rejection");

        // The claim is only meaningful if the charges really are where the code says they are.
        String source = read(SERVICE.resolve("RequestSystem.java"));
        String reject = methodBody(source, "public static boolean reject(ServerPlayerEntity player, " +
                "@Nullable MobEntity specific, long token)");
        assertTrue(reject.contains("recordRejection"),
                "an explicit rejection still charges, which is what makes the accept path's silence meaningful");
        assertTrue(source.contains("affinityLossOnTimeout"),
                "and being ignored for the whole window still charges");
    }

    @Test
    @DisplayName("the no-animation gate is applied on both request entry points")
    void theGateIsAppliedOnBothEntryPoints() {
        String source = read(SERVICE.resolve("RequestSystem.java"));
        // The periodic scan...
        assertFalse(methodBody(source, "public static void tickRequests(").contains("sendRequest("),
                "the periodic scan must apply the gate");
        // ...and the entry point used when a friend creature tried to act on its own.
        assertTrue(methodBody(source, "public static boolean canRequest(").contains("requestVerdict("),
                "the on-the-spot request path must apply the same gate, or a creature with no animation can still "
                        + "raise a request through it");
        assertTrue(source.contains("RequestVerdict requestVerdict("),
                "and the rule itself must live in one place, so the two cannot drift apart");
    }

    @Test
    @DisplayName("the gate asks whether an animation EXISTS, not whether it could start right now")
    void theGateAsksAboutExistenceNotEligibility() {
        // Getting this backwards is silent: gating on start eligibility would forbid asking whenever the pair stood
        // somewhere awkward, which is not the requested behaviour and would look like a random failure to ask.
        String bridge = read(NON.resolve("NonBridge.java"));
        String method = methodBody(bridge, "public static boolean hasMatchingAnimation(");
        assertTrue(method.contains("listCandidates"),
                "the question is answered from the candidate list");
        assertFalse(method.contains("startEligible"),
                "and it must NOT filter on start eligibility: an animation blocked by the surroundings is still an "
                        + "animation the creature may ask for");

        assertTrue(read(SERVICE.resolve("RequestSystem.java")).contains("NonBridge.hasMatchingAnimation("),
                "the request layer must go through that bridge method");
    }

    @Test
    @DisplayName("every reason the feedback can report has a message in both languages")
    void everyReasonHasAMessage() {
        java.util.List<String> langs = java.util.List.of("en_us.json", "zh_cn.json");
        for (String lang : langs) {
            String text = read(RESOURCES.resolve("assets/noncreatureaffinity/lang/" + lang));
            for (InteractionStartFeedback.Reason reason : InteractionStartFeedback.Reason.values()) {
                String key = "interaction_failure." + reason.name().toLowerCase(java.util.Locale.ROOT);
                assertTrue(text.contains(key),
                        lang + " is missing a message for " + reason + " (key " + key + "), so that failure would "
                                + "be shown to the player as a raw translation key");
            }
        }
    }
}
