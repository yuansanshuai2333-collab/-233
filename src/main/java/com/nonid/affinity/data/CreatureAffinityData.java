package com.nonid.affinity.data;

import com.nonid.affinity.NonCreatureAffinity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

/**
 * Affinity data attached to one creature: a map of "player -> relation".
 *
 * <p>Player UUIDs are stored as strings so they survive a save/load round trip.
 * Relations are never automatically evicted: adding a new player must not discard
 * that player's update or another player's history.
 */
public final class CreatureAffinityData {

    /** Historical limit, retained for binary compatibility only. No longer enforced. */
    @Deprecated
    public static final int MAX_ENTRIES = 64;

    private final Map<String, PlayerAffinity> entries = new LinkedHashMap<>();
    /** Generation of this creature: 0 = naturally spawned, 1+ = born from a player bond. */
    private int generation;

    /**
     * The player this creature is bonded to as a companion, or null when unbound.
     *
     * <p>Entity-level rather than per-relation: relations with several players can coexist, but a
     * creature can only follow one, so per-relation flags could contradict each other.
     */
    private String companionOwnerUuid;
    private UUID birthParent;

    public UUID getBirthParent() { return birthParent; }

    /** First proven birth only. Repeated callbacks cannot reset affinity or overwrite parentage. */
    public boolean recordBirthParent(UUID parent) {
        if (parent == null || birthParent != null) return false;
        birthParent = parent;
        return true;
    }

    /**
     * How the companion moves.
     *
     * <p>Null means no mode has ever been CHOSEN for this creature. {@link #getCompanionMode()} still
     * reports FOLLOW for that state, because following is what an unbound creature does; the distinction
     * itself is kept because the two states must be treated differently: a mode the player picked may never
     * be replaced, while a creature that never had one may take the mode its own state implies -- a pet that
     * is sitting belongs in WAIT.
     *
     * <p>Keeping the field nullable is what makes that possible at all. It previously held FOLLOW, and
     * {@code getCompanionMode()} coerced null to FOLLOW as well, so "no mode chosen" was unrepresentable and
     * every check for it was silently dead.
     */
    private CompanionMode companionMode;

    /**
     * Where a WAIT or WANDER order was given.
     *
     * <p>Persisted with the dimension id so the anchor still means something after a reload; a
     * position without a dimension would be ambiguous.
     */
    private String anchorDimension;
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private boolean hasAnchor;

    public Map<String, PlayerAffinity> entries() {
        return this.entries;
    }

