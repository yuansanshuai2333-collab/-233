package com.nonid.affinity.data;

import java.util.*;
import net.minecraft.nbt.NbtCompound;

/** One viewer's recorded evidence, independent of current affinity and entity loading. */
public final class FamilyGraph {
    public enum Evidence { NON_BIRTH, SAVED_BIRTH_PARENT, VANILLA_BIRTH }
    public enum NameKind { UNKNOWN, DEFAULT_TYPE, CUSTOM, PLAYER }
    public record Node(String id, String name, String type, int gender, NameKind nameKind) {
        public Node(String id,String name,String type,int gender){this(id,name,type,gender,NameKind.UNKNOWN);}
        public Node {
            if (!validId(id) || name == null || type == null || name.length() > 256 || type.length() > 256)
                throw new IllegalArgumentException("Invalid family node");
            gender = gender == 1 || gender == 2 ? gender : 0;
            nameKind = nameKind == null ? NameKind.UNKNOWN : nameKind;
        }
        public boolean player() { return id.startsWith("player:"); }
    }
    public record Edge(String parent, String child, Evidence evidence) {}
    /** Signed generations: negative ancestors, positive descendants; zero is the root itself. */
    public record Relative(Node node, int generations) {}
    public record Page(List<Relative> nodes, int total, int offset, boolean hasMore) {
        public Page { nodes = List.copyOf(nodes); }
    }
    private final NavigableMap<String, Node> nodes = new TreeMap<>();
    private final Map<String, NavigableMap<String, Evidence>> parents = new HashMap<>();
    private final Map<String, NavigableSet<String>> children = new HashMap<>();

