package com.nonid.affinity.service;

import java.nio.charset.StandardCharsets;

/**
 * Minimal class-file reader: the Code attribute length of one method.
 *
 * <p>Exists so a test can tell a stub method from a real one. The mixin that stamps attacks was once
 * applied to {@code LivingEntity#tryAttack}, which the mapped bytecode shows is a no-op stub, while the
 * real body lives in {@code MobEntity#tryAttack}. Every unit test passed while the stamp was never
 * issued at all. Comparing the two {@code Code} lengths catches that class of mistake cheaply.
 *
 * <p>Deliberately not a general disassembler: it parses the constant pool far enough to resolve the two
 * strings a method reference needs, then walks to the requested method. Anything it cannot find reports
 * {@code -1} rather than guessing.
 */
final class CodeScanner {

    private CodeScanner() {
    }

    /**
     * The {@code Code} length of the first method with this name, whatever its descriptor, or -1.
     *
     * <p>Exists because a class on the test classpath cannot always be LINKED here: asking the JVM about
     * {@code MobEntity#tryAttack} threw {@code VerifyError} (a plain JUnit process has no Minecraft class
     * hierarchy to verify against). Reading the class file avoids linking it at all.
     */
    static int findCodeLengthByName(byte[] classBytes, String methodName) {
        return scan(classBytes, methodName, null);
    }

    /**
     * The length in bytes of the {@code Code} attribute of the named method, or -1 when not found.
     *
     * @param classBytes the raw .class file contents
     * @param methodName the method name as it appears in the constant pool
     * @param descriptor the JVM descriptor, e.g. {@code (Lnet/minecraft/server/world/ServerWorld;...)Z}
     */
    static int findCodeLength(byte[] classBytes, String methodName, String descriptor) {
        return scan(classBytes, methodName, descriptor);
    }

    /**
     * The internal name of a class's direct superclass, e.g. {@code net/minecraft/entity/projectile/ProjectileEntity}.
     *
     * <p>Read from the class file so a hierarchy claim can be checked without linking anything -- the same
     * reason the rest of this reader exists.
     */
    static String superClassName(byte[] classBytes) {
        try {
            int cpCount = u2(classBytes, 8);
            int[] pool = new int[cpCount];
            int offset = 10;
            for (int i = 1; i < cpCount; i++) {
                int tag = classBytes[offset] & 0xFF;
                pool[i] = offset;
                switch (tag) {
                    case 1 -> offset += 3 + u2(classBytes, offset + 1);
                    case 7, 8, 16, 19, 20 -> offset += 3;
                    case 15 -> offset += 4;
                    case 5, 6 -> {
                        offset += 9;
                        i++;
                    }
                    default -> offset += 5;
                }
            }
            // class file header: magic(4) minor(2) major(2) cp_count(2) [...pool...]
            // then access_flags(2), this_class(2), super_class(2).
            int superClassIndex = u2(classBytes, offset + 4);
            // super_class is a CONSTANT_Class, which does not hold the name itself: it holds an index to
            // the CONSTANT_Utf8 that does. Reading the entry directly returns something that is not a
            // Utf8 and therefore no name at all.
            if ((classBytes[pool[superClassIndex]] & 0xFF) != 7) {
                return "";
            }
            int nameIndex = u2(classBytes, pool[superClassIndex] + 1);
            return utf8(classBytes, pool, nameIndex);
        } catch (RuntimeException malformed) {
            return "";
        }
    }

    /**
     * Whether the constant pool of this class mentions the given method reference.
     *
     * <p>Used to check that a method delegates to its superclass implementation. A full bytecode walk
     * would be more precise, but the constant pool already distinguishes {@code invokespecial
     * Super.method} from a lookalike: the reference is only in the pool if the class actually names it.
     *
     * @param owner      internal name of the declaring class, e.g. {@code net/minecraft/entity/...}
     * @param methodName the method name
     * @param descriptor the JVM descriptor
     */
    static boolean referencesMethod(byte[] classBytes, String owner, String methodName,
                                    String descriptor) {
        String text = new String(classBytes, StandardCharsets.ISO_8859_1);
        return text.contains(owner) && text.contains(methodName) && text.contains(descriptor);
    }

