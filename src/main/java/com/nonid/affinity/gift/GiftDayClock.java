package com.nonid.affinity.gift;
import java.math.BigInteger;
/** Each positive world-time setter advance is counted once, including multiple commands in one tick. */
public final class GiftDayClock {
    private long previous;
    private boolean initialized, active;
    private BigInteger pending = BigInteger.ZERO, observed = BigInteger.ZERO;
    public void begin(long time) { if (!initialized) { previous=time; initialized=true; } active=true; }
    public BigInteger changed(long before, long after) {
        if (!initialized) return BigInteger.ZERO;
        if (before > previous) pending=pending.add(BigInteger.valueOf(before).subtract(BigInteger.valueOf(previous)));
        var delta = after > before ? BigInteger.valueOf(after).subtract(BigInteger.valueOf(before)) : BigInteger.ZERO;
        pending=pending.add(delta); observed=observed.add(delta); previous=after; return delta;
    }
    public BigInteger endUnobserved(long time) { var known=observed; return endExact(time).subtract(known); }
    public BigInteger endExact(long time) {
        if (!active) return BigInteger.ZERO;
        active=false;
        if(time>previous) pending=pending.add(BigInteger.valueOf(time).subtract(BigInteger.valueOf(previous))); previous=time;
        var elapsed=pending; pending=BigInteger.ZERO; observed=BigInteger.ZERO; return elapsed;
    }
    public long end(long time) { return endExact(time).longValueExact(); }
    public void reset() { initialized=false; active=false; pending=BigInteger.ZERO; observed=BigInteger.ZERO; }
}
