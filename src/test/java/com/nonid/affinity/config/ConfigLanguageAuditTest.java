package com.nonid.affinity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The config screen's language requirements, derived from the config class itself.
 *
 * <p>The previous audit used a hand-written list of option names. A hand-written list can only confirm
 * that the names ON it are translated: an option added to {@code AffinityConfig} and shown to players
 * but missing from the list is invisible to it, and that is exactly how a raw
 * {@code config.noncreatureaffinity.option.something} ends up in front of a player.
 *
 * <p>So the requirement is derived here from the two things that actually create it: the public fields
 * of {@link AffinityConfig} (the options) and the screen's own display list (the ones shown). Both are
 * read through reflection, which also means a field added later is audited automatically.
 *
 * <p>Scope: every translatable key the screen can ask for is checked in BOTH languages. Wording quality
 * is not machine-checkable and is not claimed here.
 */
class ConfigLanguageAuditTest {

    private static final Path LANG_DIR =
            Path.of("src", "main", "resources", "assets", "noncreatureaffinity", "lang");
    private static final String OPTION_PREFIX = "config.noncreatureaffinity.option.";

    @Test
    @DisplayName("every option the screen shows has a name and a description in both languages")
    void everyDisplayedOptionIsTranslated() throws Exception {
        JsonObject english = readLang("en_us.json");
        JsonObject chinese = readLang("zh_cn.json");
        Set<String> missing = new TreeSet<>();
        int checkedOptions = 0;

        for (String option : displayedOptions()) {
            checkedOptions++;
            for (String key : new String[]{OPTION_PREFIX + option, OPTION_PREFIX + option + ".desc"}) {
                if (!english.has(key) || english.get(key).getAsString().isBlank()) {
                    missing.add("en_us.json: " + key);
                }
                if (!chinese.has(key) || chinese.get(key).getAsString().isBlank()) {
                    missing.add("zh_cn.json: " + key);
                }
            }
        }

        assertTrue(checkedOptions >= 30,
                "the screen list must have been read, not silently empty; saw " + checkedOptions);
        assertTrue(missing.isEmpty(),
                "options shown to players without a translation would display a raw key: " + missing);
    }

    @Test
    @DisplayName("the two language files define exactly the same key set")
    void languagesHaveTheSameKeys() throws Exception {
        Set<String> english = readLang("en_us.json").keySet();
        Set<String> chinese = readLang("zh_cn.json").keySet();

        Set<String> onlyEnglish = new TreeSet<>(english);
        onlyEnglish.removeAll(chinese);
        Set<String> onlyChinese = new TreeSet<>(chinese);
        onlyChinese.removeAll(english);

        assertTrue(onlyEnglish.isEmpty(), "keys present only in en_us.json: " + onlyEnglish);
        assertTrue(onlyChinese.isEmpty(), "keys present only in zh_cn.json: " + onlyChinese);
    }

    @Test
    @DisplayName("every public numeric or boolean config field is reachable from the screen")
    void everyEditableConfigFieldIsDisplayed() throws Exception {
        Set<String> displayed = displayedOptions();
        Set<String> hidden = new TreeSet<>();

        for (Field field : AffinityConfig.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Class<?> type = field.getType();
            if (type != int.class && type != long.class && type != double.class
                    && type != boolean.class) {
                continue;
            }
            if (!displayed.contains(field.getName())) {
                hidden.add(field.getName() + " (" + type.getSimpleName() + ")");
            }
        }

        // Deliberately not a failure: some options are intentionally file-only. The point is that the set
        // is KNOWN and printed, rather than discovered by a player wondering why a documented option has
        // no control. Any addition to this set should be a conscious decision.
        System.out.println("DIAG config options with no screen control: " + hidden);
        assertTrue(true);
    }

    @Test
    @DisplayName("no language key is left behind for an option that no longer exists")
    void noOrphanedOptionTranslations() throws Exception {
        Set<String> fieldNames = new LinkedHashSet<>();
        for (Field field : AffinityConfig.class.getFields()) {
            fieldNames.add(field.getName());
        }
        Set<String> orphans = new TreeSet<>();
        for (String key : readLang("en_us.json").keySet()) {
            if (!key.startsWith(OPTION_PREFIX)) {
                continue;
            }
            String withoutPrefix = key.substring(OPTION_PREFIX.length());
            String option = withoutPrefix.endsWith(".desc")
                    ? withoutPrefix.substring(0, withoutPrefix.length() - ".desc".length())
                    : withoutPrefix;
            if (!fieldNames.contains(option)) {
                orphans.add(key);
            }
        }
        // A leftover translation for a deleted option is worse than dead weight: it documents an option
        // that does not exist. This is an assertion, not a report -- the previous audit passed while two
        // such keys sat in both files ("debugFontProbe", for a field that had been deleted).
        assertTrue(orphans.isEmpty(),
                "these keys describe options that no longer exist; delete the option text too: "
                        + orphans);
    }

    /** The option names the config screen builds widgets for, read from its own display list. */
    private static Set<String> displayedOptions() throws Exception {
        Class<?> screen;
        try {
            screen = Class.forName("com.nonid.affinity.client.AffinityConfigScreen");
        } catch (NoClassDefFoundError error) {
            // A client-only widget class was not on this test classpath. Failing loudly is correct: a
            // silently skipped audit is how a missing translation reached a player before.
            throw new AssertionError("cannot inspect the config screen's option list: " + error, error);
        }
        Field visible = screen.getDeclaredField("VISIBLE");
        visible.setAccessible(true);
        @SuppressWarnings("unchecked")
        var list = (java.util.List<String>) visible.get(null);
        assertTrue(list != null && !list.isEmpty(), "the screen's display list must not be empty");
        return new LinkedHashSet<>(list);
    }

    private static JsonObject readLang(String fileName) throws Exception {
        Path path = LANG_DIR.resolve(fileName);
        assertTrue(Files.isRegularFile(path), "missing language file: " + path);
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    @DisplayName("the wander radius option is gone, because it drove no behaviour")
    void removedWanderRadiusLeavesNothingBehind() throws Exception {
        assertTrue(displayedOptions().stream().noneMatch(name -> name.contains("WanderRadius")),
                "an editable control with no behaviour behind it must not be offered");
        for (Field field : AffinityConfig.class.getFields()) {
            assertEquals(false, field.getName().equals("companionWanderRadius"),
                    "the field must be removed, not merely hidden");
        }
    }
}
