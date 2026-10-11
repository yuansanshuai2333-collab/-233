package com.nonid.affinity.non;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.spongepowered.include.com.google.gson.Gson;
import static org.junit.jupiter.api.Assertions.*;

/** Mixin uses a default-charset reader; ASCII, BOM-free configs also work with Windows legacy defaults. */
class MixinResourceEncodingTest {
    private static final String[] CONFIGS = {
        "noncreatureaffinity.mixins.json", "noncreatureaffinity.non.mixins.json",
        "noncreatureaffinity.mixin.client.mixins.json"
    };

    private byte[] resource(String name) throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input, name + " must be packaged");
            return input.readAllBytes();
        }
    }

    @Test void configurationsAreAsciiWithoutBom() throws Exception {
        for (String name : CONFIGS) {
            for (byte value : resource(name)) {
                assertTrue((value & 0xff) < 128, name + " contains non-ASCII bytes, possibly a BOM");
            }
        }
    }

    private void parseAll(Charset charset) throws Exception {
        for (String name : CONFIGS) {
            try (var reader = new InputStreamReader(new ByteArrayInputStream(resource(name)), charset)) {
                ConfigShape config = new Gson().fromJson(reader, ConfigShape.class);
                assertNotNull(config, name);
                assertEquals("JAVA_21", config.compatibilityLevel, name);
                assertTrue(config.client != null || config.mixins != null, name);
            }
        }
    }

    @Test void mixinGsonReadsUtf8() throws Exception { parseAll(StandardCharsets.UTF_8); }
    @Test void mixinGsonReadsGbk() throws Exception { parseAll(Charset.forName("GBK")); }
    @Test void mixinGsonReadsWindows1252() throws Exception { parseAll(Charset.forName("windows-1252")); }

    private static final class ConfigShape {
        String compatibilityLevel;
        String[] client;
        String[] mixins;
    }
}
