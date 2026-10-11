package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the ranged stamp's anchors, and the hierarchy claim the two mixins rest on.
 *
 * <p>The claim being checked: {@code PersistentProjectileEntity} declares its OWN {@code setOwner(Entity)},
 * so an injection into {@code ProjectileEntity#setOwner} would not run for an arrow. That is the exact
 * class of mistake this project already made once with {@code tryAttack} -- mixing into a parent and
 * silently never running for the child -- so it is asserted rather than assumed.
 *
 * <p>Everything is read from class files. A plain JUnit process cannot link these classes, and the mapped
 * class bytes are obfuscated at field level, so the checks are on method NAMES and hierarchy, which are
 * stable.
 */
class ProjectileStampMixinContractTest {

    private static final String PROJECTILE = "net.minecraft.entity.projectile.ProjectileEntity";
    private static final String PERSISTENT = "net.minecraft.entity.projectile.PersistentProjectileEntity";
    private static final String ARROW = "net.minecraft.entity.projectile.ArrowEntity";

    @Test
    @DisplayName("both projectile classes declare setOwner, so both need their own injection")
    void bothClassesDeclareSetOwner() throws Exception {
        int base = CodeScanner.findCodeLength(classBytes(PROJECTILE), "setOwner",
                "(Lnet/minecraft/entity/Entity;)V");
        int persistent = CodeScanner.findCodeLength(classBytes(PERSISTENT), "setOwner",
                "(Lnet/minecraft/entity/Entity;)V");

        assertTrue(base > 0, "ProjectileEntity must declare setOwner(Entity)");
        assertTrue(persistent > 0,
                "PersistentProjectileEntity must declare its OWN setOwner(Entity); if this ever becomes "
                        + "inherited only, the second mixin can be removed");
    }

    @Test
    @DisplayName("an arrow is a persistent projectile, which is why the skeleton case needs that mixin")
    void arrowExtendsPersistentProjectile() throws Exception {
        String superName = CodeScanner.superClassName(classBytes(ARROW));
        assertEquals(PERSISTENT.replace('.', '/'), superName,
                "ArrowEntity must extend PersistentProjectileEntity, or the ranged species analysis is wrong");
    }

    @Test
    @DisplayName("both launch mixins are registered and inject at the tail of setOwner")
    void mixinsAreRegisteredAndAnchored() throws Exception {
        Path config = Path.of("src", "main", "resources", "noncreatureaffinity.mixins.json");
        String configText = Files.readString(config, StandardCharsets.UTF_8);
        for (String mixin : new String[]{"ProjectileLaunchStampMixin",
                "PersistentProjectileLaunchStampMixin"}) {
            assertTrue(configText.contains("\"" + mixin + "\""),
                    mixin + " is not registered, so its injection never applies");
            Path source = Path.of("src", "main", "java", "com", "nonid", "affinity", "mixin",
                    mixin + ".java");
            assertTrue(Files.isRegularFile(source), "missing mixin source: " + mixin);
            String text = Files.readString(source, StandardCharsets.UTF_8);
            assertTrue(text.contains("setOwner(Lnet/minecraft/entity/Entity;)V"),
                    mixin + " must name setOwner(Entity) explicitly, or the anchor is ambiguous");
            assertTrue(text.contains("@At(\"TAIL\")"),
                    mixin + " must inject AFTER the owner is assigned, so the field is already set");
            assertTrue(text.contains("onCompanionProjectileOwnerAssigned"),
                    mixin + " must route through the production attribution method");
            // A non-companion owner must be PASSED THROUGH as null, not skipped: skipping it would leave
            // the projectile looking unseen, and a later companion assignment would then create a first
            // attribution for a projectile that was already in someone else's hands.
            assertTrue(text.contains("MobEntity companion = owner instanceof MobEntity mob ? mob : null"),
                    mixin + " must pass a non-companion owner through as null rather than returning early");
            assertFalse(text.contains("if (!(owner instanceof MobEntity companion)) {\n            return;"),
                    mixin + " must not return early for a non-companion owner");
        }
    }

    @Test
    @DisplayName("the subclass setOwner does call the superclass, so both injections fire for an arrow")
    void subclassDelegatesToTheSuperclass() throws Exception {
        // This is why the stamp has to be idempotent: the arrow path reaches BOTH injections, so a rule
        // that stamped on every call would overwrite the launch generation with the current one.
        assertTrue(CodeScanner.referencesMethod(classBytes(PERSISTENT),
                        PROJECTILE.replace('.', '/'), "setOwner",
                        "(Lnet/minecraft/entity/Entity;)V"),
                "PersistentProjectileEntity#setOwner is expected to delegate to ProjectileEntity#setOwner; "
                        + "if that ever changes, the double-stamp analysis must be redone");
    }

    @Test
    @DisplayName("readCustomData is declared on both classes AND the subclass delegates to the superclass")
    void readCustomDataIsDeclaredOnBothClasses() throws Exception {
        // Corrected fact: an earlier version of this test asserted the subclass does NOT call the
        // superclass, and that a base hook would therefore miss arrows. Disassembly shows the opposite --
        // PersistentProjectileEntity#readCustomData begins with invokespecial
        // ProjectileEntity.readCustomData -- so BOTH hooks fire for an arrow.
        assertTrue(CodeScanner.findCodeLengthByName(classBytes(PROJECTILE), "readCustomData") > 0,
                "ProjectileEntity must declare readCustomData(ReadView)");
        assertTrue(CodeScanner.findCodeLengthByName(classBytes(PERSISTENT), "readCustomData") > 0,
                "PersistentProjectileEntity must declare its OWN readCustomData(ReadView)");
        assertTrue(CodeScanner.referencesMethod(classBytes(PERSISTENT),
                        PROJECTILE.replace('.', '/'), "readCustomData",
                        "(Lnet/minecraft/storage/ReadView;)V"),
                "the subclass readCustomData is expected to delegate to the superclass, so both hooks fire; "
                        + "if that ever changes the duplicate-firing analysis must be redone");

        Path config = Path.of("src", "main", "resources", "noncreatureaffinity.mixins.json");
        String configText = Files.readString(config, StandardCharsets.UTF_8);
        for (String mixin : new String[]{"ProjectileStorageStampMixin",
                "PersistentProjectileStorageStampMixin"}) {
            assertTrue(configText.contains("\"" + mixin + "\""),
                    mixin + " is not registered, so its injection never applies");
            Path source = Path.of("src", "main", "java", "com", "nonid", "affinity", "mixin",
                    mixin + ".java");
            assertTrue(Files.isRegularFile(source), "missing mixin source: " + mixin);
            String text = Files.readString(source, StandardCharsets.UTF_8);
            assertTrue(text.contains("method = \"readCustomData\""),
                    mixin + " must anchor on readCustomData, the storage-read entry point");
            assertTrue(text.contains("onProjectileLoadedFromStorage"),
                    mixin + " must route through the production invalidation method");
        }
    }

    @Test
    @DisplayName("setRemoved is the one entry every removal funnels through, and it is final")
    void setRemovedIsTheSingleRemovalEntry() throws Exception {
        // The removal hook used to sit on Entity#remove, which only a genuine destroy goes through:
        // Entity#removeFromDimension calls setRemoved(CHANGED_DIMENSION) directly, and the chunk-unload
        // path in ServerEntityManager calls setRemoved(UNLOADED_TO_CHUNK) directly. An unloaded projectile
        // therefore never reached the hook while a destroyed one did.
        byte[] entity = classBytes("net.minecraft.entity.Entity");
        assertTrue(CodeScanner.findCodeLengthByName(entity, "setRemoved") > 0,
                "Entity must declare setRemoved(RemovalReason), the hook's anchor");
        assertTrue(CodeScanner.findCodeLengthByName(entity, "remove") > 0,
                "Entity must also declare remove, which delegates to setRemoved");

        // Neither projectile class declares either method, so the anchors live on Entity.
        for (String cls : new String[]{PROJECTILE, PERSISTENT}) {
            assertEquals(-1, CodeScanner.findCodeLengthByName(classBytes(cls), "setRemoved"),
                    cls + " must NOT declare setRemoved; if it ever does, the hook must be re-checked");
            assertEquals(-1, CodeScanner.findCodeLengthByName(classBytes(cls), "remove"),
                    cls + " must NOT declare remove");
        }

        Path removal = Path.of("src", "main", "java", "com", "nonid", "affinity", "mixin",
                "ProjectileRemovalStampMixin.java");
        String text = Files.readString(removal, StandardCharsets.UTF_8);
        assertTrue(text.contains("setRemoved"),
                "the removal hook must anchor on setRemoved, the single funnel point");
        assertTrue(text.contains("onProjectileInstanceRemoved"),
                "and pass the INSTANCE, because a dimension change gives two entities one uuid");
    }

    @Test
    @DisplayName("remove is declared on Entity and routes through setRemoved, not on the projectile classes")
    void removeIsDeclaredOnEntityOnly() throws Exception {
        // This is the assertion that would have caught a real load failure. The removal hook was first
        // written as an injection into "remove" on ProjectileEntity, but neither ProjectileEntity nor
        // PersistentProjectileEntity DECLARES that method -- it comes from Entity -- so Mixin found no
        // target and aborted the whole mixin at load. The dedicated-server boot caught it; this catches it
        // in the ordinary test run instead.
        int onEntity = CodeScanner.findCodeLengthByName(classBytes("net.minecraft.entity.Entity"),
                "remove");
        assertTrue(onEntity > 0, "Entity must declare remove(RemovalReason), the real injection target");

        assertEquals(-1, CodeScanner.findCodeLengthByName(classBytes(PROJECTILE), "remove"),
                "ProjectileEntity must NOT declare remove; if it ever does, the hook must be re-checked "
                        + "for a silent no-op");
        assertEquals(-1, CodeScanner.findCodeLengthByName(classBytes(PERSISTENT), "remove"),
                "PersistentProjectileEntity must NOT declare remove either");

        // And the removal mixin must be registered, on the declaring class.
        Path removal = Path.of("src", "main", "java", "com", "nonid", "affinity", "mixin",
                "ProjectileRemovalStampMixin.java");
        assertTrue(Files.isRegularFile(removal), "the removal hook must exist");
        String text = Files.readString(removal, StandardCharsets.UTF_8);
        assertEquals("net.minecraft.entity.Entity", declaredMixinTarget(text),
                "the removal hook must target the class that declares remove");
    }

    private static String declaredMixinTarget(String source) {
        var matcher = java.util.regex.Pattern.compile("@Mixin\\((\\w+)\\.class\\)").matcher(source);
        assertTrue(matcher.find(), "the mixin must declare a @Mixin target");
        String simpleName = matcher.group(1);
        var imports = java.util.regex.Pattern
                .compile("import (net\\.minecraft\\.[\\w.]*\\.%s);".formatted(simpleName))
                .matcher(source);
        assertTrue(imports.find(), "the @Mixin target must be imported explicitly, not wildcarded");
        return imports.group(1);
    }

    @Test
    @DisplayName("the damage site reads the projectile's own stamp, not the live generation")
    void damageSiteUsesTheProjectileStamp() throws Exception {
        // This is the property that makes the whole ranged path meaningful: the generation must come FROM
        // the projectile. A damage handler that read the current generation instead would verify nothing,
        // and the difference is invisible in a passing test suite otherwise.
        Path source = Path.of("src", "main", "java", "com", "nonid", "affinity", "service",
                "CompanionDamageEvents.java");
        String text = Files.readString(source, StandardCharsets.UTF_8);
        assertTrue(text.contains("onCompanionProjectileHit"),
                "the damage site must route ranged hits through the projectile path");        assertTrue(text.contains("getSource() instanceof ProjectileEntity"),
                "the damage source's own entity must be inspected to find the projectile");

        Path controller = Path.of("src", "main", "java", "com", "nonid", "affinity", "service",
                "CompanionCombatController.java");
        String controllerText = Files.readString(controller, StandardCharsets.UTF_8);
        assertTrue(controllerText.contains("stamp.generation()"),
                "the stamp's own generation must be the value verified on impact");
    }

    private static byte[] classBytes(String className) throws Exception {
        String resource = className.replace('.', '/') + ".class";
        try (var stream = ProjectileStampMixinContractTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(stream, "class not on the test classpath: " + className);
            return stream.readAllBytes();
        }
    }
}
