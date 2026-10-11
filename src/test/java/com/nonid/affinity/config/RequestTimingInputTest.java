package com.nonid.affinity.config;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RequestTimingInputTest {
    @Test void defaultsAndExactRoundTrips() {
        var cfg = new AffinityConfig();
        assertEquals(600, cfg.requestTimeoutTicks);
        assertEquals(300, cfg.requestCooldownTicks);
        for (int value : new int[]{20, 201, 600, 1200, 2400, Integer.MAX_VALUE})
            assertEquals(value, RequestTimingInput.ticks("requestTimeoutTicks", RequestTimingInput.seconds(value)).orElseThrow());
        assertEquals(0, RequestTimingInput.ticks("requestCooldownTicks", "0").orElseThrow());
    }
    @Test void invalidInputDoesNotRoundOrOverflow() {
        for (String value : new String[]{"", "-1", "NaN", "1e3", "0.5", "1.001", "107374182.4", "1."})
            assertTrue(RequestTimingInput.ticks("requestTimeoutTicks", value).isEmpty(), value);
        assertTrue(RequestTimingInput.ticks("unknown", "30").isEmpty());
    }
    @Test void oldCustomTicksSurviveLoadAndSanitize() {
        var cfg = new com.google.gson.Gson().fromJson("{\"requestTimeoutTicks\":201,\"requestCooldownTicks\":1201}", AffinityConfig.class);
        cfg.sanitize();
        assertEquals(201, cfg.requestTimeoutTicks);
        assertEquals(1201, cfg.requestCooldownTicks);
    }
}