    public static boolean validId(String id) {
        if (id == null || id.length() > 80) return false;
        int colon = id.indexOf(':');
        if (colon < 0 || !Set.of("player", "entity", "heart").contains(id.substring(0, colon))) return false;
        try { return UUID.fromString(id.substring(colon + 1)).toString().equals(id.substring(colon + 1)); }
        catch (IllegalArgumentException invalid) { return false; }
    }
    public Node get(String id) { return nodes.get(id); }
    public boolean put(Node node) { return !node.equals(nodes.put(node.id(), node)); }
    public int size() { return nodes.size(); }
    public List<Edge> edges() {
        var result = new ArrayList<Edge>();
        new TreeMap<>(parents).forEach((child, links) -> links.forEach((parent, source) -> result.add(new Edge(parent, child, source))));
        return List.copyOf(result);
    }
    /** Reject cycles and unrecorded endpoints; ownership/proximity never call this method as evidence. */
    public boolean link(String parent, String child, Evidence evidence) {
        if (evidence == null || !nodes.containsKey(parent) || !nodes.containsKey(child) || parent.equals(child)) return false;
        if (parents.getOrDefault(child, Collections.emptyNavigableMap()).containsKey(parent)) return false;
        if (reachable(child, parent, children)) return false;
        parents.computeIfAbsent(child, key -> new TreeMap<>()).put(parent, evidence);
        children.computeIfAbsent(parent, key -> new TreeSet<>()).add(child);
        return true;
    }
    private static boolean reachable(String start, String target, Map<String, ? extends Set<String>> links) {
        var queue = new ArrayDeque<String>(); var visited = new HashSet<String>(); queue.add(start);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst(); if (!visited.add(id)) continue;
            if (id.equals(target)) return true;
            var next = links.get(id); if (next != null) queue.addAll(next);
        }
        return false;
    }
    public Integer generations(String root, String target) {
        if (!nodes.containsKey(root) || !nodes.containsKey(target)) return null;
        if (root.equals(target)) return 0;
        var up = walk(root, false); if (up.containsKey(target)) return -up.get(target);
        return walk(root, true).get(target);
    }
    public Set<String> relatedIds(String root) {
        if (!nodes.containsKey(root)) return Set.of();
        var ids = new HashSet<>(walk(root, false).keySet()); ids.addAll(walk(root, true).keySet()); return Set.copyOf(ids);
    }
    private Map<String, Integer> walk(String root, boolean down) {
        var depth = new LinkedHashMap<String, Integer>(); var queue = new ArrayDeque<String>();
        depth.put(root, 0); queue.add(root);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            Collection<String> next = down ? children.getOrDefault(id, Collections.emptyNavigableSet())
                    : parents.getOrDefault(id, Collections.emptyNavigableMap()).navigableKeySet();
            for (String candidate : next) if (!depth.containsKey(candidate)) {
                depth.put(candidate, depth.get(id) + 1); queue.addLast(candidate);
            }
        }
        return depth;
    }
    /** Technical page bounds do not trim the underlying graph or silently invent unknown branches. */
    public Page page(String root, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 64) throw new IllegalArgumentException("Invalid family page");
        if (!nodes.containsKey(root)) return new Page(List.of(), 0, offset, false);
        var relatives = new ArrayList<Relative>(); relatives.add(new Relative(nodes.get(root), 0));
        var up = walk(root, false); var down = walk(root, true);
        nodes.forEach((id, node) -> {
            if (!id.equals(root)) {
                if (up.containsKey(id)) relatives.add(new Relative(node, -up.get(id)));
                else if (down.containsKey(id)) relatives.add(new Relative(node, down.get(id)));
            }
        });
        int end = (int)Math.min(relatives.size(), (long)offset + limit);
        return new Page(offset >= relatives.size() ? List.of() : relatives.subList(offset, end), relatives.size(), offset, end < relatives.size());
    }
    public NbtCompound toNbt() {
        var root = new NbtCompound(); root.putInt("schema", 1);
        var savedNodes = new NbtCompound();
        nodes.forEach((id, node) -> {
            var n = new NbtCompound(); n.putString("name", node.name()); n.putString("type", node.type()); n.putInt("gender", node.gender()); n.putString("name_kind",node.nameKind().name()); savedNodes.put(id, n);
        });
        root.put("nodes", savedNodes); var savedEdges = new NbtCompound();
        parents.forEach((child, links) -> {
            var n = new NbtCompound(); links.forEach((parent, source) -> n.putString(parent, source.name())); savedEdges.put(child, n);
        });
        root.put("parents", savedEdges); return root;
    }
    private static NameKind nameKind(String value){
        return switch(value){case "DEFAULT_TYPE"->NameKind.DEFAULT_TYPE;case "CUSTOM"->NameKind.CUSTOM;case "PLAYER"->NameKind.PLAYER;default->NameKind.UNKNOWN;};
    }
    public static FamilyGraph fromNbt(NbtCompound root) {
        var graph = new FamilyGraph(); var savedNodes = root.getCompoundOrEmpty("nodes"); int invalid = 0;
        for (String id : new TreeSet<>(savedNodes.getKeys())) {
            var n = savedNodes.getCompoundOrEmpty(id);
            try { graph.put(new Node(id, n.getString("name", ""), n.getString("type", ""), n.getInt("gender", 0), nameKind(n.getString("name_kind","UNKNOWN")))); }
            catch (IllegalArgumentException bad) { invalid++; }
        }
        var savedEdges = root.getCompoundOrEmpty("parents");
        for (String child : new TreeSet<>(savedEdges.getKeys())) {
            var links = savedEdges.getCompoundOrEmpty(child);
            for (String parent : new TreeSet<>(links.getKeys())) try {
                if (!graph.link(parent, child, Evidence.valueOf(links.getString(parent, "")))) invalid++;
            } catch (IllegalArgumentException bad) { invalid++; }
        }
        if (invalid > 0) com.nonid.affinity.NonCreatureAffinity.LOGGER.warn("Ignored {} malformed or cyclic family facts; no inferred kinship was added", invalid);
        return graph;
    }
}
