package com.nonid.affinity.gift;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/** Approved independent channels. Distinct enchanted books are distinct equal-probability entries. */
public final class GiftProductionTable {
    public record Species(String habit, List<GiftContent.Reward> items) {}
    public static final Set<String> ENVIRONMENTS = Set.of("normal", "plains", "forest", "taiga", "snow", "desert", "jungle", "swamp", "ocean", "river", "cave", "mushroom", "pale_garden", "nether", "end");
    public static final Set<String> STRUCTURES = Set.of("none", "ancient_city", "end_city", "monument", "trial_chambers");
    private static final int[][] QUANTITY = {{70,25,5},{55,35,10},{40,40,20},{25,45,30}};
    private static final double[][] CHANCES = {{.15,.20,.03,.00005},{.20,.25,.05,.0001},{.25,.30,.08,.0002},{.30,.35,.12,.0005}};
    private static final Map<String, Species> SPECIES = new LinkedHashMap<>();
    private static final List<GiftContent.Reward> UTILITY, ULTIMATE;
    static {
        try (var input = GiftProductionTable.class.getResourceAsStream("/data/noncreatureaffinity/gifts/production.json")) {
            if (input == null) throw new IllegalStateException("Missing production gift tables");
            var data = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var row : data.getAsJsonObject("species").entrySet()) {
                var entry = row.getValue().getAsJsonObject();
                SPECIES.put("minecraft:"+row.getKey(), new Species(entry.get("habit").getAsString(), entries(entry.getAsJsonArray("items"))));
            }
            UTILITY = entries(data.getAsJsonArray("utility")); ULTIMATE = entries(data.getAsJsonArray("ultimate"));
            if (ULTIMATE.size() != 35 || SPECIES.isEmpty()) throw new IllegalStateException("Invalid production tables");
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    private GiftProductionTable() {}
    private static List<GiftContent.Reward> entries(JsonArray array) {
        var result = new ArrayList<GiftContent.Reward>();
        for (var value : array) {
            String id = value.getAsString();
            if (id.startsWith("book:")) {
                var parts = id.split(":");
                result.add(new GiftContent.Reward("minecraft:enchanted_book",1,"minecraft:"+parts[1],Integer.parseInt(parts[2])));
            } else result.add(new GiftContent.Reward("minecraft:"+id,1));
        }
        if (result.isEmpty() || new HashSet<>(result).size() != result.size()) throw new IllegalArgumentException("Empty or duplicate gift pool");
        return List.copyOf(result);
    }
    private static List<GiftContent.Reward> items(String ids) {
        return Arrays.stream(ids.split(" ")).map(id -> new GiftContent.Reward("minecraft:"+id,1)).toList();
    }
    public static Map<String, Species> species() { return Collections.unmodifiableMap(SPECIES); }
    public static List<GiftContent.Reward> ultimate() { return ULTIMATE; }
    public static List<GiftContent.Reward> utility() { return UTILITY; }
    public static double chance(int tier, int channel) { return CHANCES[tier][channel]; }
    public static int quantity(int tier, int maximum, RandomGenerator random) {
        if (tier < 0 || tier > 3 || maximum != 3 && maximum != 6 && maximum != 12) throw new IllegalArgumentException("Invalid gift quantity band");
        int roll = random.nextInt(100), band = roll < QUANTITY[tier][0] ? 0 : roll < QUANTITY[tier][0]+QUANTITY[tier][1] ? 1 : 2;
        int width = maximum/3; return band*width + random.nextInt(width)+1;
    }
    public static List<GiftContent.Reward> environment(String habit, String biome) {
        if (!ENVIRONMENTS.contains(biome)) throw new IllegalArgumentException("Unknown gift environment");
        boolean water = habit.equals("aquatic") || habit.equals("undead_aquatic");
        boolean plants = habit.equals("grazer");
        if (biome.equals("end")) return items(plants ? "chorus_fruit chorus_flower" : "end_stone chorus_fruit obsidian");
        if (biome.equals("nether")) return items(plants ? "crimson_fungus warped_fungus nether_sprouts" : "quartz basalt blackstone crimson_fungus warped_fungus");
        if (water) return items(biome.equals("ocean") ? "kelp seagrass sand clay_ball prismarine_shard" : "seagrass clay_ball lily_pad");
        if (habit.equals("hunter")) return items(biome.equals("ocean") || biome.equals("river") ? "cod salmon" : biome.equals("snow") || biome.equals("taiga") ? "rabbit chicken sweet_berries" : "chicken rabbit mutton");
        return items(switch(biome) {
            case "desert" -> plants ? "cactus dead_bush" : "sand sandstone cactus dead_bush flint";
            case "jungle" -> "bamboo cocoa_beans melon_slice jungle_sapling";
            case "taiga" -> "sweet_berries spruce_sapling brown_mushroom";
            case "snow" -> plants ? "spruce_sapling wheat_seeds" : "snowball ice spruce_sapling";
            case "swamp" -> "lily_pad brown_mushroom red_mushroom mangrove_propagule";
            case "ocean", "river" -> plants ? "kelp seagrass" : "clay_ball sand kelp";
            case "cave" -> plants ? "glow_berries moss_block" : "coal iron_nugget flint glow_berries amethyst_shard";
            case "mushroom" -> "brown_mushroom red_mushroom";
            case "pale_garden" -> "pale_oak_sapling pale_moss_block pale_hanging_moss";
            case "forest" -> "oak_sapling birch_sapling brown_mushroom apple";
            default -> "wheat_seeds dandelion poppy oak_sapling";
        });
    }
    public static List<GiftContent.Reward> precious(String type, String habit, String biome, String structure) {
        var result = new ArrayList<GiftContent.Reward>();
        if (habit.equals("hunter") || habit.equals("collector")) result.addAll(items("rabbit_foot"));
        if (habit.equals("end") && !type.equals("minecraft:endermite") && biome.equals("end")) result.addAll(items("dragon_breath"));
        boolean collector = habit.equals("collector") || habit.equals("undead") || habit.equals("end") || type.equals("minecraft:warden");
        if (collector && structure.equals("ancient_city")) result.addAll(items("echo_shard silence_armor_trim_smithing_template"));
        if (collector && structure.equals("end_city")) result.addAll(items("spire_armor_trim_smithing_template"));
        if (Set.of("minecraft:dolphin","minecraft:guardian","minecraft:elder_guardian","minecraft:drowned").contains(type) && structure.equals("monument")) result.addAll(items("sponge tide_armor_trim_smithing_template"));
        if (collector && structure.equals("trial_chambers")) result.addAll(items("trial_key ominous_trial_key flow_armor_trim_smithing_template bolt_armor_trim_smithing_template"));
        return List.copyOf(result);
    }
    public record Feature(GiftContent.Reward reward, int maximum) {
        public Feature { if (maximum != 3 && maximum != 6 && maximum != 12) throw new IllegalArgumentException("Invalid feature range"); }
    }
    public static List<Feature> sheepFeature(String wool) {
        if (!Set.of("white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black").contains(wool)) throw new IllegalArgumentException("Invalid sheep color");
        return List.of(new Feature(new GiftContent.Reward("minecraft:"+wool+"_wool",1),6),
                new Feature(new GiftContent.Reward("minecraft:wheat",1),6),
                new Feature(new GiftContent.Reward("minecraft:dandelion",1),3),
                new Feature(new GiftContent.Reward("minecraft:poppy",1),3));
    }
    public static List<GiftContent.Reward> draw(String type, String biome, String structure, int tier,
            Predicate<GiftContent.Reward> registered, Predicate<GiftContent.Reward> single, RandomGenerator random) {
        var species = SPECIES.get(type);
        if (species == null) throw new IllegalArgumentException("Unsupported gift source");
        var feature = type.equals("minecraft:sheep") ? sheepFeature("white") : species.items().stream().map(r -> new Feature(r,12)).toList();
        return draw(type,biome,structure,tier,feature,registered,single,random);
    }
    public static List<GiftContent.Reward> draw(String type, String biome, String structure, int tier,
            List<Feature> feature, Predicate<GiftContent.Reward> registered, Predicate<GiftContent.Reward> single, RandomGenerator random) {
        var species = SPECIES.get(type);
        if (species == null || tier < 0 || tier > 3 || feature.isEmpty()) throw new IllegalArgumentException("Unsupported gift source");
        var rewards = new ArrayList<GiftContent.Reward>();
        var eligibleFeature = feature.stream().filter(f -> registered.test(f.reward())).toList();
        if (!eligibleFeature.isEmpty()) {
            var chosen = eligibleFeature.get(random.nextInt(eligibleFeature.size()));
            rewards.add(chosen.reward().withCount(single.test(chosen.reward()) ? 1 : quantity(tier,chosen.maximum(),random)));
        }
        if (rewards.isEmpty()) append(rewards, items("paper"), 12, tier, registered, single, random);
        var channels = List.of(UTILITY, environment(species.habit(),biome), precious(type,species.habit(),biome,structure), ULTIMATE);
        for (int i=0;i<channels.size();i++) if (random.nextDouble() < CHANCES[tier][i]) append(rewards,channels.get(i),i<2?6:3,tier,registered,single,random);
        return List.copyOf(rewards);
    }
    private static void append(List<GiftContent.Reward> into, List<GiftContent.Reward> pool, int maximum, int tier,
            Predicate<GiftContent.Reward> registered, Predicate<GiftContent.Reward> single, RandomGenerator random) {
        var available = pool.stream().filter(registered).toList(); if (available.isEmpty()) return;
        var reward = available.get(random.nextInt(available.size()));
        into.add(reward.withCount(!reward.enchantment().isEmpty() || single.test(reward) ? 1 : quantity(tier,maximum,random)));
    }
}
