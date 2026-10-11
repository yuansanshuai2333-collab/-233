package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the wiring of the settlement fix, and the isolation of the optional NeedsOfNature adapter.
 *
 * <p>The review of the previous attempt made two wiring requirements explicit: the settlement must use the REAL
 * stop reason rather than a time estimate, and reading another mod's internals must be isolated, version
 * limited and verifiable. Both are checked here.
 *
 * <p>Scope, stated honestly: this proves the decision is consulted and that the adapter is wired and gated. It
 * does not run NeedsOfNature, so the end-to-end behaviour -- including whether the reason is captured on a real
 * escape -- still needs the in-game pass.
 */
class MatingSettlementWiringTest {

    private static final String INTEGRATION = "com.nonid.affinity.non.NonIntegration";
    private static final String INTEGRATION_INTERNAL = "com/nonid/affinity/non/NonIntegration";
    private static final String RULES = "com/nonid/affinity/service/MatingSettlementRules";
    private static final String SESSIONS = "com/nonid/affinity/service/GroupMatingSessions";
    private static final String EVIDENCE = "com/nonid/affinity/service/MatingStopEvidence";

    @Test
    @DisplayName("the stop handler uses the real reason, not a time estimate")
    void stopHandlerUsesTheRealReason() throws Exception {
        byte[] integration = classBytes(INTEGRATION);

        assertTrue(CodeScanner.referencesMethod(integration, RULES, "decide",
                        "(Lcom/nonid/affinity/service/MatingSettlementRules$Facts;)"
                                + "Lcom/nonid/affinity/service/MatingSettlementRules$Decision;"),
                "the settlement decision must come from MatingSettlementRules.decide");
        assertTrue(CodeScanner.referencesMethod(integration, EVIDENCE, "observedKind",
                        "(Ljava/util/UUID;)Lcom/nonid/affinity/service/MatingStopEvidence$Kind;"),
                "the decision must be fed the observed stop reason");
        assertTrue(CodeScanner.referencesMethod(integration, "com/nonid/affinity/service/MatingSessionLifecycle",
                        "take", "(Ljava/lang/Object;Ljava/util/UUID;)Ljava/util/List;")
                        && CodeScanner.referencesMethod(classBytes("com.nonid.affinity.service.MatingSessionLifecycle"),
                        SESSIONS, "takeAll", "(Ljava/util/UUID;)Ljava/util/List;"),
                "the per-instance session must be consumed, which is what makes a stop settle at most once");

        // The rejected approach must be gone from the rules class entirely, not just unused.
        assertEquals(-1, CodeScanner.findCodeLengthByName(classBytes(RULES), "expectedStageTicks"),
                "the time-estimate helper must not exist: a time estimate is not completion evidence");
    }

    @Test
    @DisplayName("the stop path cannot charge affinity: the rejection penalty stays on the reject path")
    void stopPathCannotDoubleCharge() throws Exception {
        byte[] integration = classBytes(INTEGRATION);
        assertFalse(CodeScanner.referencesMethod(integration, "com/nonid/affinity/service/AffinityService",
                        "recordRejection", "(Lnet/minecraft/server/world/ServerWorld;"
                                + "Lnet/minecraft/server/network/ServerPlayerEntity;"
                                + "Lnet/minecraft/entity/Entity;"
                                + "Lcom/nonid/affinity/config/AffinityConfig;)V"),
                "the mating settlement must never apply the rejection penalty: the reject path already did");
        assertTrue(CodeScanner.referencesMethod(integration, "com/nonid/affinity/service/AffinityService",
                        "recordMating", "(Lnet/minecraft/server/world/ServerWorld;"
                                + "Lnet/minecraft/server/network/ServerPlayerEntity;"
                                + "Lnet/minecraft/entity/Entity;Z"
                                + "Lcom/nonid/affinity/config/AffinityConfig;)V"),
                "the completion reward is still the one entry point, reached only through the decision");
    }

