package com.nonid.affinity.non.compat;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The instructions in NON's animation controller that this addon's adapter anchors on, kept where a plain
 * JUnit test can reach them -- and OUTSIDE the mixin package on purpose.
 *
 * <p><b>Why it must not live in the mixin package.</b> Mixin restricts the classes that may be loaded from a
 * declared mixin package: a class sitting in {@code com.nonid.affinity.non.mixin} that is NOT itself a mixin
 * can be refused with an {@code IllegalClassLoadError} when something loads it as an ordinary class -- which is
 * exactly what the mixin config plugin does with this one, and what a unit test does with it. {@code require =
 * 0} does not help: that flag only tolerates a failed INJECTION, not a failed class load, and the plugin runs
 * before any injection. It therefore lives in {@code com.nonid.affinity.non.compat}, which is not declared as a
 * mixin package, as a public class.
 *
 * <p><b>Why a separate class at all.</b> The injections use {@code require = 0} so that a NeedsOfNature update
 * cannot break a player's game, and that flag makes a wrong anchor fail SILENTLY: the next-stage key would
 * simply stop granting affinity again, which is the very defect this adapter exists to fix. A descriptor string
 * nothing validates is therefore a liability. This class holds the two anchors and the checks for them, with no
 * Mixin dependency, so tests can drive them against NON's real compiled class as well as against synthetic
 * bytecode.
 *
 * <p><b>What the anchors are.</b>
 * <ul>
 *   <li>{@code maybeAdvanceStages(...)}: the tick-driven completion branch calls
 *       {@code scheduleMobMountRestores(ServerWorld, Collection, long)}, a call no other branch makes.</li>
 *   <li>{@code advanceStage(ServerWorld, UUID)}: its FINAL-stage branch calls
 *       {@code stopInstance(ServerWorld, UUID, boolean, StopReason)}. The same method also STEPS to an
 *       intermediate stage through {@code advanceToStageIndex}, which is deliberately not matched: a step is an
 *       ordinary running state, not a completion.</li>
 * </ul>
 *
 * <p>Both were verified against NON 1.5.11's real class (see {@code NonAdvanceStageAnchorTest}).
 */
public final class ServerAnimationControllerBytecode {

    /** Internal name of the class both anchors live in, in the intermediary namespace. */
    public static final String CONTROLLER_OWNER =
            "com/nonid/internal/animation/server/ServerAnimationController";

    /** The readable name of the same class, as the mixin config and the plugin spell it. */
    public static final String CONTROLLER_CLASS_NAME =
            "com.nonid.internal.animation.server.ServerAnimationController";

    /** Simple name of the tick-driven completion path method. */
    public static final String NATURAL_COMPLETION_METHOD = "maybeAdvanceStages";

    /**
     * The full descriptor of that method, as compiled in NON 1.5.11.
     *
     * <p>Not required by the Mixin annotation itself -- that one is matched by name, so a renamed parameter
     * cannot make the injection fail -- but the argument LAYOUT it describes is, because the handler reads the
     * instance id with {@code @Local(argsOnly = true, index = 1)}. A future NON that inserts or removes a
     * leading parameter would leave that index pointing at something else, and the injection would fail
     * silently. The plugin therefore checks this descriptor's index-1 type, and the test checks the real class
     * against it.
     */
    public static final String NATURAL_COMPLETION_METHOD_DESCRIPTOR =
            "(Lnet/minecraft/class_3218;Ljava/util/UUID;"
                    + "Lcom/nonid/internal/animation/server/ServerAnimationController$ActiveInstance;"
                    + "JLjava/util/List;Ljava/util/List;)Z";

    /** The argument index {@code @Local(argsOnly = true, index = 1)} reads: the instance id. */
    public static final int INSTANCE_ID_ARGUMENT_INDEX = 1;