    /**
     * The method invocations of one method, in bytecode order, as {@code owner#name}.
     *
     * <p>Exists so a test can pin the ORDER of two vanilla writes inside a single method. That ordering was
     * the whole defect behind a horse-taming bug: vanilla writes the owner before it sets the tame flag, so a
     * hook on the owner write observes {@code isTame() == false} on a first taming.
     *
     * <p>Honest limitation: this scans the {@code Code} array for the three-byte invoke patterns instead of
     * walking every instruction length, so an operand that happens to contain an invoke opcode byte could add
     * a phantom entry. Callers must therefore assert the EXACT sequence they expect; a phantom entry fails the
     * test loudly rather than being silently ignored.
     *
     * @return the invocation list, or an empty list when the method has no Code attribute
     */
    static java.util.List<String> invocations(byte[] classBytes, String methodName, String descriptor) {
        java.util.List<String> calls = new java.util.ArrayList<>();
        try {
            int[] pool = new int[0];
            int cpCount = u2(classBytes, 8);
            pool = new int[cpCount];
            int offset = 10;
            for (int i = 1; i < cpCount; i++) {
                int tag = classBytes[offset] & 0xFF;
                pool[i] = offset;
                switch (tag) {
                    case 1 -> offset += 3 + u2(classBytes, offset + 1);
                    case 7, 8, 16, 19, 20 -> offset += 3;
                    case 15 -> offset += 4;
                    case 5, 6 -> {
                        offset += 9;
                        i++;
                    }
                    default -> offset += 5;
                }
            }
            byte[] code = codeBytes(classBytes, pool, methodName, descriptor);
            if (code == null) {
                return calls;
            }
            for (int i = 0; i + 2 < code.length; i++) {
                int opcode = code[i] & 0xFF;
                if (opcode != 0xB6 && opcode != 0xB7 && opcode != 0xB8 && opcode != 0xB9) {
                    continue;
                }
                int refIndex = ((code[i + 1] & 0xFF) << 8) | (code[i + 2] & 0xFF);
                String resolved = resolveMethodRef(classBytes, pool, refIndex);
                if (resolved != null) {
                    calls.add(resolved);
                }
            }
        } catch (RuntimeException malformed) {
            return calls;
        }
        return calls;
    }

    /** Resolve a Methodref/InterfaceMethodref index to {@code owner#name}, or null when it is not one. */
    private static String resolveMethodRef(byte[] classBytes, int[] pool, int refIndex) {
        if (refIndex <= 0 || refIndex >= pool.length) {
            return null;
        }
        int refOffset = pool[refIndex];
        int tag = classBytes[refOffset] & 0xFF;
        if (tag != 10 && tag != 11) {
            return null;
        }
        int classIndex = u2(classBytes, refOffset + 1);
        int nameAndTypeIndex = u2(classBytes, refOffset + 3);
        if (classIndex <= 0 || classIndex >= pool.length
                || nameAndTypeIndex <= 0 || nameAndTypeIndex >= pool.length) {
            return null;
        }
        if ((classBytes[pool[classIndex]] & 0xFF) != 7) {
            return null;
        }
        String owner = utf8(classBytes, pool, u2(classBytes, pool[classIndex] + 1));
        String name = utf8(classBytes, pool, u2(classBytes, pool[nameAndTypeIndex] + 1));
        return owner.isEmpty() || name.isEmpty() ? null : owner + "#" + name;
    }

    /** The raw {@code Code} array of the named method, or null when it has none. */
    private static byte[] codeBytes(byte[] classBytes, int[] pool, String wantedMethod,
                                    String wantedDescriptor) {
        int cursor = methodTableStart(classBytes);
        int methods = u2(classBytes, cursor);
        cursor += 2;
        for (int i = 0; i < methods; i++) {
            String name = utf8(classBytes, pool, u2(classBytes, cursor + 2));
            String desc = utf8(classBytes, pool, u2(classBytes, cursor + 4));
            int attributes = u2(classBytes, cursor + 6);
            int attrCursor = cursor + 8;
            for (int a = 0; a < attributes; a++) {
                String attrName = utf8(classBytes, pool, u2(classBytes, attrCursor));
                int attrLength = u4(classBytes, attrCursor + 2);
                if ("Code".equals(attrName) && wantedMethod.equals(name)
                        && wantedDescriptor.equals(desc)) {
                    int codeLength = u4(classBytes, attrCursor + 10);
                    byte[] code = new byte[codeLength];
                    System.arraycopy(classBytes, attrCursor + 14, code, 0, codeLength);
                    return code;
                }
                attrCursor += 6 + attrLength;
            }
            cursor = attrCursor;
        }
        return null;
    }