    @Test
    @DisplayName("every animation is tracked from its start, and the old guesswork paths are gone")
    void everyAnimationIsTrackedFromItsStart() throws Exception {
        String source = Files.readString(
                Path.of("src", "main", "java", "com", "nonid", "affinity", "non", "NonIntegration.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("NonAnimationEvents.STARTED.register"),
                "the start event must be observed, or an autonomous animation cannot be judged at all");
        assertTrue(source.contains("NonAnimationEvents.STOPPED.register"),
                "the stop event is still what triggers settlement");
        assertFalse(source.contains("settleAutonomousMating"),
                "the old path settled any stop it had no record for, which is how an escape paid out");
        assertFalse(source.contains("ESCAPED."),
                "an escape must be recorded per instance in the session registry, not in a bare id set");
        assertTrue(source.contains("instanceId"),
                "settlement logs must name the instance, so one animation definition running twice can be told apart");
    }

    @Test
    @DisplayName("the internals adapter is isolated: optional config, plugin gate, verified version")
    void internalsAdapterIsIsolatedAndVersionLimited() throws Exception {
        Path config = Path.of("src", "main", "resources", "noncreatureaffinity.non.mixins.json");
        assertTrue(Files.isRegularFile(config), "the adapter needs its own mixin configuration");
        String json = Files.readString(config, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"required\": false"),
                "the adapter config must be optional, so a missing NeedsOfNature cannot break startup");
        assertTrue(json.contains("NonIntegrationMixinPlugin"),
                "the adapter must be gated by a plugin that checks the mod and its version");
        assertTrue(json.contains("NonNaturalEndMixin"), "the mixin must be listed in that config");

        Path fabric = Path.of("src", "main", "resources", "fabric.mod.json");
        String fabricJson = Files.readString(fabric, StandardCharsets.UTF_8);
        assertTrue(fabricJson.contains("noncreatureaffinity.non.mixins.json"),
                "the optional config must be registered, or the adapter never loads");

        Path plugin = Path.of("src", "main", "java", "com", "nonid", "affinity", "non", "mixin",
                "NonIntegrationMixinPlugin.java");
        String pluginSource = Files.readString(plugin, StandardCharsets.UTF_8);
        assertTrue(pluginSource.contains("isModLoaded(\"needsofnature\")"),
                "the plugin must switch off completely when NeedsOfNature is absent");
        assertTrue(pluginSource.contains("VERIFIED_VERSION_PREFIXES"),
                "the adapter must be limited to the versions it was verified against");
        assertTrue(pluginSource.contains("preApply"),
                "the plugin must verify the members it injects into from the class Mixin is about to transform");
        assertTrue(pluginSource.contains("setAdapterStatus"),
                "the outcome of that verification must be reported, not held silently");
        assertTrue(pluginSource.contains("will NOT be granted"),
                "when the adapter is off, the consequence must be stated in the log rather than hidden");
    }

    @Test
    @DisplayName("the adapter observes the two natural-completion paths, and says what it could not read")
    void adapterObservesTheCompletionPaths() throws Exception {
        byte[] mixin = classBytes("com.nonid.affinity.non.mixin.NonNaturalEndMixin");
        String pool = new String(mixin, StandardCharsets.ISO_8859_1);

        assertTrue(pool.contains("maybeAdvanceStages"),
                "the tick-driven completion fires the stop event inline, so its path must be observed");
        assertTrue(pool.contains("fireStopEvent"),
                "that path must be anchored to the STOP-EVENT dispatch: wrapping an earlier call in the same"
                        + " branch cleared the completion mark before the event could read it");
        assertFalse(pool.contains("scheduleMobMountRestores"),
                "the mount-restore call runs before the stop event, so it must not be the natural-end anchor");
        assertTrue(pool.contains("advanceStage"),
                "the explicit advance entry must be observed too, so a requested end is never mistaken for an"
                        + " interruption");
        assertTrue(pool.contains("recordFinalStageAdvance"),
                "the requested-end observation must be recorded in the evidence store the settlement reads");
        // The defect this guards: treating the WHOLE advance call as evidence. A step to an intermediate stage
        // is an ordinary running state -- NON fires its stage-advance event from that same code -- so a
        // third-party listener that stops the animation there, or a call left marked by an exception, must not
        // read as a completion. The completion is recorded where the final stage ends, and nowhere else.
        assertFalse(pool.contains("enterAdvanceStage") || pool.contains("leaveAdvanceStage")
                        || pool.contains("isAdvancing"),
                "being inside the advance call must not be recorded as evidence: only the final-stage stop is");
        // The reason-carrying stop method is private and its reason type is package-private, so it cannot be
        // injected into by name. The descriptor below is an ANCHOR for the one instruction inside advanceStage,
        // not a claim that the stop method itself is reachable.
        assertFalse(pool.contains("stopInstance(Lnet/minecraft/class_3218;Ljava/util/UUID;Z)Z")
                        || pool.contains("stopInstance(Lnet/minecraft/server/world/ServerWorld;"
                        + "Ljava/util/UUID;Z)Z"),
                "the reason-less stop overload must not be the anchor: only the final-stage one is");
    }