    /**
     * The descriptor of the natural branch's STOP-EVENT dispatch, which the first injection wraps.
     *
     * <p>Chosen because it is the last step of that branch before the settlement runs: the event is dispatched
     * synchronously from inside this call, and the settlement reads the completion mark from its handler. An
     * earlier delivery anchored on the mount-restore scheduling call instead, which that branch performs EARLIER
     * (bytecode offset 155, against 239 for this one in NON 1.5.11) -- so the wrapper's {@code finally} cleared
     * the mark before the event could read it, and every natural completion settled to nothing.
     *
     * <p>The second parameter type is NON's {@code ActiveInstance}, which is package-private; the handler
     * receives it as a coerced {@code Object} and passes it straight back.
     */
    public static final String NATURAL_COMPLETION_ANCHOR_DESCRIPTOR =
            "(Ljava/util/UUID;"
                    + "Lcom/nonid/internal/animation/server/ServerAnimationController$ActiveInstance;)V";

    /** Simple name of the stop-event dispatch the natural path is anchored on. */
    public static final String STOP_EVENT_METHOD = "fireStopEvent";

    /** Simple name of the explicit advance entry. */
    public static final String ADVANCE_METHOD = "advanceStage";

    /**
     * The full descriptor of the explicit advance entry itself, which the mixin declares as its target method.
     *
     * <p>Checked as well, because a mixin whose {@code method} selector does not match injects nothing at all
     * and says nothing about it under {@code require = 0}.
     */
    public static final String ADVANCE_METHOD_DESCRIPTOR =
            "(Lnet/minecraft/class_3218;Ljava/util/UUID;)Z";

    /**
     * The descriptor of the final-stage stop call, in the intermediary namespace the mod is remapped to.
     *
     * <p>{@code class_3218} is the intermediary name of {@code ServerWorld}; the enum keeps its readable name
     * because it belongs to NON, which is not remapped. A literal is used because the {@code StopReason} enum's
     * type is package-private in NON and must not be touched at mixin-preparation time.
     */
    public static final String FINAL_STAGE_STOP_DESCRIPTOR =
            "(Lnet/minecraft/class_3218;Ljava/util/UUID;Z"
                    + "Lcom/nonid/internal/animation/server/ServerAnimationController$StopReason;)Z";

    private ServerAnimationControllerBytecode() {
    }

    /**
     * Whether this class really declares the tick-driven completion path the first injection targets.
     *
     * <p>Matched by NAME, mirroring the Mixin annotation, which also matches by name: a name-only lookup here
     * cannot disagree with the injection it verifies. The SIGNATURE is not ignored, though -- see
     * {@link #hasInstanceIdAtExpectedArgumentIndex}.
     */
    public static boolean hasNaturalCompletionMethod(org.objectweb.asm.tree.ClassNode controller) {
        return findMethod(controller, NATURAL_COMPLETION_METHOD) != null;
    }

    /**
     * Whether the completion path's argument layout still puts the instance id where the handler reads it.
     *
     * <p>This is the check that a name-only verification misses. The handler takes the instance id from
     * {@code @Local(argsOnly = true, index = 1)}; if a future NON adds or drops a leading parameter, that index
     * holds a different value and the injection fails silently under {@code require = 0} -- the completion would
     * simply stop granting affinity, with nothing in the log. Reading the descriptor's index-1 type is what
     * turns that into a reported ERROR.
     */
    public static boolean hasInstanceIdAtExpectedArgumentIndex(org.objectweb.asm.tree.ClassNode controller) {
        MethodNode method = findMethod(controller, NATURAL_COMPLETION_METHOD);
        return method != null && argumentType(method.desc, INSTANCE_ID_ARGUMENT_INDEX)
                .equals("Ljava/util/UUID;");
    }

    /**
     * The type descriptor of one argument of a method descriptor, or an empty string when it is not there.
     *
     * <p>Walks the parameter list by its own structure rather than by splitting on semicolons: an array or a
     * nested object type would make a naive split report the wrong argument, and a wrong answer here is what
     * the check exists to prevent.
     */
    public static String argumentType(String methodDescriptor, int argumentIndex) {
        if (methodDescriptor == null || argumentIndex < 0 || methodDescriptor.length() < 3
                || methodDescriptor.charAt(0) != '(') {
            return "";
        }
        int cursor = 1;
        int index = 0;
        while (cursor < methodDescriptor.length() && methodDescriptor.charAt(cursor) != ')') {
            int start = cursor;
            while (methodDescriptor.charAt(cursor) == '[') {
                cursor++;
            }
            char kind = methodDescriptor.charAt(cursor);
            if (kind == 'L') {
                cursor = methodDescriptor.indexOf(';', cursor);
                if (cursor < 0) {
                    return "";
                }
                cursor++;
            } else if (kind == 'T') {
                // A type variable also ends with a semicolon.
                cursor = methodDescriptor.indexOf(';', cursor);
                if (cursor < 0) {
                    return "";
                }
                cursor++;
            } else {
                cursor++;
            }
            if (index == argumentIndex) {
                return methodDescriptor.substring(start, cursor);
            }
            index++;
        }
        return "";
    }

