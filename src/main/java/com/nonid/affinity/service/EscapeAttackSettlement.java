package com.nonid.affinity.service;

import com.nonid.affinity.data.PlayerAffinity;
import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.IntConsumer;

/** An actual attack is excused only by an escape charge that really settled. */
public final class EscapeAttackSettlement {
    private final UUID id = UUID.randomUUID();
    private final PlayerAffinity relation;
    private final int min, max;
    private final HashSet<UUID> waiting;
    private final IntConsumer apply;
    private boolean recorded, finished, paid;
    private int loss;

    public EscapeAttackSettlement(PlayerAffinity relation, int min, int max,
                                 Collection<UUID> sessions, IntConsumer apply) {
        this.relation = relation; this.min = min; this.max = max;
        this.waiting = new HashSet<>(sessions); this.apply = apply;
        finished = waiting.isEmpty();
    }
    public void record(int amount) {
        if (recorded) return;
        recorded = true; loss = Math.max(0, Math.min(2, amount));
        if (loss == 0 || paid) return;
        if (finished) apply.accept(loss);
        else relation.deferEscapeAttack(id, loss, min, max);
    }
    public void settled(UUID session, boolean actuallyPaid) {
        if (finished || !waiting.remove(session)) return;
        paid |= actuallyPaid;
        if (paid || waiting.isEmpty()) {
            finished = true;
            boolean pending = relation.removeDeferredEscapeAttack(id);
            if (pending && !paid) apply.accept(loss);
        }
    }
}
