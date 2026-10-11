package com.nonid.affinity.non;

import com.nonid.affinity.NonCreatureAffinity;

/**
 * Logging for the NeedsOfNature bridge.
 *
 * <p>Warnings here always go to the log because they explain why an interaction did not
 * happen (missing content pack, unmet conditions). Debug lines follow the config flag.
 */
final class NoNLog {

    private NoNLog() {
    }

    static void warn(String format, Object... args) {
        NonCreatureAffinity.LOGGER.warn("[NoN Affinity] " + format, args);
    }

    /**
     * A warning that must carry a stack trace.
     *
     * <p>An exception caught on a diagnostic path is the only record of WHERE the engine refused. Logging just its
     * type and message names the failure but not the call site, and a stack trace is what turns "the engine said no"
     * into a locatable line in the other mod.
     *
     * <p>Passed as the last argument, which slf4j treats as the exception rather than as a substitution value, so the
     * stack trace is printed instead of being stringified into the message.
     */
    static void warn(String format, Object arg1, Object arg2, Object arg3, Object arg4, Throwable thrown) {
        NonCreatureAffinity.LOGGER.warn("[NoN Affinity] " + format, arg1, arg2, arg3, arg4, thrown);
    }

    static void info(String format, Object... args) {
        NonCreatureAffinity.LOGGER.info("[NoN Affinity] " + format, args);
    }

    static void debug(String format, Object... args) {
        if (NonCreatureAffinity.LOG_VERBOSE) {
            NonCreatureAffinity.LOGGER.info("[NoN Affinity] " + format, args);
        }
    }
}