    /**
     * Whether the tick-driven completion branch really contains the STOP-EVENT dispatch the injection wraps.
     *
     * <p>This is the anchor whose scope contains the settlement's read of the mark. A check for an earlier call
     * in the same branch would report success while the injection silently cleared the mark too soon -- which is
     * exactly the defect that shipped once, so the check is pinned to the dispatch itself.
     */
    public static boolean hasNaturalCompletionAnchor(org.objectweb.asm.tree.ClassNode controller) {
        MethodNode method = findMethod(controller, NATURAL_COMPLETION_METHOD);
        return containsCall(method, CONTROLLER_OWNER, STOP_EVENT_METHOD,
                NATURAL_COMPLETION_ANCHOR_DESCRIPTOR);
    }

    /**
     * Whether this class really declares the explicit advance entry with the descriptor the mixin targets.
     */
    public static boolean hasAdvanceMethod(org.objectweb.asm.tree.ClassNode controller) {
        return findMethodWithDescriptor(controller, ADVANCE_METHOD, ADVANCE_METHOD_DESCRIPTOR) != null;
    }

    /**
     * Whether the explicit advance entry really contains the final-stage stop call the second injection anchors
     * on -- checked INCLUDING the owning class, because the injection target names that owner and a lookalike
     * call on some other class would not be injected at all.
     */
    public static boolean containsStopInstanceInvocation(MethodNode method) {
        return containsCall(method, CONTROLLER_OWNER, "stopInstance", FINAL_STAGE_STOP_DESCRIPTOR);
    }

    /** Convenience used by the plugin: the advance entry's body, or null when the class does not declare it. */
    public static MethodNode advanceMethod(org.objectweb.asm.tree.ClassNode controller) {
        return findMethodWithDescriptor(controller, ADVANCE_METHOD, ADVANCE_METHOD_DESCRIPTOR);
    }

    /** The first method with this name, whatever its descriptor, or null when there is none. */
    public static MethodNode findMethod(org.objectweb.asm.tree.ClassNode owner, String name) {
        if (owner == null || owner.methods == null) {
            return null;
        }
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name)) {
                return method;
            }
        }
        return null;
    }

    /** The first method with this exact name and descriptor, or null when there is none. */
    public static MethodNode findMethodWithDescriptor(org.objectweb.asm.tree.ClassNode owner, String name,
                                                      String descriptor) {
        if (owner == null || owner.methods == null) {
            return null;
        }
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name) && descriptor.equals(normalizeDescriptor(method.desc))) {
                return method;
            }
        }
        return null;
    }

    /**
     * Whether this method body contains a call to {@code name} with {@code descriptor}, on {@code callOwner}.
     *
     * <p>Reads the ASM instruction list Mixin already hands over, so it resolves no class and is safe to run
     * before the game bootstraps. The opcode is deliberately not compared: Mixin's {@code @At("INVOKE")} matches
     * an invocation whatever its form, and NON calls these ones {@code static}.
     */
    public static boolean containsCall(MethodNode method, String callOwner, String name, String descriptor) {
        if (method == null || method.instructions == null) {
            return false;
        }
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && callOwner.equals(call.owner)
                    && name.equals(call.name)
                    && descriptor.equals(normalizeDescriptor(call.desc))) {
                return true;
            }
        }
        return false;
    }

    /** Development uses named Minecraft classes; production uses intermediary classes. */
    private static String normalizeDescriptor(String descriptor) {
        return descriptor.replace("Lnet/minecraft/server/world/ServerWorld;", "Lnet/minecraft/class_3218;");
    }
}
