package com.nonid.affinity.non;

import com.nonid.affinity.NonCreatureAffinity;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflection helpers for NeedsOfNature records.
 *
 * <p>Only used to read "compile-time visible but rename-prone" record components. If a future
 * NON release renames a component, this module falls back to candidate accessor names instead
 * of crashing at runtime.
 */
public final class NonReflect {

    private static final Map<Class<?>, Map<String, MethodHandle>> ACCESSOR_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHOD_CACHE = new HashMap<>();

    private NonReflect() {
    }

    /**
     * Read a property from an object: record style {@code name()} first, then bean style
     * {@code getName()}.
     *
     * @return the value, or {@code fallback} when it cannot be resolved
     */
    @SuppressWarnings("unchecked")
    public static <T> T getProperty(Object target, String name, T fallback) {
        if (target == null) {
            return fallback;
        }
        try {
            Object value = accessor(target.getClass(), name).invoke(target);
            return value == null ? fallback : (T) value;
        } catch (Throwable throwable) {
            if (NonCreatureAffinity.LOG_VERBOSE) {
                NonCreatureAffinity.LOGGER.warn("[NoN Affinity] cannot read property {}: {}",
                        name, throwable.toString());
            }
            return fallback;
        }
    }

    /** Read an int property with a fallback. */
    public static int getInt(Object target, String name, int fallback) {
        Object value = getProperty(target, name, null);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    /** Read a boolean property with a fallback. */
    public static boolean getBoolean(Object target, String name, boolean fallback) {
        Object value = getProperty(target, name, null);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static MethodHandle accessor(Class<?> type, String name)
            throws NoSuchMethodException, IllegalAccessException {
        Map<String, MethodHandle> cache = ACCESSOR_CACHE.computeIfAbsent(type, key -> new ConcurrentHashMap<>());
        MethodHandle cached = cache.get(name);
        if (cached != null) {
            return cached;
        }
        MethodHandle handle = resolveAccessor(type, name);
        cache.put(name, handle);
        return handle;
    }

    private static MethodHandle resolveAccessor(Class<?> type, String name)
            throws NoSuchMethodException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        String capitalized = name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
        String[] candidates = {name, "get" + capitalized, "is" + capitalized};
        for (String candidate : candidates) {
            try {
                Method method = type.getMethod(candidate);
                if (method.getParameterCount() != 0) {
                    continue;
                }
                method.setAccessible(true);
                return lookup.unreflect(method).asType(MethodType.methodType(Object.class, Object.class));
            } catch (NoSuchMethodException ignored) {
                // Try the next candidate name.
            }
        }
        // Last resort: a public field with the same name.
        try {
            Field field = type.getField(name);
            return lookup.unreflectGetter(field).asType(MethodType.methodType(Object.class, Object.class));
        } catch (NoSuchFieldException exception) {
            throw new NoSuchMethodException(type.getName() + " has no property named " + name);
        }
    }

    /** Invoke a static method by reflection; returns null on failure. */
    public static Object invokeStatic(String className, String methodName, Class<?>[] parameterTypes, Object... args) {
        String key = className + "#" + methodName;
        try {
            Method method = METHOD_CACHE.computeIfAbsent(key, ignored -> {
                try {
                    Class<?> owner = Class.forName(className);
                    Method found = owner.getMethod(methodName, parameterTypes);
                    found.setAccessible(true);
                    return found;
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            });
            return method.invoke(null, args);
        } catch (Throwable throwable) {
            if (NonCreatureAffinity.LOG_VERBOSE) {
                NonCreatureAffinity.LOGGER.warn("[NoN Affinity] cannot invoke {}: {}", key, throwable.toString());
            }
            return null;
        }
    }

    /** Clear cached accessors, used on config reload. */
    public static void clearCaches() {
        ACCESSOR_CACHE.clear();
        METHOD_CACHE.clear();
    }
}
