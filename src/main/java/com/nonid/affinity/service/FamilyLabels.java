package com.nonid.affinity.service;

import java.util.UUID;

/** Family labels require recorded parent identity; ownership and proximity are not evidence. */
public final class FamilyLabels {
    private FamilyLabels() {}

    public static String label(UUID recordedParent, UUID viewer, int genderMask) {
        if (recordedParent == null || !recordedParent.equals(viewer)) return "";
        return switch (genderMask) {
            case 1 -> "son";
            case 2 -> "daughter";
            default -> "child";
        };
    }
    public static String relative(int generations, int genderMask) {
        if (generations > 2) return "descendant";
        if (generations < -2) return "ancestor";
        return switch (generations) {
            case 1 -> genderMask == 1 ? "son" : genderMask == 2 ? "daughter" : "child";
            case 2 -> genderMask == 1 ? "grandson" : genderMask == 2 ? "granddaughter" : "grandchild";
            case -1 -> genderMask == 1 ? "father" : genderMask == 2 ? "mother" : "parent";
            case -2 -> genderMask == 1 ? "grandfather" : genderMask == 2 ? "grandmother" : "grandparent";
            default -> "";
        };
    }
}
