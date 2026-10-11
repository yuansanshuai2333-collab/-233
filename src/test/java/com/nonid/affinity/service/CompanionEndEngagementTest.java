package com.nonid.affinity.service;

import com.nonid.affinity.service.policy.EngagementState;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the actual private production method, without constructing a Minecraft entity. */
class CompanionEndEngagementTest {
    private static final Class<?> STATE;
    private static final Method END;
    static {
        try {
            STATE = Class.forName("com.nonid.affinity.service.CompanionCombatController$Runtime");
            END = Class.forName("com.nonid.affinity.service.CompanionCombatController")
                    .getDeclaredMethod("endEngagement", STATE);
            END.setAccessible(true);
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    private static Object state() throws Exception {
        var constructor = STATE.getDeclaredConstructor(UUID.class);
        constructor.setAccessible(true);
        return constructor.newInstance(UUID.randomUUID());
    }
    private static Field field(String name) throws Exception {
        Field result = STATE.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
    private static void assertEnded(Object state, EngagementState progress) throws Exception {
        assertNull(field("engagedTarget").get(state));
        assertFalse(field("engagementWasSelfDefence").getBoolean(state));
        assertEquals(0L, field("engagedSequence").getLong(state));
        assertEquals(Double.MAX_VALUE, field("lastDistanceToTarget").getDouble(state));
        assertFalse(progress.isEngaged());
        assertNull(progress.targetId());
    }
    @Test void productionEndClearsPopulatedStateAndAdvancesGeneration() throws Exception {
        Object state = state();
        UUID target = UUID.randomUUID();
        field("engagedTarget").set(state, target);
        field("engagementWasSelfDefence").setBoolean(state, true);
        field("engagedSequence").setLong(state, 42L);
        field("lastDistanceToTarget").setDouble(state, 3.0);
        EngagementState progress = (EngagementState) field("progress").get(state);
        progress.begin(target, 0L);
        long generation = progress.generation();
        // A recursive production implementation throws here: failure is not swallowed.
        END.invoke(null, state);
        assertEnded(state, progress);
        assertEquals(generation + 1, progress.generation());
        assertFalse(progress.recordHit(target, 10L));
    }
    @Test void repeatedProductionEndReturnsNormally() throws Exception {
        Object state = state();
        EngagementState progress = (EngagementState) field("progress").get(state);
        END.invoke(null, state);
        END.invoke(null, state);
        assertEnded(state, progress);
    }
    /** Standalone verification of the same test bodies; normal Gradle runs use @Test. */
    public static void main(String[] args) throws Exception {
        var test = new CompanionEndEngagementTest();
        test.productionEndClearsPopulatedStateAndAdvancesGeneration();
        test.repeatedProductionEndReturnsNormally();
        System.out.println("PASS: 2 production endEngagement checks");
    }
}
