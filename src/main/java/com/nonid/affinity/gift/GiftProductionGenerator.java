package com.nonid.affinity.gift;

import com.nonid.affinity.service.CompanionDirectory;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

/** Server-only production provider. No nearby-entity search, container scan or chunk request. */
public final class GiftProductionGenerator implements GiftService.Generator {
    public static final GiftProductionGenerator INSTANCE = new GiftProductionGenerator();
    private record Sample(ServerWorld world, net.minecraft.util.math.BlockPos position, int tick, String environment, String structure) {}
    private static final Map<UUID, Sample> SAMPLES = new HashMap<>();
    public static void reset() { SAMPLES.clear(); }
    public static void forget(UUID entity) { SAMPLES.remove(entity); }
    private GiftProductionGenerator() {}
    public static boolean supports(LivingEntity mob) {
        return GiftProductionTable.species().containsKey(Registries.ENTITY_TYPE.getId(mob.getType()).toString());
    }
    public static void advance(GiftLedger ledger, LivingEntity mob, BigInteger elapsed) {
        if (elapsed.signum() == 0 || !supports(mob) || !(mob.getEntityWorld() instanceof ServerWorld world)) return;
        var pos = mob.getBlockPos(); var chunk = world.getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4);
        if (chunk == null || !mob.isAlive() || mob.isRemoved() || world.getEntity(mob.getUuid()) != mob) return;
        String identity = CompanionDirectory.identityOf(mob); if (identity == null) return;
        String dimension = world.getRegistryKey().getValue().toString();
        var cached = SAMPLES.get(mob.getUuid());
        if (cached != null && cached.world() == world && cached.position().equals(pos)
                && world.getServer().getTicks()-cached.tick() >= 0 && world.getServer().getTicks()-cached.tick() < 20) {
            ledger.habitat(identity).advance(dimension+"|"+cached.environment()+"|none",elapsed);
            ledger.landmark(identity).advance(dimension+"|normal|"+cached.structure(),elapsed); return;
        }
        String biome = world.getBiome(pos).getKey().map(k -> k.getValue().getPath()).orElse("");
        String environment = classify(dimension, biome);
        ledger.habitat(identity).advance(dimension+"|"+environment+"|none",elapsed);
        String structure = "none";
        var registry = world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
        // Use only already-loaded start chunks referenced by this loaded chunk. No StructureAccessor lookup may load them.
        for (String name : List.of("ancient_city","end_city","monument","trial_chambers")) {
            var feature = registry.getOptionalValue(net.minecraft.util.Identifier.ofVanilla(name)); if (feature.isEmpty()) continue;
            var starts = new ArrayList<net.minecraft.structure.StructureStart>();
            var own = chunk.getStructureStart(feature.get()); if (own != null) starts.add(own);
            var references = chunk.getStructureReferences().get(feature.get());
            if (references != null) for (long reference : references) {
                var at = new ChunkPos(reference);
                var loaded = world.getChunkManager().getWorldChunk(at.x,at.z); if (loaded == null) continue;
                var start = loaded.getStructureStart(feature.get()); if (start != null) starts.add(start);
            }
            if (starts.stream().anyMatch(start -> start.hasChildren() && start.getChildren().stream().anyMatch(piece -> piece.getBoundingBox().contains(pos)))) { structure = name; break; }
        }
        SAMPLES.put(mob.getUuid(),new Sample(world,pos.toImmutable(),world.getServer().getTicks(),environment,structure));
        ledger.landmark(identity).advance(dimension+"|normal|"+structure,elapsed);
    }
    public static String classify(String dimension, String biome) {
        if (dimension.equals("minecraft:the_nether") || biome.contains("nether") || biome.contains("basalt") || biome.contains("soul_sand") || biome.contains("crimson") || biome.contains("warped")) return "nether";
        if (dimension.equals("minecraft:the_end") || biome.startsWith("end_") || biome.equals("the_end") || biome.equals("small_end_islands")) return "end";
        if (biome.contains("pale_garden")) return "pale_garden";
        if (biome.contains("ocean")) return "ocean";
        if (biome.contains("river")) return "river";
        if (biome.contains("mushroom")) return "mushroom";
        if (biome.contains("cave") || biome.equals("deep_dark")) return "cave";
        if (biome.contains("desert") || biome.contains("badlands")) return "desert";
        if (biome.contains("jungle")) return "jungle";
        if (biome.contains("swamp")) return "swamp";
        if (biome.contains("snow") || biome.contains("frozen") || biome.contains("ice") || biome.contains("peaks") || biome.contains("grove")) return "snow";
        if (biome.contains("taiga")) return "taiga";
        if (biome.contains("forest") || biome.contains("cherry")) return "forest";
        return "plains";
    }
    @Override public GiftContent prepare(LivingEntity mob, UUID viewer, int tier, long clock) {
        if (!(mob.getEntityWorld() instanceof ServerWorld world) || !supports(mob) || !mob.isAlive() || mob.isRemoved()
                || world.getEntity(mob.getUuid()) != mob) throw new IllegalArgumentException("Unavailable production gift source");
        var affinity = mob instanceof com.nonid.affinity.data.AffinityHolder holder ? holder.peekAffinityData() : null;
        var relation = affinity == null ? null : affinity.get(viewer);
        if (world.getServer().getPlayerManager().getPlayer(viewer) == null || relation == null
                || com.nonid.affinity.service.RelationshipBond.tier(relation.getBond(),relation.getAffinity()) != tier
                || com.nonid.affinity.service.ReplayIsolation.protects(mob.getUuid(),viewer))
            throw new IllegalArgumentException("Unverified production gift relationship or tier");
        String identity = CompanionDirectory.identityOf(mob); var ledger = GiftService.peek(world.getServer());
        if (identity == null || ledger == null) throw new IllegalStateException("Missing authoritative gift ledger");
        String home = ledger.habitat(identity).stable(), landmark = ledger.landmark(identity).stable();
        String biome = home.isEmpty() ? "normal" : home.split("\\|")[1];
        String structure = landmark.isEmpty() || home.isEmpty() || !home.split("\\|")[0].equals(landmark.split("\\|")[0]) ? "none" : landmark.split("\\|")[2];
        String type = Registries.ENTITY_TYPE.getId(mob.getType()).toString();
        java.util.random.RandomGenerator random = () -> world.random.nextLong();
        var features = mob instanceof net.minecraft.entity.passive.SheepEntity sheep
                ? GiftProductionTable.sheepFeature(sheep.getColor().asString())
                : GiftProductionTable.species().get(type).items().stream().map(r -> new GiftProductionTable.Feature(r,12)).toList();
        var rewards = GiftProductionTable.draw(type,biome,structure,tier,features,
                reward -> GiftRewardStacks.create(world.getServer(),reward).isPresent(),
                reward -> GiftRewardStacks.create(world.getServer(),reward).orElseThrow().getMaxCount()==1,random);
        if (rewards.isEmpty()) throw new IllegalStateException("All ordinary production gift items unavailable");
        String name = mob.hasCustomName() ? mob.getCustomName().getString() : "";
        if (name.length()>256) { int end=256; if(Character.isHighSurrogate(name.charAt(end-1))) end--; name=name.substring(0,end); }
        return new GiftContent(UUID.randomUUID(),viewer,identity,name,type,tier,tier,clock,rewards,
                List.of(new GiftContent.Reward("minecraft:paper",1)));
    }
}
