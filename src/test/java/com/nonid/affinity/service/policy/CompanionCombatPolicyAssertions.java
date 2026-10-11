package com.nonid.affinity.service.policy;

import java.util.UUID;
import static com.nonid.affinity.service.policy.CompanionCombatPolicy.*;

/**
 * Handover assertion harness, kept verbatim from the handover policy package.
 *
 * <p>Standalone regression suite: Java 21, no game or test framework required. Run via
 * {@link CompanionCombatPolicySpecTest}. These 70 assertions are NOT counted with the project's
 * JUnit cases: the handover README requires them to stay separate, and they only prove the pure
 * policy, never game integration.
 */
public final class CompanionCombatPolicyAssertions {
    private static final UUID OWNER = new UUID(0, 1), PET = new UUID(0, 2), TARGET = new UUID(0, 3);
    private static final String WORLD = "minecraft:overworld";
    private static final Eligibility OK = new Eligibility(true, true, false, true, true, true, true);
    private static int checks;
    private static Context context(Mode mode) {
        return new Context(mode, OWNER, PET, WORLD, 200, 10, 100, true, false);
    }
    private static Evidence evidence(Trigger trigger, boolean hostile) {
        return new Evidence(trigger, trigger == Trigger.SELF_DEFENCE ? PET : OWNER,
                TARGET, WORLD, 150, 11, hostile, hostile);
    }
    private static void expect(String name, Object wanted, Object actual) {
        checks++;
        if (!wanted.equals(actual)) throw new AssertionError(name + ": expected " + wanted + ", got " + actual);
    }
    private static void rejected(String name, Context c, Evidence e, Eligibility t, Reason reason) {
        Decision d = decide(c, e, t);
        expect(name + " action", Action.IGNORE, d.action());
        expect(name + " reason", reason, d.reason());
    }
    public static void main(String[] args) {
        // Each row: owner attacks neutral, owner attacks hostile, owner hurt,
        // live hostile scan, self defence. Explicit table, not implementation-derived expectations.
        Action E = Action.ENGAGE, I = Action.IGNORE, D = Action.DEFEND_IN_PLACE;
        Action[][] expected = {{E,E,E,I,E},{I,E,E,I,E},{I,I,I,I,D},{E,E,E,E,E}};
        Mode[] modes = {Mode.FOLLOW, Mode.WANDER, Mode.WAIT, Mode.ATTACK};
        Evidence[] events = {evidence(Trigger.OWNER_ATTACK,false), evidence(Trigger.OWNER_ATTACK,true),
                evidence(Trigger.OWNER_HURT,false), evidence(Trigger.HOSTILE_TO_OWNER,true),
                evidence(Trigger.SELF_DEFENCE,false)};
        for (int m=0;m<modes.length;m++) for(int n=0;n<events.length;n++)
            expect("matrix " + modes[m] + "/" + n, expected[m][n], decide(context(modes[m]),events[n],OK).action());

        Context c = context(Mode.FOLLOW);
        Evidence hit = evidence(Trigger.OWNER_ATTACK,false);
        rejected("wrong owner",c,new Evidence(Trigger.OWNER_ATTACK,new UUID(0,9),TARGET,WORLD,150,11,false,false),OK,Reason.WRONG_PRINCIPAL);
        rejected("wrong self defence principal",context(Mode.WAIT),new Evidence(Trigger.SELF_DEFENCE,OWNER,TARGET,WORLD,150,11,false,false),OK,Reason.WRONG_PRINCIPAL);
        rejected("old mode event",c,new Evidence(Trigger.OWNER_ATTACK,OWNER,TARGET,WORLD,150,10,false,false),OK,Reason.BEFORE_MODE_ENTRY);
        rejected("expired",c,new Evidence(Trigger.OWNER_ATTACK,OWNER,TARGET,WORLD,99,11,false,false),OK,Reason.STALE_EVENT);
        expect("exact TTL boundary",E,decide(c,new Evidence(Trigger.OWNER_ATTACK,OWNER,TARGET,WORLD,100,11,false,false),OK).action());
        rejected("future event",c,new Evidence(Trigger.OWNER_ATTACK,OWNER,TARGET,WORLD,201,11,false,false),OK,Reason.STALE_EVENT);
        rejected("cross dimension evidence",c,new Evidence(Trigger.OWNER_ATTACK,OWNER,TARGET,"minecraft:the_nether",150,11,false,false),OK,Reason.WRONG_WORLD);
        rejected("target unloaded elsewhere",c,hit,new Eligibility(true,false,false,true,true,true,true),Reason.WRONG_WORLD);
        rejected("dead",c,hit,new Eligibility(false,true,false,true,true,true,true),Reason.INVALID_TARGET);
        rejected("protected",c,hit,new Eligibility(true,true,true,true,true,true,true),Reason.PROTECTED_TARGET);
        rejected("owner target",c,new Evidence(Trigger.OWNER_ATTACK,OWNER,OWNER,WORLD,150,11,false,false),OK,Reason.PROTECTED_TARGET);
        rejected("self target",c,new Evidence(Trigger.OWNER_ATTACK,OWNER,PET,WORLD,150,11,false,false),OK,Reason.PROTECTED_TARGET);
        rejected("no attack executor",c,hit,new Eligibility(true,true,false,false,true,true,true),Reason.UNSUPPORTED_COMBAT);
        rejected("out of response range",c,hit,new Eligibility(true,true,false,true,false,true,true),Reason.OUT_OF_RANGE);
        rejected("out of pursuit limit",c,hit,new Eligibility(true,true,false,true,true,false,true),Reason.OUT_OF_RANGE);
        rejected("owner absent",new Context(Mode.FOLLOW,OWNER,PET,WORLD,200,10,100,false,false),hit,OK,Reason.OWNER_UNAVAILABLE);
        rejected("occupied",new Context(Mode.FOLLOW,OWNER,PET,WORLD,200,10,100,true,true),hit,OK,Reason.OCCUPIED);
        rejected("hostility gone",context(Mode.ATTACK),evidence(Trigger.HOSTILE_TO_OWNER,false),OK,Reason.NO_CURRENT_HOSTILITY);
        rejected("wait attacker outside reach",context(Mode.WAIT),evidence(Trigger.SELF_DEFENCE,false),
                new Eligibility(true,true,false,true,true,true,false),Reason.OUT_OF_STATIONARY_REACH);
        expect("wait defends even with owner far/offline",D,decide(
                new Context(Mode.WAIT,OWNER,PET,WORLD,200,10,100,false,false),evidence(Trigger.SELF_DEFENCE,false),
                new Eligibility(true,true,false,true,false,false,true)).action());
        expect("wait rejects replayed owner hit",I,decide(context(Mode.WAIT),hit,OK).action());
        expect("hostility AFTER hitting neutral does not qualify wander",I,decide(context(Mode.WANDER),
                new Evidence(Trigger.OWNER_ATTACK,OWNER,TARGET,WORLD,150,11,false,true),OK).action());
        // ATTACK also assists owner hits, per the updated user requirement.
        expect("attack sees subsequent real hostility",E,decide(context(Mode.ATTACK),evidence(Trigger.HOSTILE_TO_OWNER,true),OK).action());
        expect("follow movement",Movement.FOLLOW_OWNER,defaultMovement(Mode.FOLLOW));
        expect("wander movement",Movement.NATURAL_ROAM,defaultMovement(Mode.WANDER));
        expect("wait movement",Movement.HOLD_POSITION,defaultMovement(Mode.WAIT));
        expect("attack movement",Movement.FOLLOW_OWNER,defaultMovement(Mode.ATTACK));
        Mode mode=Mode.FOLLOW;
        for(Mode next:new Mode[]{Mode.WANDER,Mode.WAIT,Mode.ATTACK,Mode.FOLLOW}) {
            mode=nextMode(mode); expect("cycle",next,mode);
        }
        // Clock arithmetic remains valid at the upper bound (no overflowing now+TTL).
        expect("clock maximum",E,decide(new Context(Mode.FOLLOW,OWNER,PET,WORLD,Long.MAX_VALUE,0,100,true,false),
                new Evidence(Trigger.OWNER_ATTACK,OWNER,TARGET,WORLD,Long.MAX_VALUE-100,1,false,false),OK).action());
        System.out.println("PASS: " + checks + " assertions; pure policy only, no game integration tested.");
    }
}
