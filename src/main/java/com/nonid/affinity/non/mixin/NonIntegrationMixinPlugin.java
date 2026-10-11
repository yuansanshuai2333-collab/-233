package com.nonid.affinity.non.mixin;

import com.nonid.affinity.non.compat.ServerAnimationControllerBytecode;
import com.nonid.affinity.service.MatingStopEvidence;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Switches the NeedsOfNature natural-completion adapter on ONLY where it was verified, and reports what it
 * found.
 *
 * <p>Two things this plugin deliberately does NOT do:
 * <ul>
 *   <li><b>It never loads a game or NeedsOfNature class.</b> The plugin runs before the game bootstraps its
 *       registries; touching those classes here crashed the server outright in testing
 *       ({@code ServerAnimationController.<clinit>}: "Not bootstrapped"), and resolving method signatures was
 *       enough to load {@code Entity} before the mixin phase, which broke cardinal-components
 *       ("target net.minecraft.class_1297 was loaded too early"). Verification therefore reads the ASM tree
 *       Mixin already hands us, which touches no JVM class at all.</li>
 *   <li><b>It never fails silently.</b> The gate and the member check both write their outcome to
 *       {@link MatingStopEvidence#setAdapterStatus}, which the integration logs once at startup at WARN level.
 *       If the adapter is off, the settlement refuses every completion reward, and the log says exactly why.</li>
 * </ul>
 *
 * <p>The adapter's injections use {@code require = 0} on purpose: a NeedsOfNature update that moves an internal
 * method should not break a player's game. That flag tolerates ONE thing -- a failed injection at a chosen
 * anchor -- and nothing else: it does not cover a class-loading error, a bad target descriptor, or any other
 * exception in this plugin or in the mixins. The consequence of a tolerated failure is a refused reward (never
 * a wrong one), which is why every anchor this adapter depends on is verified here, by owner, name, descriptor
 * and position, and reported at ERROR when it is not found.
 */
public final class NonIntegrationMixinPlugin implements IMixinConfigPlugin {

    /** Verified core versions; only explicit build metadata may follow a core version. */
    private static final String[] VERIFIED_VERSION_PREFIXES = {"1.5.1", "1.5.2"};

    private static final String CONTROLLER = "com.nonid.internal.animation.server.ServerAnimationController";

    /** Mixin hands target names in internal (slash) form, so the comparison has to use it too. */
    private static final String CONTROLLER_INTERNAL =
            "com/nonid/internal/animation/server/ServerAnimationController";

    /** NON's own class, where the successful-escape call lives. */
    private static final String NEEDS_OF_NATURE = "com.nonid.NeedsOfNature";
    private static final String NEEDS_OF_NATURE_INTERNAL = "com/nonid/NeedsOfNature";

    private boolean enabled;

    @Override
    public void onLoad(String mixinPackage) {
        if (!FabricLoader.getInstance().isModLoaded("needsofnature")) {
            this.enabled = false;
            MatingStopEvidence.setAdapterStatus(
                    "NeedsOfNature is not installed; mating affinity settlement is inactive");
            return;
        }
        String version = FabricLoader.getInstance().getModContainer("needsofnature")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        if (!isVerifiedVersion(version)) {
            this.enabled = false;
            // Only the version string is touched: no class loading, no registry access.
            MatingStopEvidence.setAdapterStatus("NeedsOfNature " + version + " is not a version the"
                    + " completion-path adapter was verified against (" + String.join(", ",
                    VERIFIED_VERSION_PREFIXES) + "); mating affinity will NOT be granted automatically,"
                    + " because a completed mating cannot be told from an interrupted one");
            return;
        }
        this.enabled = true;
        MatingStopEvidence.setAdapterStatus("enabled for NeedsOfNature " + version
                + "; settlement uses the real stop reason");
    }

    /** Whether this NON version is in the verified range. */
    static boolean isVerifiedVersion(String version) {
        if (version == null) {
            return false;
        }
        for (String prefix : VERIFIED_VERSION_PREFIXES) {
            if (version.equals(prefix) || (version.startsWith(prefix + "+")
                    && version.length() > prefix.length() + 1)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean apply = this.enabled && (mixinClassName.endsWith(".NonVariantStorageMixin")
                || isController(targetClassName) || isNeedsOfNature(targetClassName)
                || "com.nonid.entity.PregnancyEggEntity".equals(targetClassName.replace('/', '.'))
                || "com.nonid.NeedsOfNature$PregnancyHatchState".equals(targetClassName.replace('/', '.'))
                || "com.nonid.NonDebugAnimationSelectionSystem".equals(targetClassName)
                || "com/nonid/NonDebugAnimationSelectionSystem".equals(targetClassName)
                || "com.nonid.NonDebugAnimationSelectionSystem$Session".equals(targetClassName)
                || "com/nonid/NonDebugAnimationSelectionSystem$Session".equals(targetClassName)
                || "com.nonid.NonDebugAnimationSelectionSystem$Choice".equals(targetClassName)
                || "com/nonid/NonDebugAnimationSelectionSystem$Choice".equals(targetClassName)
                || "com.nonid.internal.animation.AnimationEngine".equals(targetClassName)
                || "com/nonid/internal/animation/AnimationEngine".equals(targetClassName));
        if (!targetClassName.startsWith("net.minecraft")) {
            // Logged so the log states whether the adapter actually reached the classes it needs. Silent here
            // would make "the settlement refuses everything" impossible to diagnose.
            org.slf4j.LoggerFactory.getLogger("noncreatureaffinity").info(
                    "[NoN Affinity] natural-completion adapter target {} -> {} (mixin {})", targetClassName,
                    apply ? "applying" : "not applied", mixinClassName);
        }
        return apply;
    }

    /** Whether a Mixin-supplied target name (slash or dot form) is NON's animation controller. */
    private static boolean isController(String targetClassName) {
        if (targetClassName == null) {
            return false;
        }
        return targetClassName.equals(CONTROLLER_INTERNAL) || targetClassName.equals(CONTROLLER);
    }

    /** Whether it is NON's main class, where the successful-escape call lives. */
    private static boolean isNeedsOfNature(String targetClassName) {
        if (targetClassName == null) {
            return false;
        }
        return targetClassName.equals(NEEDS_OF_NATURE_INTERNAL) || targetClassName.equals(NEEDS_OF_NATURE);
    }

    /**
     * Verify the members the adapter injects into, from the class Mixin is about to transform.
     *
     * <p>Reads descriptors from the ASM tree: no JVM class is loaded, so this is safe this early.
     */
    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                         IMixinInfo mixinInfo) {
        if (mixinClassName.endsWith(".NonNativeStartIntentMixin")
                || mixinClassName.endsWith(".NonNativeEventContextMixin")) {
            String name = mixinClassName.endsWith(".NonNativeStartIntentMixin")
                    ? "startNowWithContext" : "startAndBroadcast";
            boolean found = targetClass.methods.stream().anyMatch(method -> method.name.equals(name));
            org.slf4j.LoggerFactory.getLogger("noncreatureaffinity").info(
                    "[NoN Affinity] native intent/context adapter {} anchor: {}", name,
                    found ? "found; exact injection is required" : "missing");
            return;
        }
        if (mixinClassName.endsWith(".NonDebugStartMixin")) {
            boolean found = targetClass.methods.stream().anyMatch(method -> method.name.equals("executeChoice"));
            org.slf4j.LoggerFactory.getLogger("noncreatureaffinity").info(
                    "[NoN Affinity] explicit debug start anchor: {}", found ? "found" : "missing");
            return;
        }
        if (mixinClassName.endsWith(".NonBirthFamilyMixin")) {
            boolean found = targetClass.methods.stream().anyMatch(method ->
                    method.name.equals("applyPregnancyMobFlagsForEgg"));
            org.slf4j.LoggerFactory.getLogger("noncreatureaffinity").info(
                    "[NoN Affinity] birth family metadata anchor: {}", found ? "found" : "missing");
            return;
        }
        if (mixinClassName.endsWith(".NonStartFailureMessageMixin")) {
            boolean found = targetClass.methods.stream().anyMatch(method -> method.name.equals("dbg")
                    && method.desc.startsWith("(Ljava/lang/String;[Ljava/lang/Object;)L"));
            org.slf4j.LoggerFactory.getLogger("noncreatureaffinity").info(
                    "[NoN Affinity] interaction failure message anchor: {}",
                    found ? "found" : "missing; unknown-reason fallback will be used");
            return;
        }
        if (isNeedsOfNature(targetClassName)) {
            // The successful-escape call is what marks a won struggle as the player's escape.
            for (MethodNode method : targetClass.methods) {
                if ("completeAttackEscape".equals(method.name)) {
                    return;
                }
            }
            MatingStopEvidence.setAdapterStatus("NeedsOfNature changed " + targetClassName
                    + ": completeAttackEscape was not found, so a won struggle cannot be recorded as an"
                    + " escape (no escape loss will be charged)");
            org.slf4j.LoggerFactory.getLogger("noncreatureaffinity").error(
                    "[NoN Affinity] could not find completeAttackEscape in {}; a successful struggle will not"
                            + " be charged the escape loss. Please report this with the NeedsOfNature version.",
                    targetClassName);
            return;
        }
        if (!isController(targetClassName)) {
            return;
        }
        // Three things are checked, because "the method exists" proves nothing about either injection: a mixin
        // whose method selector does not match its target, or whose @At anchor is absent, injects NOTHING and --
        // under require = 0 -- says NOTHING. Both anchors are therefore verified by owner, name and descriptor,
        // and the advance entry is matched by its full signature rather than by name alone.
        if (!ServerAnimationControllerBytecode.hasNaturalCompletionMethod(targetClass)) {
            reportAnchorMissing(targetClassName, ServerAnimationControllerBytecode.NATURAL_COMPLETION_METHOD
                    + "(...)", "the tick-driven completion path was not found, so a mating that runs out on its"
                    + " own will NOT grant affinity");
            return;
        }
        if (!ServerAnimationControllerBytecode.hasInstanceIdAtExpectedArgumentIndex(targetClass)) {
            // A name-only check cannot see this: the handler reads the instance id from a fixed argument index,
            // so a changed argument layout would make the injection fail silently.
            reportAnchorMissing(targetClassName, "the instance id at argument "
                            + ServerAnimationControllerBytecode.INSTANCE_ID_ARGUMENT_INDEX + " of "
                            + ServerAnimationControllerBytecode.NATURAL_COMPLETION_METHOD + "(...)",
                    "the tick-driven completion path's arguments moved, so its handler cannot read the instance"
                            + " id and a mating that runs out on its own will NOT grant affinity");
            return;
        }
        if (!ServerAnimationControllerBytecode.hasNaturalCompletionAnchor(targetClass)) {
            reportAnchorMissing(targetClassName, ServerAnimationControllerBytecode.NATURAL_COMPLETION_METHOD
                            + "(...) -> " + ServerAnimationControllerBytecode.STOP_EVENT_METHOD
                            + ServerAnimationControllerBytecode.NATURAL_COMPLETION_ANCHOR_DESCRIPTOR,
                    "the tick-driven completion path has no stop-event dispatch to wrap, so a mating that runs"
                            + " out on its own will NOT grant affinity");
            return;
        }
        if (!ServerAnimationControllerBytecode.hasAdvanceMethod(targetClass)) {
            reportAnchorMissing(targetClassName, ServerAnimationControllerBytecode.ADVANCE_METHOD
                            + ServerAnimationControllerBytecode.ADVANCE_METHOD_DESCRIPTOR,
                    "the explicit advance entry was not found with the signature the mixin targets, so the"
                            + " next-stage control will NOT grant affinity");
            return;
        }
        MethodNode advance = ServerAnimationControllerBytecode.advanceMethod(targetClass);
        if (!ServerAnimationControllerBytecode.containsStopInstanceInvocation(advance)) {
            reportAnchorMissing(targetClassName, "a call to " + ServerAnimationControllerBytecode
                            .CONTROLLER_OWNER + "#stopInstance"
                            + ServerAnimationControllerBytecode.FINAL_STAGE_STOP_DESCRIPTOR,
                    "the explicit advance entry has no final-stage stop call to anchor on, so the next-stage"
                            + " control will NOT grant affinity");
            return;
        }
        MatingStopEvidence.setAdapterStatus("enabled and verified against " + targetClassName
                + " (both completion anchors present with their owning class and full signatures)");
        org.slf4j.LoggerFactory.getLogger("noncreatureaffinity").info(
                "[NoN Affinity] stop-path adapter verified against {}: the tick-driven completion path with its"
                        + " stop-event dispatch, and the final-stage stop inside the advance entry"
                        + " ({}(...)), are both present. Stepping to an intermediate stage is NOT a completion,"
                        + " and a stop that takes neither path is recorded as an interruption",
                targetClassName, ServerAnimationControllerBytecode.ADVANCE_METHOD);
    }

    /** Report an unusable anchor at ERROR and record the consequence, which is always a refused reward. */
    private static void reportAnchorMissing(String targetClassName, String what, String consequence) {
        MatingStopEvidence.setAdapterStatus("NeedsOfNature changed " + targetClassName + ": " + what
                + " was not found, so " + consequence);
        org.slf4j.LoggerFactory.getLogger("noncreatureaffinity").error(
                "[NoN Affinity] could not find {} in {}; {}. Please report this with the NeedsOfNature version.",
                what, targetClassName, consequence);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // No target set declarations to reconcile.
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                          IMixinInfo mixinInfo) {
        // preApply already reported the outcome; nothing further to verify here.
    }
}
