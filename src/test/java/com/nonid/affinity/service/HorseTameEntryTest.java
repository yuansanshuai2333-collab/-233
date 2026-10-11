package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The horse-taming entry point, which is not a {@code TameableEntity} and does not tame the way wolves do.
 *
 * <p>The defect these tests exist for: the mixin hooked {@code AbstractHorseEntity#setOwner}'s TAIL and asked
 * {@code isTame()} there. Vanilla writes the owner FIRST and sets the tame flag afterwards, so on a first
 * taming that guard always saw {@code false}, returned early, and the normal taming path never produced a
 * bond. Every existing test still passed, because none of them encoded the write ORDER.
 *
 * <p>The assertions below are therefore about ordering, not about a method simply existing:
 * <ul>
 *   <li>the vanilla call sequence inside {@code bondWithPlayer} is read from bytecode and pinned;</li>
 *   <li>the guard rule is shown to reject exactly the state the old anchor observed;</li>
 *   <li>our compiled mixin is checked to anchor on {@code bondWithPlayer}'s success return and to have
 *       dropped the {@code setOwner} hook;</li>
 *   <li>the coverage claim (one declaration, no subclass overrides) is checked against the subclasses
 *       themselves rather than asserted in a comment.</li>
 * </ul>
 */
class HorseTameEntryTest {

    private static final String ABSTRACT_HORSE = "net.minecraft.entity.passive.AbstractHorseEntity";
    /** Constant-pool form: names inside a class file use slashes, not dots. */
    private static final String ABSTRACT_HORSE_INTERNAL = "net/minecraft/entity/passive/AbstractHorseEntity";
    private static final String BOND_DESCRIPTOR =
            "(Lnet/minecraft/entity/player/PlayerEntity;)Z";
    private static final UUID OWNER = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");

    @Test
    @DisplayName("vanilla writes the owner BEFORE the tame flag, which is why the old anchor missed the tame")
    void ownerIsWrittenBeforeTheTameFlag() throws Exception {
        List<String> calls = CodeScanner.invocations(classBytes(ABSTRACT_HORSE), "bondWithPlayer",
                BOND_DESCRIPTOR);

        // The exact sequence, read from 1.21.11 bytecode. Both writes are here, owner first -- which is the
        // fact the first version of the mixin got wrong. Pinned in full so a vanilla change cannot silently
        // invalidate the reasoning; if this fails after an update, re-read bondWithPlayer before editing it.
        assertEquals(List.of(
                        ABSTRACT_HORSE_INTERNAL + "#setOwner",
                        ABSTRACT_HORSE_INTERNAL + "#setTame",
                        "net/minecraft/advancement/criterion/TameAnimalCriterion#trigger",
                        ABSTRACT_HORSE_INTERNAL + "#getEntityWorld",
                        "net/minecraft/world/World#sendEntityStatus"),
                calls,
                "bondWithPlayer's call sequence changed; re-check where the taming anchor belongs");

        int ownerIndex = calls.indexOf(ABSTRACT_HORSE_INTERNAL + "#setOwner");
        int tameIndex = calls.indexOf(ABSTRACT_HORSE_INTERNAL + "#setTame");
        assertTrue(ownerIndex < tameIndex,
                "the owner is written first, so a hook on that write observes isTame() == false and must not"
                        + " be used as the taming anchor; observed order: " + calls);
    }

    @Test
    @DisplayName("the tame guard rejects an owner write, a missing owner and the client side")
    void tameGuardRequiresAllThreeFacts() {
        // The state the old anchor observed: owner written, tame flag not yet set. This is the assertion that
        // fails if someone "simplifies" the guard away.
        assertFalse(RelationshipRules.isTameCompleted(false, OWNER, true),
                "an owner write is not a completed tame");
        assertFalse(RelationshipRules.isTameCompleted(true, null, true),
                "no owner reference means the tame cannot be credited to anyone");
        assertFalse(RelationshipRules.isTameCompleted(true, OWNER, false),
                "the client side must never establish a bond");
        assertTrue(RelationshipRules.isTameCompleted(true, OWNER, true),
                "tamed + owner + server side is exactly the state that may be credited");
    }

    @Test
    @DisplayName("our mixin anchors on bondWithPlayer's success return, not on the owner write")
    void mixinAnchorsOnTheSuccessReturn() throws Exception {
        byte[] mixin = classBytes("com.nonid.affinity.mixin.VanillaHorseTameRelationshipMixin");

        assertTrue(CodeScanner.referencesMethod(mixin, ABSTRACT_HORSE_INTERNAL, "bondWithPlayer",
                        BOND_DESCRIPTOR),
                "the mixin must name bondWithPlayer as its target");
        assertTrue(CodeScanner.findCodeLengthByName(mixin, "noncreatureaffinity$afterHorseBonded") > 0,
                "the handler must have a real body, not be a stub");
        assertFalse(new String(mixin, StandardCharsets.ISO_8859_1).contains("setOwner"),
                "the anchor must not go back to setOwner: that write happens before the tame flag, so the"
                        + " guard silently rejects every first taming");
    }

    @Test
    @DisplayName("bondWithPlayer is declared once and no horse subclass overrides the taming writers")
    void theWholeFamilyIsCoveredByOneInjection() throws Exception {
        assertTrue(CodeScanner.findCodeLengthByName(classBytes(ABSTRACT_HORSE), "bondWithPlayer") > 0,
                "the injected method must be declared on the mixed class");

        // javap confirms none of these declares bondWithPlayer/setOwner/setTame on 1.21.11. Checked here so a
        // future override shows up as a failing test instead of a horse that silently stops bonding.
        String[] subclasses = {
            "net.minecraft.entity.passive.HorseEntity",
            "net.minecraft.entity.passive.AbstractDonkeyEntity",
            "net.minecraft.entity.passive.DonkeyEntity",
            "net.minecraft.entity.passive.MuleEntity",
            "net.minecraft.entity.passive.LlamaEntity",
            "net.minecraft.entity.passive.TraderLlamaEntity",
            "net.minecraft.entity.passive.CamelEntity",
            "net.minecraft.entity.mob.SkeletonHorseEntity",
            "net.minecraft.entity.mob.ZombieHorseEntity"
        };
        for (String subclass : subclasses) {
            byte[] bytes = classBytes(subclass);
            assertEquals(-1, CodeScanner.findCodeLengthByName(bytes, "bondWithPlayer"),
                    subclass + " overrides bondWithPlayer, so the parent injection no longer covers it;"
                            + " the mixin needs a target that includes this class");
        }
    }

    /** Read a compiled class from the test classpath without linking it. */
    private static byte[] classBytes(String className) throws Exception {
        String resource = className.replace('.', '/') + ".class";
        try (var stream = HorseTameEntryTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, "class not on the test classpath: " + className);
            return stream.readAllBytes();
        }
    }
}
