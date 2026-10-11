package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards what a compilation check alone does not: that the attack-launch stamp is anchored to the RIGHT
 * method on the RIGHT class, and that its mixin is registered.
 *
 * <p>Failure modes this catches, all of which previously reached a candidate jar unverified:
 * <ul>
 *   <li><b>the mixin is on the wrong class.</b> This actually happened: it mixed into
 *       {@code LivingEntity#tryAttack}, which the mapped bytecode shows is a no-op stub, while the real
 *       attack body is {@code MobEntity#tryAttack}. The stamp was therefore never issued for any mob and
 *       the late-hit check was inert, while every unit test still passed. The class is asserted below,
 *       and the stub itself is asserted to be a stub, so this cannot silently recur.</li>
 *   <li>a Yarn update renaming the injected target method. Mixin resolves {@code method = "tryAttack"}
 *       at load time, so this would fail at GAME START. Here it fails the ordinary test run instead.</li>
 *   <li>a mixin written but never listed in {@code noncreatureaffinity.mixins.json}, in which case the
 *       injection never applies and the stamp silently never happens.</li>
 * </ul>
 *
 * <p>Scope, stated honestly: this does not load or apply the mixin, so it proves the TARGET and CLASS are
 * right and the mixin is registered -- not that the injection is behaviourally correct in a running
 * client. That still needs the in-game acceptance pass.
 */
class AttackStampMixinContractTest {

    /** Mirrors @Mixin(MobEntity.class) in the mixin source. */
    private static final String MIXIN_TARGET = "net.minecraft.entity.mob.MobEntity";

    private static final String MOB_ENTITY = "net.minecraft.entity.mob.MobEntity";
    private static final String LIVING_ENTITY = "net.minecraft.entity.LivingEntity";

    @Test
    @DisplayName("the injected attack method really exists on the mixed class")
    void injectedTargetMethodExists() throws Exception {
        // Read from the class file rather than through reflection: a plain JUnit process cannot LINK
        // MobEntity (asking about its methods threw VerifyError, because the Minecraft class hierarchy is
        // not verifiable here). Bytecode needs no linking and answers the same question.
        byte[] mob = classBytes(MOB_ENTITY);
        int codeLength = CodeScanner.findCodeLengthByName(mob, "tryAttack");
        assertTrue(codeLength > 0,
                "MobEntity must declare tryAttack by name, or the mixin's anchor does not exist");
    }

    @Test
    @DisplayName("MobEntity is the class that implements the attack, and LivingEntity's version is a stub")
    void theAttackBodyIsOnMobEntityNotLivingEntity() throws Exception {
        // This is the failure that actually shipped: the mixin sat on LivingEntity#tryAttack, which the
        // mapped bytecode shows is a no-op stub calling onAttacking and returning false, while the real
        // attack body is MobEntity#tryAttack. The stamp was therefore never issued for any mob and the
        // late-hit check was inert, while every unit test still passed.
        int stubSize = CodeScanner.findCodeLengthByName(classBytes(LIVING_ENTITY), "tryAttack");
        int realSize = CodeScanner.findCodeLengthByName(classBytes(MOB_ENTITY), "tryAttack");

        assertTrue(stubSize > 0, "LivingEntity must also declare a tryAttack, the stub one");
        assertTrue(realSize > 0, "MobEntity must declare the real tryAttack");
        assertTrue(stubSize < realSize,
                "LivingEntity#tryAttack is expected to be a stub and MobEntity#tryAttack the real body; "
                        + "sizes were " + stubSize + " vs " + realSize
                        + ". If this ever changes, the mixin target must be re-examined.");
        // A stub is tiny; the real body computes damage, applies it and runs enchantment hooks.
        assertTrue(stubSize < 100,
                "the stub should be only a few instructions, was " + stubSize + " bytes");
    }

    /** Read a compiled class from the test classpath without linking it. */
    private static byte[] classBytes(String className) throws Exception {
        String resource = className.replace('.', '/') + ".class";
        try (var stream = AttackStampMixinContractTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(stream, "class not on the test classpath: " + className);
            return stream.readAllBytes();
        }
    }

    @Test
    @DisplayName("the mixin targets the mapped class and brackets the body through the production wrapper")
    void mixinSourceMatchesItsTarget() throws Exception {
        Path source = Path.of("src", "main", "java", "com", "nonid", "affinity", "mixin",
                "CompanionAttackStampMixin.java");
        assertTrue(Files.isRegularFile(source), "the attack-stamp mixin source must exist");
        String text = Files.readString(source, StandardCharsets.UTF_8);

        assertEquals(MIXIN_TARGET, declaredMixinTarget(text),
                "the mixin must target the class whose method this test looks up, or the two can drift");
        assertTrue(text.contains("tryAttack"),
                "the injection must anchor on the attack method");
    }

    @Test
    @DisplayName("the mixin WRAPS the whole attack method, not a call inside it")
    void mixinWrapsTheWholeMethod() throws Exception {
        Path mixin = Path.of("src", "main", "java", "com", "nonid", "affinity", "mixin",
                "CompanionAttackStampMixin.java");
        String mixinText = Files.readString(mixin, StandardCharsets.UTF_8);

        // This is the assertion that catches the defect rc.16 shipped: a @Redirect on a single call at the
        // top of the body released the stamp before any damage was applied, so no melee hit could ever be
        // credited while every "there is a finally" test still passed.
        assertTrue(mixinText.contains("@WrapMethod"),
                "the whole method must be wrapped; an @Inject or @Redirect brackets only part of it");
        assertFalse(mixinText.contains("@Redirect"),
                "a redirect on a call inside the body does NOT wrap the attack");
        assertFalse(mixinText.contains("@Inject(method"),
                "injection points at HEAD/RETURN cannot cover a throwing exit either");
        assertTrue(mixinText.contains("original.call("),
                "the original must be invoked through the operation handle, so it runs exactly once");
        assertFalse(mixinText.contains("this.tryAttack") || mixinText.contains("self.tryAttack"),
                "the wrapper must not re-enter tryAttack: that would recurse instead of running the body");
        assertTrue(mixinText.contains("AttackStampHandler.runWithStamp"),
                "and the wrapper must route through the production handler that owns the try/finally");

        Path handler = Path.of("src", "main", "java", "com", "nonid", "affinity", "service",
                "AttackStampHandler.java");
        assertTrue(Files.isRegularFile(handler), "the wrapper must exist as its own testable class");
        String handlerText = Files.readString(handler, StandardCharsets.UTF_8);
        assertTrue(handlerText.contains("try {") && handlerText.contains("} finally {"),
                "the handler must use a real finally so the stamp is released on every exit path");
        assertTrue(handlerText.contains("onCompanionAttackFinished"),
                "the finally must route through the production cleanup");
        assertFalse(handlerText.contains("catch ("),
                "no exception may be caught or swallowed to make cleanup work");
    }

    @Test
    @DisplayName("our own movement goal claims both controls, which is why the air gate has to exist")
    void ourMovementGoalClaimsBothControls() throws Exception {
        // Read from our OWN source, which is not obfuscated, so this is a reliable check of the property
        // that creates the conflict: this goal holds MOVE and JUMP.
        //
        // Why this matters, and why the previous write-up was incomplete: vanilla registers SwimGoal and
        // BreatheAirGoal at priority 0. Reading the mapped jar with javap shows SwimGoal claims JUMP only
        // (it calls getJumpControl().setActive()) while BreatheAirGoal claims MOVE. Both of those are
        // controls this goal holds, so BOTH vanilla goals are blocked while it runs -- the earlier note
        // recorded the risk as SwimGoal alone and missed the MOVE conflict with BreatheAirGoal.
        //
        // This assertion is deliberately about our source and not about vanilla's bytecode: the class
        // bytes on this test classpath are obfuscated at field level (Goal$Control.JUMP is not a constant
        // in them), so a string scan of vanilla bytecode here would report "no controls" whether or not
        // the claim were true. A check that cannot fail is worse than no check.
        Path source = Path.of("src", "main", "java", "com", "nonid", "affinity", "service",
                "CompanionMovementGoal.java");
        assertTrue(Files.isRegularFile(source), "the movement goal source must exist");
        String text = Files.readString(source, StandardCharsets.UTF_8);

        assertTrue(text.contains("Control.MOVE"), "this goal must claim MOVE, or there is no conflict");
        assertTrue(text.contains("Control.JUMP"), "this goal must claim JUMP, or there is no conflict");
        // And the gate must actually be wired into the decision, not merely documented.
        assertTrue(text.contains("AirSupply.mustYieldForAir"),
                "the air gate must be part of controlsCompanion, or it changes nothing");
    }

    @Test
    @DisplayName("every mixin source in the package is registered in the mixin config")
    void mixinsAreRegistered() throws Exception {
        Path mixinDir = Path.of("src", "main", "java", "com", "nonid", "affinity", "mixin");
        Path config = Path.of("src", "main", "resources", "noncreatureaffinity.mixins.json");
        assertTrue(Files.isRegularFile(config), "the mixin config must exist");
        String configText = Files.readString(config, StandardCharsets.UTF_8);

        try (var files = Files.list(mixinDir)) {
            var sources = files.filter(path -> path.toString().endsWith(".java")).toList();
            assertTrue(sources.size() >= 3, "expected the existing mixins plus the attack stamp");
            for (Path source : sources) {
                String name = source.getFileName().toString().replace(".java", "");
                assertTrue(configText.contains("\"" + name + "\""),
                        name + " exists but is not listed in noncreatureaffinity.mixins.json, so its"
                                + " injection never applies");
            }
        }
    }

    @Test
    @DisplayName("an accessor mixin declares accessors only, because a body makes Mixin reject it at startup")
    void accessorMixinsHaveNoBodies() throws Exception {
        // This is a defect that reached a candidate jar: MerchantScreenHandlerAccess declared a `default` method
        // that composed its two accessors. Compilation, the full test suite and the jar listing were all green, and
        // the game then refused to start with
        //   "@Mixin target type mismatch: MerchantScreenHandler is not an interface"
        // because a body turns the mixin into an interface mixin instead of an accessor mixin. The composite read
        // belongs in the class that uses it; nothing belongs in the accessor.
        Path mixinDir = Path.of("src", "main", "java", "com", "nonid", "affinity", "mixin");
        try (var files = Files.list(mixinDir)) {
            var sources = files.filter(path -> path.toString().endsWith(".java")).toList();
            assertTrue(sources.size() >= 3, "expected the mixin package to still hold the existing mixins");
            for (Path source : sources) {
                String text = Files.readString(source, StandardCharsets.UTF_8);
                if (!text.contains("@Accessor(\"") && !text.contains("@Invoker(\"")) {
                    continue;
                }
                assertFalse(text.contains("default "),
                        source.getFileName() + " is an accessor mixin and must not declare a default method: "
                                + "Mixin refuses to prepare such a mixin and the game does not start");
                assertFalse(text.contains("static "),
                        source.getFileName() + " is an accessor mixin and must not declare a static method");
                assertTrue(text.contains("interface "),
                        source.getFileName() + " uses @Accessor and must therefore be an interface");
            }
        }
    }

    @Test
    @DisplayName("no mixin config owns a package that also holds normal classes, because that kills the client")
    void mixinPackagesHoldOnlyMixins() throws Exception {
        // A defect that reached a candidate jar and was only caught by STARTING THE CLIENT:
        //
        //   IllegalClassLoadError: com.nonid.affinity.client.NonCreatureAffinityClient is in a defined mixin
        //   package com.nonid.affinity.client.* owned by noncreatureaffinity.client.mixins.json and cannot be
        //   referenced directly
        //
        // Declaring a package as a mixin package makes Mixin refuse to load ANY class in it directly -- including
        // the mod's own client entrypoint -- so the game crashed during initialisation before drawing anything.
        // Compilation, the whole unit suite, the packaged jar listing and a SERVER start were all green, because
        // none of them loads a client entrypoint through the mixin transformer.
        //
        // The rule: every class in a package named by a mixin config must be a mixin. An accompanying class goes in
        // a sibling package that no config claims.
        for (Path config : mixinConfigs()) {
            String text = Files.readString(config, StandardCharsets.UTF_8);
            String packageName = declaredPackage(text);
            assertTrue(packageName.startsWith("com.nonid.affinity"),
                    config.getFileName() + " must declare a package inside the mod");
            String directory = packageName.replace('.', '/');
            Path sourceDir = Path.of("src", "main", "java", directory);
            assertTrue(Files.isDirectory(sourceDir),
                    config.getFileName() + " names package " + packageName + " but " + sourceDir
                            + " does not exist, so the declared package and the code have drifted apart");

            // The mixins the config lists, by simple name.
            java.util.Set<String> listed = new java.util.HashSet<>();
            for (String entry : text.split("\"")) {
                if (entry.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
                    listed.add(entry);
                }
            }
            // A Mixin config PLUGIN is the one legitimate non-mixin in such a package: it is named by the config's
            // own "plugin" entry and is loaded by Mixin as an ordinary class, which is exactly why it must live in
            // the package the config names. It is detected from its source rather than hard-coded, so this stays
            // honest if the plugin is ever renamed or removed.
            java.util.Set<String> plugins = new java.util.HashSet<>();
            Matcher pluginRef = Pattern.compile("\"plugin\"\\s*:\\s*\"([\\w.]+)\"").matcher(text);
            if (pluginRef.find()) {
                String[] parts = pluginRef.group(1).split("\\.");
                plugins.add(parts[parts.length - 1]);
            }
            try (var files = Files.list(sourceDir)) {
                for (Path source : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String simpleName = source.getFileName().toString().replace(".java", "");
                    if (plugins.contains(simpleName)) {
                        String pluginSource = Files.readString(source, StandardCharsets.UTF_8);
                        assertTrue(pluginSource.contains("IMixinConfigPlugin"),
                                simpleName + " is named as this config's plugin but does not implement"
                                        + " IMixinConfigPlugin, so it is not the exception it claims to be");
                        continue;
                    }
                    assertTrue(listed.contains(simpleName),
                            source.getFileName() + " lives in " + packageName + ", which " + config.getFileName()
                                    + " declares as a MIXIN package, but it is not listed as a mixin. Mixin will"
                                    + " refuse to load it directly, and if anything references it -- an entrypoint,"
                                    + " a service, another class -- the game crashes at startup before it can even"
                                    + " draw. Move it to a package that no mixin config claims.");
                }
            }
        }
    }

    /** Every mixin configuration the mod ships, read from the mod metadata so none can be forgotten. */
    private static java.util.List<Path> mixinConfigs() throws Exception {
        Path metadata = Path.of("src", "main", "resources", "fabric.mod.json");
        String json = Files.readString(metadata, StandardCharsets.UTF_8);
        java.util.List<Path> configs = new java.util.ArrayList<>();
        for (String entry : json.split("\"")) {
            if (entry.endsWith(".mixins.json")) {
                Path config = Path.of("src", "main", "resources", entry);
                assertTrue(Files.isRegularFile(config),
                        "fabric.mod.json names " + entry + " but that file does not exist");
                configs.add(config);
            }
        }
        assertTrue(configs.size() >= 3, "expected the common, NON and client mixin configs");
        return configs;
    }

    /** The {@code package} a mixin config declares. */
    private static String declaredPackage(String text) {
        Matcher matcher = Pattern.compile("\"package\"\\s*:\\s*\"([\\w.]+)\"").matcher(text);
        assertTrue(matcher.find(), "a mixin config must declare its package");
        return matcher.group(1);
    }

    private static String declaredMixinTarget(String source) {
        Matcher matcher = Pattern.compile("@Mixin\\((\\w+)\\.class\\)").matcher(source);
        assertTrue(matcher.find(), "the mixin must declare a @Mixin target");
        String simpleName = matcher.group(1);
        Matcher imports = Pattern.compile("import (net\\.minecraft\\.[\\w.]*\\.%s);"
                .formatted(simpleName)).matcher(source);
        assertTrue(imports.find(), "the @Mixin target must be imported explicitly, not wildcarded");
        return imports.group(1);
    }

    private static String injectedMethodName(String source) {
        Matcher matcher = Pattern.compile("@Inject\\(method = \"(\\w+)\"").matcher(source);
        assertTrue(matcher.find(), "the mixin must declare an @Inject with an explicit method name");
        return matcher.group(1);
    }
}
