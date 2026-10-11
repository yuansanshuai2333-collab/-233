package com.nonid.affinity.non;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the two NeedsOfNature facts the forced-mating start depends on, by asking NON itself instead of trusting
 * a reading of its bytecode.
 *
 * <p><b>Why this exists.</b> rc.39 started the forced mating with a hand-built
 * {@code NonAnimationSessionData.PlayerAttack(victim, null, initiator, attackers)}. NON's record constructor
 * rejects a null {@code finishOwnerUuid} outright, so that call always threw
 * {@link IllegalArgumentException}, the start fell back to a plain animation, and the A/D struggle screen the
 * batch was written for was never reached -- while every existing test still passed, because they only checked
 * that the code mentioned the right classes. These assertions make the same mistake fail loudly.
 *
 * <p>The classes are read reflectively and WITHOUT triggering static initialisation where it matters:
 * {@code NonAnimationSessionData} has no static state, so loading it is safe in a plain JUnit process. NON's
 * main class is deliberately never touched here -- its initialiser builds registries and would not survive
 * outside the game.
 *
 * <p>Honest scope: this proves the payload shape. It does not run an animation, so whether the screen actually
 * appears and the mash actually advances still needs the in-game pass.
 */
class NonAttackPayloadContractTest {

    private static final String SESSION_DATA = "com.nonid.NonAnimationSessionData";

    @Test
    @DisplayName("a creature attack is attack mode with NO PlayerAttack, which is what the forced mating uses")
    void creatureAttacksCarryNoPlayerAttack() throws Exception {
        Class<?> data = load(SESSION_DATA);
        assumeTrue(data != null, "NeedsOfNature is not on the test classpath; contract test skipped");

        Object attack = data.getMethod("attack").invoke(null);

        assertNotNull(attack, "NonAnimationSessionData.attack() is NON's own creature-attack shape");
        assertEquals(Boolean.TRUE, data.getMethod("isAttack").invoke(attack),
                "NON sends its struggle control only when the start context says ATTACK");
        assertNull(data.getMethod("playerAttack").invoke(attack),
                "the creature-attack shape carries no PlayerAttack; that record is the PVP shape, and NON's own"
                        + " NonGiveUpSystem tells the two apart with isPlayerAttackContext(...)");
    }

    @Test
    @DisplayName("the PVP payload rejects a null finishOwner, so it must never be hand-built")
    void thePvpPayloadRejectsANullFinishOwner() throws Exception {
        Class<?> payload = load(SESSION_DATA + "$PlayerAttack");
        assumeTrue(payload != null, "NeedsOfNature is not on the test classpath; contract test skipped");

        Constructor<?> canonical = payload.getDeclaredConstructor(
                UUID.class, UUID.class, UUID.class, Set.class);
        UUID victim = UUID.randomUUID();
        UUID initiator = UUID.randomUUID();

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> canonical.newInstance(victim, null, initiator, Set.of(initiator)),
                "NON refuses a payload without a finishOwner; building one here can only ever throw");
        assertTrue(thrown.getCause() instanceof IllegalArgumentException,
                "the refusal is the documented IllegalArgumentException, not some other failure");

        Object complete = canonical.newInstance(victim, initiator, initiator, Set.of(initiator));
        assertNotNull(complete,
                "the same payload with a non-null finishOwner is accepted -- and would tell NON that a PLAYER"
                        + " is attacking the victim, which is a different feature with different side effects");
    }

    /** Load a class for reflection only, or null when NeedsOfNature is absent from the test classpath. */
    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, NonAttackPayloadContractTest.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;
        }
    }
}
