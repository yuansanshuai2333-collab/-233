package com.nonid.affinity.config;

import java.math.BigDecimal;
import java.util.OptionalInt;

/** Seconds in the editor, exact ticks in existing configuration files. */
public final class RequestTimingInput {
    public static boolean supports(String name) {
        return name.equals("requestTimeoutTicks") || name.equals("requestCooldownTicks");
    }
    public static String seconds(int ticks) {
        return BigDecimal.valueOf(ticks).divide(BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString();
    }
    public static OptionalInt ticks(String name, String seconds) {
        if (!supports(name) || seconds == null || !seconds.matches("[0-9]+(?:\\.[0-9]+)?")) return OptionalInt.empty();
        try {
            int ticks = new BigDecimal(seconds).multiply(BigDecimal.valueOf(20)).intValueExact();
            return ticks >= (name.equals("requestTimeoutTicks") ? 20 : 0) ? OptionalInt.of(ticks) : OptionalInt.empty();
        } catch (NumberFormatException | ArithmeticException e) {
            return OptionalInt.empty();
        }
    }
    private RequestTimingInput() {}
}