    /** Offset of the method count, after the constant pool, header, interfaces and fields. */
    private static int methodTableStart(byte[] classBytes) {
        // The pool entries are variable length, so the table position has to be found by walking it.
        int cpCount = u2(classBytes, 8);
        int offset = 10;
        for (int i = 1; i < cpCount; i++) {
            int tag = classBytes[offset] & 0xFF;
            switch (tag) {
                case 1 -> offset += 3 + u2(classBytes, offset + 1);
                case 7, 8, 16, 19, 20 -> offset += 3;
                case 15 -> offset += 4;
                case 5, 6 -> {
                    offset += 9;
                    i++;
                }
                default -> offset += 5;
            }
        }
        int cursor = offset + 6;
        int interfaces = u2(classBytes, cursor);
        cursor += 2 + interfaces * 2;
        int fields = u2(classBytes, cursor);
        cursor += 2;
        for (int i = 0; i < fields; i++) {
            int attributes = u2(classBytes, cursor + 6);
            cursor += 8;
            for (int a = 0; a < attributes; a++) {
                cursor += 6 + u4(classBytes, cursor + 2);
            }
        }
        return cursor;
    }

    private static int scan(byte[] classBytes, String wantedMethod, String wantedDescriptor) {
        int[] pool = new int[0];
        try {
            int cpCount = u2(classBytes, 8);
            pool = new int[cpCount];
            // Pass 1: note where each Utf8 entry starts, so a later name comparison is a byte compare.
            int offset = 10;
            for (int i = 1; i < cpCount; i++) {
                int tag = classBytes[offset] & 0xFF;
                pool[i] = offset;
                switch (tag) {
                    case 1 -> {   // Utf8: tag, u2 length, bytes
                        offset += 3 + u2(classBytes, offset + 1);
                    }
                    case 7, 8, 16, 19, 20 -> offset += 3;
                    case 15 -> offset += 4;
                    case 5, 6 -> { // Long and Double take two pool slots
                        offset += 9;
                        i++;
                    }
                    default -> offset += 5;
                }
            }
            // Skip access flags, this/super, interfaces.
            int cursor = offset + 6;
            int interfaces = u2(classBytes, cursor);
            cursor += 2 + interfaces * 2;
            // Fields: access, name, descriptor, attributes.
            int fields = u2(classBytes, cursor);
            cursor += 2;
            for (int i = 0; i < fields; i++) {
                int attributes = u2(classBytes, cursor + 6);
                cursor += 8;
                for (int a = 0; a < attributes; a++) {
                    cursor += 6 + u4(classBytes, cursor + 2);
                }
            }
            // Methods.
            int methods = u2(classBytes, cursor);
            cursor += 2;
            for (int i = 0; i < methods; i++) {
                String name = utf8(classBytes, pool, u2(classBytes, cursor + 2));
                String desc = utf8(classBytes, pool, u2(classBytes, cursor + 4));
                int attributes = u2(classBytes, cursor + 6);
                int attrCursor = cursor + 8;
                int codeLength = -1;
                for (int a = 0; a < attributes; a++) {
                    String attrName = utf8(classBytes, pool, u2(classBytes, attrCursor));
                    int attrLength = u4(classBytes, attrCursor + 2);
                    if ("Code".equals(attrName)) {
                        // Code layout, measured from the attribute's name index:
                        //   +0 u2 attribute_name_index, +2 u4 attribute_length,
                        //   +6 u2 max_stack, +8 u2 max_locals, +10 u4 code_length.
                        codeLength = u4(classBytes, attrCursor + 10);
                    }
                    attrCursor += 6 + attrLength;
                }
                if (wantedMethod.equals(name)
                        && (wantedDescriptor == null || wantedDescriptor.equals(desc))) {
                    return codeLength;
                }
                cursor = attrCursor;
            }
        } catch (RuntimeException malformed) {
            // A class file this reader does not understand must not be reported as a stub, so the
            // caller sees "not found" and can decide what to do about it.
            return -1;
        }
        return -1;
    }

    private static int u2(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static int u4(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }

    private static String utf8(byte[] bytes, int[] pool, int index) {
        if (index <= 0 || index >= pool.length) {
            return "";
        }
        int offset = pool[index];
        if ((bytes[offset] & 0xFF) != 1) {
            return "";
        }
        int length = u2(bytes, offset + 1);
        return new String(bytes, offset + 3, length, StandardCharsets.UTF_8);
    }
}
