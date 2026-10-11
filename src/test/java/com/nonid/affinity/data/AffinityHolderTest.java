package com.nonid.affinity.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Covers the behaviour the Mixin relies on: {@link AffinityHolder} hands out a stable,
 * lazily created data object, while the peek accessor never creates anything.
 *
 * <p>Creating real Minecraft entities in a plain unit test is not possible (the entity type
 * registry is not bootstrapped), so this uses a hand-written holder. The actual Mixin
 * injection is verified by booting the mod in a real game, which the development server run
 * does — see DEVELOPMENT.md.
 */
class AffinityHolderTest {

    private static final UUID PLAYER = UUID.fromString("55555555-5555-5555-5555-555555555555");

    /** Minimal stand-in for what the Mixin generates. */
    private static final class FakeHolder implements AffinityHolder {
        private CreatureAffinityData data;

        @Override
        public CreatureAffinityData affinityDataRaw() {
            return this.data;
        }

        @Override
        public void setAffinityDataRaw(CreatureAffinityData value) {
            this.data = value;
        }
    }

    @Test
    void dataIsCreatedLazilyAndStaysStable() {
        FakeHolder holder = new FakeHolder();

        // Peeking before anything exists must not create data.
        assertNull(holder.peekAffinityData());
        assertNull(holder.affinityDataRaw());

        CreatureAffinityData created = holder.affinityData();
        assertNotNull(created);
        assertTrue(created.isEmpty());
        assertTrue(created == holder.affinityData(), "the same instance must be reused");
        assertTrue(created == holder.peekAffinityData(), "peek must see it after creation");
    }

    @Test
    void writesThroughTheHolderAreVisibleThroughPeek() {
        FakeHolder holder = new FakeHolder();

        holder.affinityData().getOrCreate(PLAYER, 12);
        assertNotNull(holder.peekAffinityData());
        assertEquals(12, holder.peekAffinityData().get(PLAYER).getAffinity());
    }

    @Test
    void holdersDoNotShareState() {
        FakeHolder first = new FakeHolder();
        FakeHolder second = new FakeHolder();

        first.affinityData().getOrCreate(PLAYER, 30);

        assertNotNull(first.peekAffinityData());
        assertNull(second.peekAffinityData(), "each entity keeps its own data");
        assertFalse(second.affinityData().hasAnyBefriended());
    }

    @Test
    void settingDataDirectlyReplacesIt() {
        FakeHolder holder = new FakeHolder();
        CreatureAffinityData replacement = new CreatureAffinityData();
        replacement.setGeneration(4);

        holder.setAffinityDataRaw(replacement);

        assertNotNull(holder.peekAffinityData());
        assertEquals(4, holder.peekAffinityData().getGeneration());
    }

    @Test
    void perEntityLoopsReadWithoutCreating() throws Exception {
        // The peek/affinityData split above only helps if the code that walks EVERY entity uses the right
        // one. Three such paths used the creating accessor, so the first tick of a world attached an empty
        // record to every mob in it and persisted it -- observed in a production run as "entries: []" on an
        // untamed wolf. Checked against the sources because the loops need a live ServerWorld to run.
        assertNoCreatingAccessorInLoop(
                java.nio.file.Path.of("src", "main", "java", "com", "nonid", "affinity",
                        "service", "RequestSystem.java"),
                "tickCooldowns");
        assertNoCreatingAccessorInLoop(
                java.nio.file.Path.of("src", "main", "java", "com", "nonid", "affinity",
                        "NonCreatureAffinity.java"),
                "tickDailyDecay");
        assertNoCreatingAccessorInLoop(
                java.nio.file.Path.of("src", "main", "java", "com", "nonid", "affinity",
                        "command", "AffinityCommand.java"),
                "countTracked");
    }

    /**
     * The named method must not reach for the creating accessor on the loop's holder.
     *
     * <p>A source check rather than a behavioural one: these methods walk a live world, and a unit test
     * cannot build one. It is deliberately narrow -- it looks for the exact expression the bug used.
     */
    private static void assertNoCreatingAccessorInLoop(java.nio.file.Path source, String method)
            throws Exception {
        String text = java.nio.file.Files.readString(source, java.nio.charset.StandardCharsets.UTF_8);
        // Find the DECLARATION, not a call to it: the declaration is the line that both names the method and
        // starts with a modifier.
        StringBuilder body = new StringBuilder();
        boolean inside = false;
        for (String rawLine : text.split("\n", -1)) {
            // Normalise the line ending: the sources are CRLF, and a stray '\r' would stop the closing brace
            // below from ever matching, so the body would swallow the next method as well.
            String line = rawLine.endsWith("\r") ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
            String trimmed = line.trim();
            if (!inside && line.contains(method + "(")
                    && (trimmed.startsWith("private ") || trimmed.startsWith("public ")
                        || trimmed.startsWith("protected ") || trimmed.startsWith("static "))) {
                inside = true;
            }
            if (inside) {
                body.append(line).append('\n');
                if (line.equals("    }")) {
                    break;
                }
            }
        }
        String methodBody = body.toString();
        assertFalse(methodBody.isEmpty(), method + " must exist in " + source);
        assertFalse(methodBody.contains("holder.affinityData()"),
                method + " walks every entity and must peek, not create: " + source);
        assertTrue(methodBody.contains("peekAffinityData()"),
                method + " must read through peekAffinityData(): " + source);
    }
}