    @Test
    @DisplayName("the escape loss is wired, and the reward path stays the only source of a token")
    void escapeLossIsWiredSeparatelyFromTheReward() throws Exception {
        byte[] integration = classBytes(INTEGRATION);

        assertTrue(CodeScanner.referencesMethod(integration, RULES, "escapeLoss", "(ZZI)I"),
                "the size of the escape loss must come from the tested rule");
        assertTrue(CodeScanner.referencesMethod(integration,
                        "com/nonid/affinity/service/AffinityService", "recordForcedEscape",
                        "(Lnet/minecraft/server/world/ServerWorld;"
                                + "Lnet/minecraft/server/network/ServerPlayerEntity;"
                                + "Lnet/minecraft/entity/Entity;I"
                                + "Lcom/nonid/affinity/config/AffinityConfig;)I"),
                "the escape must be charged through its own entry point, which cannot hand out a token");
        assertTrue(CodeScanner.referencesMethod(integration, SESSIONS, "markPlayerEscape",
                        "(Ljava/util/UUID;Ljava/util/UUID;)I"),
                "an escape must be recorded as the PLAYER's doing, which is what makes it chargeable");

        // The request-rejection penalty must remain reachable only from the reject path.
        assertFalse(CodeScanner.referencesMethod(integration, "com/nonid/affinity/service/AffinityService",
                        "recordRejection", "(Lnet/minecraft/server/world/ServerWorld;"
                                + "Lnet/minecraft/server/network/ServerPlayerEntity;"
                                + "Lnet/minecraft/entity/Entity;"
                                + "Lcom/nonid/affinity/config/AffinityConfig;)V"),
                "the settlement must not charge a rejection: that belongs to the reject path");
    }

    @Test
    @DisplayName("the break-free action is wired to the session state, and says what it could not use")
    void breakFreeIsWired() throws Exception {
        byte[] integration = classBytes(INTEGRATION);
        assertTrue(CodeScanner.referencesMethod(integration, SESSIONS, "findForcedForPlayer",
                        "(Ljava/util/UUID;)Lcom/nonid/affinity/service/MatingSessions$Session;"),
                "break-free must resolve the target from the server's own session state, not from the client");
        assertTrue(CodeScanner.referencesMethod(integration, SESSIONS, "markPlayerEscape",
                        "(Ljava/util/UUID;Ljava/util/UUID;)I"),
                "breaking free must be recorded as the player's escape so the configured loss applies");
        assertTrue(CodeScanner.referencesMethod(integration,
                        "com/nonid/api/animation/NonAnimationApi", "stop",
                        "(Lnet/minecraft/server/world/ServerWorld;Ljava/util/UUID;)Z"),
                "escape must stop the owned session directly, even when a stage loops");
    }

