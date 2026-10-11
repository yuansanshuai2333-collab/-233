package com.nonid.affinity.data;

/**
 * Access to the affinity data carried by an entity.
 *
 * <p>Implemented by a Mixin on {@link net.minecraft.entity.Entity} so that the rest of the code can
 * work with affinity data without depending on the Mixin itself. The interface lives in the data
 * package while the implementation is injected, which keeps the compile-time dependency one-way.
 *
 * <p>Two access styles are provided:
 * <ul>
 *   <li>{@link #affinityData()} creates the data when absent, for code that is about to write;</li>
 *   <li>{@link #peekAffinityData()} never creates anything, for read-only queries.</li>
 * </ul>
 *
 * <p>The distinction matters: bulk scans run over every nearby entity, and using the creating
 * accessor there would attach empty data objects to creatures the player never interacted with.
 */
public interface AffinityHolder {

    /**
     * The raw stored data, or {@code null} when this entity has none.
     *
     * <p>Implemented by the Mixin. Callers should normally use {@link #affinityData()} or
     * {@link #peekAffinityData()} instead of this.
     */
    CreatureAffinityData affinityDataRaw();

    /**
     * Replace the stored data.
     *
     * <p>Implemented by the Mixin. Passing {@code null} clears it.
     */
    void setAffinityDataRaw(CreatureAffinityData data);

    /**
     * The affinity data, created on demand.
     *
     * <p>Use this when the caller intends to modify the data. For read-only queries use
     * {@link #peekAffinityData()} so that unrelated entities are not given empty data.
     */
    default CreatureAffinityData affinityData() {
        CreatureAffinityData data = affinityDataRaw();
        if (data == null) {
            data = new CreatureAffinityData();
            setAffinityDataRaw(data);
        }
        return data;
    }

    /**
     * Read-only peek: returns {@code null} when the data has not been created yet, and never writes
     * to the attachment.
     *
     * <p>Used by per-tick bulk queries (for example scanning friendly units) so that every creature
     * that happens to walk past is not given an empty data object.
     */
    default CreatureAffinityData peekAffinityData() {
        return affinityDataRaw();
    }
}
