package com.nonid.affinity.non;

import com.nonid.NeedsOfNature.PregnancyVariantData;
import com.nonid.affinity.data.BirthIdentity;
import com.nonid.affinity.data.FamilyGraph;
import com.nonid.affinity.service.CompanionArchive;
import com.nonid.affinity.service.ReplayIsolation;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/** Optional NoN adapter. Carries the selected donor, not the most recent nearby creature. */
public final class NonBirthIdentity {
    private static final ThreadLocal<BirthIdentity.Birth> CURRENT = new ThreadLocal<>();
    private record Snapshot(ServerWorld world, UUID instance, Identifier source, BirthIdentity.Birth birth) {}
    private record EggBatch(ServerWorld world, Identifier source, BirthIdentity.Birth birth) {}
    private record EggGrant(Entity egg, UUID mother, Identifier source, BirthIdentity.Birth birth) {}
    private static final ThreadLocal<Snapshot> SNAPSHOT = new ThreadLocal<>();
    private static final ThreadLocal<EggBatch> EGG_BATCH = new ThreadLocal<>();
    private static final ThreadLocal<EggGrant> EGG = new ThreadLocal<>();
    private NonBirthIdentity() {}

    public static PregnancyVariantData capture(ServerWorld world, Entity donor, PregnancyVariantData appearance) {
        if (world == null || donor == null || donor.getEntityWorld() != world) return appearance;
        var components = new LinkedHashMap<String, String>();
        if (appearance != null) components.putAll(appearance.components());
        components.put(BirthIdentity.COMPONENT, BirthIdentity.encodeDonor(new BirthIdentity.Donor(
                CompanionArchive.identityNode(donor), ReplayIsolation.participantsWith(donor.getUuid()))));
        return new PregnancyVariantData(components, appearance == null ? null : appearance.horseMarkingIndex());
    }
    public static BirthIdentity.Birth evidence(ServerWorld world, UUID mother, Identifier source, PregnancyVariantData variant) {
        if (world == null || mother == null) return null;
        var player = world.getServer().getPlayerManager().getPlayer(mother);
        var node = player == null ? new FamilyGraph.Node("player:" + mother, "", "minecraft:player", 0)
                : CompanionArchive.identityNode(player);
        var donor = variant == null ? null : BirthIdentity.decodeDonor(variant.components().get(BirthIdentity.COMPONENT));
        return BirthIdentity.birth(node, source == null ? "" : source.toString(), donor);
    }
    public static BirthIdentity.Birth current() { return CURRENT.get(); }
    public static Scope enter(BirthIdentity.Birth birth) { return new Scope(birth); }
    public static Scope enterSnapshot(ServerWorld world, UUID instance, Identifier source, BirthIdentity.Birth birth) {
        var scope = new Scope(birth); SNAPSHOT.set(new Snapshot(world, instance, source, birth)); return scope;
    }
    public static Scope enterOutcome(ServerWorld world, UUID instance, UUID mother, Identifier source, PregnancyVariantData variant) {
        var snapshot=SNAPSHOT.get();
        var birth=evidence(world,mother,source,variant);
        // Only the exact native hatch invocation may restore identity stripped by NoN's
        // cross-species appearance rule. Arbitrary same-mother scopes are not evidence.
        if (!hasIdentity(variant) && snapshot!=null && snapshot.world()==world
                && java.util.Objects.equals(snapshot.instance(),instance)
                && java.util.Objects.equals(snapshot.source(),source)
                && snapshot.birth()!=null && snapshot.birth().viewer().equals(mother)) birth=snapshot.birth();
        var scope=new Scope(birth); EGG_BATCH.set(new EggBatch(world,source,birth)); return scope;
    }
    private static boolean hasIdentity(PregnancyVariantData variant) {
        return variant!=null && variant.components().containsKey(BirthIdentity.COMPONENT);
    }
    public static BirthIdentity.Birth eggEvidence(Entity egg, ServerWorld world, UUID mother, Identifier source, PregnancyVariantData variant) {
        if(hasIdentity(variant)) return evidence(world,mother,source,variant);
        var grant=EGG.get();
        if(grant!=null && grant.egg()==egg && java.util.Objects.equals(grant.mother(),mother)
                && java.util.Objects.equals(grant.source(),source) && grant.birth()!=null
                && grant.birth().viewer().equals(mother)) return grant.birth();
        return evidence(world,mother,source,variant);
    }
    public static EggScope grantEgg(Entity egg, UUID mother, Identifier source) {
        var batch=EGG_BATCH.get();
        var birth=batch!=null && batch.world()==egg.getEntityWorld() ? batch.birth() : null;
        if(birth!=null && !java.util.Objects.equals(batch.source(),source))
            birth=new BirthIdentity.Birth(birth.mother(),null,birth.isolated());
        return new EggScope(new EggGrant(egg,mother,source,birth));
    }
    public static final class EggScope implements AutoCloseable {
        private final EggGrant previous;
        private EggScope(EggGrant grant) {previous=EGG.get();EGG.set(grant);}
        @Override public void close(){if(previous==null)EGG.remove();else EGG.set(previous);}
    }
    public static final class Scope implements AutoCloseable {
        private final BirthIdentity.Birth previous;
        private final Snapshot previousSnapshot;
        private final EggBatch previousBatch;
        private Scope(BirthIdentity.Birth birth) {
            previous = CURRENT.get(); previousSnapshot=SNAPSHOT.get();previousBatch=EGG_BATCH.get();
            SNAPSHOT.remove();EGG_BATCH.remove();
            if (birth == null) CURRENT.remove(); else CURRENT.set(birth);
        }
        @Override public void close() {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
            if(previousSnapshot==null)SNAPSHOT.remove();else SNAPSHOT.set(previousSnapshot);
            if(previousBatch==null)EGG_BATCH.remove();else EGG_BATCH.set(previousBatch);
        }
    }
}