    @Test
    @DisplayName("the forced mating is started as NON's own mob-attack escape, never as a hand-built PVP payload")
    void forcedMatingUsesTheMobAttackEscapePath() throws Exception {
        byte[] integration = classBytes(INTEGRATION);
        String pool = new String(integration, StandardCharsets.ISO_8859_1);

        assertTrue(pool.contains("startNowWithContext"),
                "the attack escape needs NON's own start, because the public API cannot declare that context");
        assertTrue(CodeScanner.referencesMethod(integration, "com/nonid/NonAnimationSessionData", "attack",
                        "()Lcom/nonid/NonAnimationSessionData;"),
                "the session data must be NON's creature-attack shape (NonAnimationSessionData.attack(), which"
                        + " is what NON's own buildModeContext(true) produces)");
        assertFalse(pool.contains("NonAnimationSessionData$PlayerAttack"),
                "the PVP payload must not be hand-built here: NON's PlayerAttack constructor rejects a null"
                        + " finishOwner (so the rc.39 start threw into the fallback and the struggle screen was"
                        + " never reached), and a non-null one makes NON believe a PLAYER is attacking the"
                        + " victim, which is a different feature with different side effects");
        assertFalse(pool.contains("playerAttack"),
                "nothing may claim a PLAYER is the attacker: the creature is, and NON's mob-attack path is the"
                        + " one that recognises it");
        assertTrue(pool.contains("AnimationStartContext"),
                "the start context is what carries the attack session data");
        assertTrue(CodeScanner.referencesMethod(integration, EVIDENCE, "adapterStatus",
                        "()Ljava/lang/String;"),
                "the integration must report whether the optional adapter is available");

        byte[] escapeMixin = classBytes("com.nonid.affinity.non.mixin.NonAttackEscapeMixin");
        String escapePool = new String(escapeMixin, StandardCharsets.ISO_8859_1);
        assertTrue(escapePool.contains("completeAttackEscape"),
                "the won struggle is announced by NON's own escape-completion call, which must be observed");
        assertTrue(escapePool.contains("NeedsOfNature"),
                "that call lives on NON's main class");

        String config = Files.readString(
                Path.of("src", "main", "resources", "noncreatureaffinity.non.mixins.json"),
                StandardCharsets.UTF_8);
        assertTrue(config.contains("NonAttackEscapeMixin"), "the escape mixin must be registered");
    }

    @Test
    @DisplayName("the attack start reuses NON's own stage list, and the log says whether a bar can appear")
    void theAttackStartReusesNonsOwnStages() throws Exception {
        byte[] integration = classBytes(INTEGRATION);
        String pool = new String(integration, StandardCharsets.ISO_8859_1);

        assertTrue(CodeScanner.referencesMethod(integration,
                        "com/nonid/internal/animation/data/AnimationDefinitions", "getDefinition",
                        "(Lnet/minecraft/util/Identifier;)"
                                + "Lcom/nonid/internal/animation/data/AnimationDefinitions$Definition;"),
                "the stage list must come from NON's own definition, which is what NonAnimationApi.start passes:"
                        + " hand-building it from the public view dropped each prop's suppressed-slot mask, so"
                        + " held items were shown that the content pack hides");
        assertFalse(pool.contains("AnimationStageInfo$ActorPropEntry"),
                "constructing NON's stage entries here is what lost the prop masks; that must not come back");
        assertTrue(CodeScanner.referencesMethod(integration,
                        "com/nonid/internal/animation/data/AnimationGameplayMetadata", "escapable",
                        "(Lnet/minecraft/util/Identifier;)Z"),
                "NON sends its struggle control per stage, so the start log must state how many stages are"
                        + " escapable; otherwise 'the bar never appeared' cannot be told from a broken start");
        assertTrue(pool.contains("could not start the forced mating as an attack escape"),
                "a failed attack start must say so in the log, and the fallback keeps the interaction alive");
    }

    /** Read a compiled class from the test classpath without linking it. */
    private static byte[] classBytes(String className) throws Exception {
        String resource = className.replace('.', '/') + ".class";
        try (var stream = MatingSettlementWiringTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, "class not on the test classpath: " + className);
            return stream.readAllBytes();
        }
    }
}

