package com.nonid.affinity.client;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CompanionInfoPresentationTest {
    private static final String PREFIX = "companion_info.noncreatureaffinity.";
    private record Expected(CompanionInfoPresentation.TextField field, String token, String key) {}
    private static Expected e(CompanionInfoPresentation.TextField field, String token) {
        return new Expected(field, token, PREFIX + field.name().toLowerCase(java.util.Locale.ROOT) + "." + token);
    }
    private static Expected status(String token) { return new Expected(CompanionInfoPresentation.TextField.STATUS, token, PREFIX + "status." + token); }
    private static final java.util.List<Expected> KNOWN = java.util.List.of(
            status("unselected"), status("loading"), status("ready"), status("no_relation"), status("invalid"),
            status("out_of_range"), status("timeout"), status("unsupported"), status("historical"), status("dead"),
            e(CompanionInfoPresentation.TextField.OWNER, "unknown"), e(CompanionInfoPresentation.TextField.OWNER, "self"),
            e(CompanionInfoPresentation.TextField.OWNER, "other"), e(CompanionInfoPresentation.TextField.OWNER, "unowned"),
            e(CompanionInfoPresentation.TextField.CONTROL, "unknown"), e(CompanionInfoPresentation.TextField.CONTROL, "none"),
            e(CompanionInfoPresentation.TextField.CONTROL, "native"), e(CompanionInfoPresentation.TextField.CONTROL, "relationship"),
            e(CompanionInfoPresentation.TextField.MODE, "unknown"), e(CompanionInfoPresentation.TextField.MODE, "native"),
            e(CompanionInfoPresentation.TextField.MODE, "sit"), e(CompanionInfoPresentation.TextField.MODE, "follow"),
            e(CompanionInfoPresentation.TextField.MODE, "wander"), e(CompanionInfoPresentation.TextField.MODE, "wait"),
            e(CompanionInfoPresentation.TextField.MODE, "passive"), e(CompanionInfoPresentation.TextField.MODE, "stay"),
            e(CompanionInfoPresentation.TextField.MODE, "attack"),
            e(CompanionInfoPresentation.TextField.RESTRICTION, "unknown"), e(CompanionInfoPresentation.TextField.RESTRICTION, "native"),
            e(CompanionInfoPresentation.TextField.RESTRICTION, "ownership_conflict"), e(CompanionInfoPresentation.TextField.RESTRICTION, "other_owner"),
            e(CompanionInfoPresentation.TextField.RESTRICTION, "locked"), e(CompanionInfoPresentation.TextField.RESTRICTION, "unbound"),
            e(CompanionInfoPresentation.TextField.RESTRICTION, "controllable"), e(CompanionInfoPresentation.TextField.RESTRICTION, "interaction"),
            e(CompanionInfoPresentation.TextField.RESTRICTION, "riding"), e(CompanionInfoPresentation.TextField.RESTRICTION, "leashed"));

    @Test
    void summaryIsIdentityAndRelationOnly() {
        assertTrue(CompanionInfoPresentation.allowed(CompanionInfoPresentation.Access.SUMMARY,
                CompanionInfoPresentation.Field.IDENTITY));
        assertTrue(CompanionInfoPresentation.allowed(CompanionInfoPresentation.Access.SUMMARY,
                CompanionInfoPresentation.Field.RELATION));
        assertFalse(CompanionInfoPresentation.allowed(CompanionInfoPresentation.Access.SUMMARY,
                CompanionInfoPresentation.Field.VITALS));
        assertFalse(CompanionInfoPresentation.allowed(CompanionInfoPresentation.Access.SUMMARY,
                CompanionInfoPresentation.Field.CONTROL));
    }

    @Test
    void detailedModeCanRenderOnlyAuthoritativeSnapshotFields() {
        for (var field : CompanionInfoPresentation.Field.values())
            assertTrue(CompanionInfoPresentation.allowed(CompanionInfoPresentation.Access.DETAILED, field));
        assertTrue(CompanionInfoPresentation.allowed(CompanionInfoPresentation.Access.DETAILED,
                CompanionInfoPresentation.Field.HISTORY));
    }

    @Test
    void directoryDoesNotPretendToBeLiveControl() {
        assertTrue(CompanionInfoPresentation.allowed(CompanionInfoPresentation.Access.DIRECTORY,
                CompanionInfoPresentation.Field.HISTORY));
        assertFalse(CompanionInfoPresentation.allowed(CompanionInfoPresentation.Access.DIRECTORY,
                CompanionInfoPresentation.Field.CONTROL));
        assertFalse(CompanionInfoPresentation.allowed(CompanionInfoPresentation.Access.DIRECTORY,
                CompanionInfoPresentation.Field.DISTANCE));
    }

    @Test
    void unknownSentinelsStayUnknown() {
        assertEquals("0", CompanionInfoPresentation.integer(0));
        assertEquals("companion_info.noncreatureaffinity.unknown", CompanionInfoPresentation.integer(-1));
        assertEquals("1.5", CompanionInfoPresentation.decimal(1.5));
        assertEquals("companion_info.noncreatureaffinity.unknown", CompanionInfoPresentation.decimal(-1));
        assertEquals("companion_info.noncreatureaffinity.unknown", CompanionInfoPresentation.decimal(Double.NaN));
    }

    @Test
    void stateTokensNeverLeakToPlayerText() {
        assertEquals("companion_info.noncreatureaffinity.status.ready", CompanionInfoPresentation.key(CompanionInfoPresentation.TextField.STATUS, "ready"));
        assertEquals("companion_info.noncreatureaffinity.owner.unknown", CompanionInfoPresentation.key(CompanionInfoPresentation.TextField.OWNER, "unknown"));
        assertEquals("companion_info.noncreatureaffinity.control.unknown", CompanionInfoPresentation.key(CompanionInfoPresentation.TextField.CONTROL, ""));
        assertEquals("companion_info.noncreatureaffinity.mode.unknown", CompanionInfoPresentation.key(CompanionInfoPresentation.TextField.MODE, "timeout"));
        assertEquals("companion_info.noncreatureaffinity.restriction.other_owner", CompanionInfoPresentation.key(CompanionInfoPresentation.TextField.RESTRICTION, "other_owner"));
        assertNotEquals("ready", CompanionInfoPresentation.key(CompanionInfoPresentation.TextField.STATUS, "ready"));
        assertEquals("companion_info.noncreatureaffinity.mode.unknown", CompanionInfoPresentation.key(CompanionInfoPresentation.TextField.MODE, "internal_future_mode"));
    }

    @Test
    void everyKnownTokenMapsWithoutLeakingTheToken() {
        assertEquals(37, KNOWN.size());
        for (var expected : KNOWN)
            assertEquals(expected.key(), CompanionInfoPresentation.key(expected.field(), expected.token()), expected.token());
    }

    @Test
    void nullAndUnknownValuesUseTheExistingUnknownKeys() {
        for (var field : CompanionInfoPresentation.TextField.values()) {
            assertEquals(CompanionInfoPresentation.key(field, "unknown"), CompanionInfoPresentation.key(field, null));
            assertEquals(CompanionInfoPresentation.key(field, "unknown"), CompanionInfoPresentation.key(field, "future_token"));
            assertEquals(CompanionInfoPresentation.key(field, "unknown"), CompanionInfoPresentation.key(field, ""));
        }
    }

    @Test
    void mappedKeysHaveNonEmptyValuesInChineseAndEnglishResources() throws Exception {
        for (var lang : new String[]{"zh_cn", "en_us"}) {
            com.google.gson.JsonObject json;
            try (var stream = getClass().getResourceAsStream("/assets/noncreatureaffinity/lang/" + lang + ".json")) {
                assertNotNull(stream, lang);
                try (var reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
                    json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
            for (var expected : KNOWN) {
                assertTrue(json.has(expected.key()), expected.key() + " missing in " + lang);
                assertFalse(json.get(expected.key()).isJsonNull(), expected.key() + " null in " + lang);
                assertFalse(json.get(expected.key()).getAsString().isBlank(), expected.key() + " blank in " + lang);
            }
        }
    }
}