    /** The bonded companion owner, or null when this creature has no companion bond. */
    public java.util.UUID getCompanionOwner() {
        if (this.companionOwnerUuid == null || this.companionOwnerUuid.isEmpty()) {
            return null;
        }
        try {
            return java.util.UUID.fromString(this.companionOwnerUuid);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Bind this creature to a player as a companion. Existing bonds are never overwritten. */
    public void setCompanionOwner(java.util.UUID owner) {
        this.companionOwnerUuid = owner == null ? null : owner.toString();
    }

    /** Whether this creature is bonded to the given player. */
    public boolean isCompanionOf(java.util.UUID player) {
        java.util.UUID owner = getCompanionOwner();
        return owner != null && owner.equals(player);
    }

    /** Record the anchor point for WAIT or WANDER. */
    public void setAnchor(String dimension, double xValue, double yValue, double zValue) {
        this.anchorDimension = dimension;
        this.anchorX = xValue;
        this.anchorY = yValue;
        this.anchorZ = zValue;
        this.hasAnchor = true;
    }

    /** Forget the anchor; used when the mode returns to FOLLOW. */
    public void clearAnchor() {
        this.hasAnchor = false;
        this.anchorDimension = null;
    }

    public boolean hasAnchor() {
        return this.hasAnchor;
    }

    /** Whether the anchor lies in the given dimension. */
    public boolean anchorIn(String dimension) {
        return this.hasAnchor && dimension != null && dimension.equals(this.anchorDimension);
    }

    public double anchorX() {
        return this.anchorX;
    }

    public double anchorY() {
        return this.anchorY;
    }

    public double anchorZ() {
        return this.anchorZ;
    }

    public CompanionMode getCompanionMode() {
        return this.companionMode == null ? CompanionMode.FOLLOW : this.companionMode;
    }

    /**
     * The mode that was actually recorded, or null when none ever was.
     *
     * <p>Separate from {@link #getCompanionMode()} on purpose: that one answers "what does this creature do",
     * this one answers "has anything been decided yet". Only the second question may be used to decide whether
     * a default is allowed to apply.
     */
    public CompanionMode chosenMode() {
        return this.companionMode;
    }

    public void setCompanionMode(CompanionMode mode) {
        this.companionMode = mode == null ? CompanionMode.FOLLOW : mode;
    }

    public int getGeneration() {
        return this.generation;
    }

    public void setGeneration(int value) {
        this.generation = Math.max(0, value);
    }

    /** Relation toward one player, or null when absent. */
    public PlayerAffinity get(UUID player) {
        return player == null ? null : this.entries.get(player.toString());
    }

    public PlayerAffinity getByKey(String key) {
        return this.entries.get(key);
    }

    /** Relation toward one player, created with the default affinity when absent. */
    public PlayerAffinity getOrCreate(UUID player, int defaultAffinity) {
        String key = player.toString();
        PlayerAffinity existing = this.entries.get(key);
        if (existing != null) {
            return existing;
        }
        PlayerAffinity created = new PlayerAffinity(defaultAffinity);
        this.entries.put(key, created);
        return created;
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    public int size() {
        return this.entries.size();
    }

    public void clear() {
        this.entries.clear();
    }

    /** Whether any relation reached friend status. */
    public boolean hasAnyBefriended() {
        for (PlayerAffinity affinity : this.entries.values()) {
            if (affinity.isBefriended()) {
                return true;
            }
        }
        return false;
    }

    /** Relation with the highest affinity, used for server-side friendly checks. */
    public PlayerAffinity highestAffinity() {
        PlayerAffinity best = null;
        for (PlayerAffinity affinity : this.entries.values()) {
            if (best == null || affinity.getAffinity() > best.getAffinity()) {
                best = affinity;
            }
        }
        return best;
    }

    public NbtCompound toNbt() {
        NbtCompound root = new NbtCompound();
        if (birthParent != null) root.putString("birth_parent", birthParent.toString());
        if (this.generation != 0) {
            root.putInt("generation", this.generation);
        }
        // Companion fields are written only when set, so old saves stay byte-identical. The mode is written
        // only once one was chosen: an absent mode means "never decided", which is not the same as FOLLOW.
        if (this.companionOwnerUuid != null && !this.companionOwnerUuid.isEmpty()) {
            root.putString("companion_owner", this.companionOwnerUuid);
            if (this.companionMode != null) {
                root.putString("companion_mode", this.companionMode.name());
            }
        }
        if (this.hasAnchor) {
            root.putString("anchor_dimension", this.anchorDimension);
            root.putDouble("anchor_x", this.anchorX);
            root.putDouble("anchor_y", this.anchorY);
            root.putDouble("anchor_z", this.anchorZ);
        }
        NbtList list = new NbtList();
        for (Map.Entry<String, PlayerAffinity> entry : this.entries.entrySet()) {
            NbtCompound wrapper = new NbtCompound();
            wrapper.putString("player_uuid", entry.getKey());
            wrapper.put("data", entry.getValue().toNbt());
            list.addElement(list.size(), wrapper);
        }
        root.put("entries", list);
        return root;
    }

    public static CreatureAffinityData fromNbt(NbtCompound root) {
        CreatureAffinityData data = new CreatureAffinityData();
        try {
            String parent = root.getString("birth_parent", "");
            if (!parent.isEmpty()) data.birthParent = UUID.fromString(parent);
        } catch (IllegalArgumentException ignored) {
            // Invalid or absent birth identity must never create an inferred family label.
        }
        data.generation = Math.max(0, root.getInt("generation", 0));
        // Absent in saves written before companion support: that simply means "unbound, FOLLOW".
        String companionOwner = root.getString("companion_owner", "");
        if (!companionOwner.isEmpty()) {
            data.companionOwnerUuid = companionOwner;
            // An absent mode stays absent: it means the player never chose one, which is what lets the load
            // backfill give a sitting pet WAIT instead of the plain default.
            String companionModeName = root.getString("companion_mode", "");
            if (!companionModeName.isEmpty()) {
                data.companionMode = CompanionMode.parse(companionModeName);
            }
            String anchorDimension = root.getString("anchor_dimension", "");
            if (!anchorDimension.isEmpty()) {
                data.anchorDimension = anchorDimension;
                data.anchorX = root.getDouble("anchor_x", 0.0);
                data.anchorY = root.getDouble("anchor_y", 0.0);
                data.anchorZ = root.getDouble("anchor_z", 0.0);
                data.hasAnchor = true;
            }
        }
        NbtList list = root.getListOrEmpty("entries");
        for (int i = 0; i < list.size(); i++) {
            NbtCompound wrapper = list.getCompoundOrEmpty(i);
            String key = wrapper.getString("player_uuid", "");
            if (key.isEmpty()) {
                continue;
            }
            data.entries.put(key, PlayerAffinity.fromNbt(wrapper.getCompoundOrEmpty("data")));
        }
        return data;
    }

    /** Short human readable summary used by debug logging. */
    public String describe() {
        if (this.entries.isEmpty()) {
            return "no relations";
        }
        PlayerAffinity best = this.highestAffinity();
        StringBuilder builder = new StringBuilder();
        builder.append(this.entries.size()).append(" relation(s), highest ")
                .append(best == null ? 0 : best.getAffinity());
        if (NonCreatureAffinity.LOG_VERBOSE) {
            builder.append(" | ");
            boolean first = true;
            for (PlayerAffinity affinity : this.entries.values()) {
                if (!first) {
                    builder.append(", ");
                }
                first = false;
                builder.append(affinity.getAffinity());
            }
        }
        return builder.toString();
    }

    /** Cheap checksum used to decide whether a client sync is needed. */
    public long checksum(UUID player) {
        PlayerAffinity affinity = get(player);
        if (affinity == null) {
            return 0L;
        }
        long hash = affinity.getAffinity();
        hash = hash * 31L + (affinity.isBefriended() ? 1 : 0);
        hash = hash * 31L + (affinity.isTamedByUs() ? 1 : 0);
        hash = hash * 31L + affinity.getMatingCount();
        return hash;
    }
}
