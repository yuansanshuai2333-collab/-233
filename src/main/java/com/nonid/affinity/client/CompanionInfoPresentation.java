package com.nonid.affinity.client;

/**
 * Read-only page wiring policy. This is deliberately not connected to layout or
 * network code yet; it records which already-authoritative fields a page mode
 * may render and gives every unknown numeric value one display convention.
 */
public final class CompanionInfoPresentation {
    public enum Access { SUMMARY, DETAILED, DIRECTORY }
    public enum Field { IDENTITY, RELATION, VITALS, CONTROL, DISTANCE, HISTORY }
    public enum TextField { STATUS, OWNER, CONTROL, MODE, RESTRICTION }
    private static final String PREFIX = "companion_info.noncreatureaffinity.";

    private CompanionInfoPresentation() {}

    public static boolean allowed(Access access, Field field) {
        return switch (access) {
            case SUMMARY -> field == Field.IDENTITY || field == Field.RELATION;
            case DETAILED -> true;
            case DIRECTORY -> field == Field.IDENTITY || field == Field.RELATION || field == Field.HISTORY;
        };
    }

    /** Returns an existing localization key; protocol tokens never reach player text. */
    public static String key(TextField field, String value) {
        if (value == null || value.isBlank() || value.equals("unknown")) return unknownKey(field);
        boolean known = switch (field) {
            case STATUS -> switch (value) {
                case "unselected", "loading", "ready", "no_relation", "invalid", "out_of_range", "timeout",
                        "unsupported", "historical", "dead" -> true;
                default -> false;
            };
            case OWNER -> switch (value) { case "self", "other", "unowned" -> true; default -> false; };
            case CONTROL -> switch (value) { case "none", "native", "relationship" -> true; default -> false; };
            case MODE -> switch (value) { case "native", "sit", "follow", "wander", "wait", "passive", "stay", "attack" -> true; default -> false; };
            case RESTRICTION -> switch (value) {
                case "native", "ownership_conflict", "other_owner", "locked", "unbound", "controllable",
                        "interaction", "riding", "leashed" -> true;
                default -> false;
            };
        };
        return known ? PREFIX + field.name().toLowerCase(java.util.Locale.ROOT) + "." + value : unknownKey(field);
    }

    private static String unknownKey(TextField field) {
        return switch (field) {
            case STATUS -> PREFIX + "unknown";
            default -> PREFIX + field.name().toLowerCase(java.util.Locale.ROOT) + ".unknown";
        };
    }

    /** Negative values are the existing protocol's unknown sentinel. */
    public static String integer(int value) {
        return value < 0 ? PREFIX + "unknown" : Integer.toString(value);
    }

    /** Negative or non-finite values are the existing protocol's unknown sentinel. */
    public static String decimal(double value) {
        return !Double.isFinite(value) || value < 0 ? PREFIX + "unknown" : Double.toString(value);
    }
}
