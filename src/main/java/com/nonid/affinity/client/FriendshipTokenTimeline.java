package com.nonid.affinity.client;

/** The approved art has 60 frames over three seconds; its preview-only pause is excluded. */
public final class FriendshipTokenTimeline {
    private FriendshipTokenTimeline() {}
    public static int frame(long elapsedMillis) {
        return elapsedMillis<0||elapsedMillis>=3000 ? -1 : (int)(elapsedMillis/50);
    }
}
