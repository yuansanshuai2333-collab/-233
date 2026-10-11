package com.nonid.affinity.service;

/** NoN energy, named sexual repression in the installed NoN Chinese translation. Read only. */
public record RequestEnergy(int value, int maximum, boolean initialized) {
    public static final RequestEnergy UNKNOWN = new RequestEnergy(-1, -1, false);
    public boolean known() { return initialized && value >= 0 && maximum > 0; }
    public double ratio() { return known() ? Math.clamp((double)value / maximum, 0, 1) : -1; }
    public int hearts() { return known() ? (int)Math.floor(ratio()*8) : 0; }
    public int stage() { return RequestPresentation.desireStage(value, maximum, initialized); }
}