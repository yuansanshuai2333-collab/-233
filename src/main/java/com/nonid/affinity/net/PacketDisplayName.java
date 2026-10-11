package com.nonid.affinity.net;

/** String packet limits use UTF-16 length. Do not leave half a supplementary character at the boundary. */
public final class PacketDisplayName {
    private PacketDisplayName() {}
    public static String fit(String name, int limit) {
        if (name == null || limit <= 0) return "";
        if (name.length() <= limit) return name;
        int end = limit;
        if (Character.isHighSurrogate(name.charAt(end - 1)) && Character.isLowSurrogate(name.charAt(end))) end--;
        return name.substring(0, end);
    }
}
