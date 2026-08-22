// Copyright 2026 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for #53 item 3: "Report Issue" opened a blank GitHub form instead of one
 * pre-filled with the crash summary.
 */
class CrashSummaryTest {

    private static final String LOG_TEXT =
            "10:00:00.000 [main] INFO  o.t.e.version.TerasologyVersion - "
                    + "[buildNumber=42, buildId=42, buildTag=Terasology-42, buildUrl=, jobName=Terasology/engine/develop, "
                    + "dateTime=2026-08-20, displayVersion=Aeternum, engineVersion=5.4.0-SNAPSHOT]\n"
                    + "10:00:00.100 [main] INFO  o.t.e.core.TerasologyEngine - OS: Linux, arch: amd64, version: 6.12.85\n"
                    + "10:00:01.000 [main] INFO  o.t.e.core.modes.loadProcesses.RegisterMods - Activating module: engine:5.4.0-SNAPSHOT\n"
                    + "10:00:01.010 [main] INFO  o.t.e.core.modes.loadProcesses.RegisterMods - Activating module: CoreAssets:2.4.0\n"
                    + "10:00:01.020 [main] INFO  o.t.e.core.modes.loadProcesses.RegisterMods - Activating module: CoreAssets:2.4.0\n";

    @Test
    void extractsEngineAndDisplayVersion() {
        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), LOG_TEXT);
        String body = summary.buildBody(null);

        assertTrue(body.contains("5.4.0-SNAPSHOT"), "Expected the engine version in the body, got: " + body);
        assertTrue(body.contains("Aeternum"), "Expected the display version in the body, got: " + body);
    }

    @Test
    void extractsActiveModulesAndDeduplicates() {
        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), LOG_TEXT);
        String body = summary.buildBody(null);

        assertTrue(body.contains("engine:5.4.0-SNAPSHOT"), "Expected engine module, got: " + body);
        // Occurs twice in the log (duplicate "Activating module" line) - should appear once in the body.
        int occurrences = body.split("CoreAssets:2\\.4\\.0", -1).length - 1;
        assertEquals(1, occurrences, "Expected the duplicate module line deduplicated, got: " + body);
    }

    @Test
    void missingVersionAndModulesDegradeGracefullyInsteadOfFailing() {
        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), "no relevant lines here");
        String body = summary.buildBody(null);

        assertTrue(body.contains("unknown"), "Expected a fallback for a missing version, got: " + body);
        assertTrue(body.contains("none found in logs"), "Expected a fallback for an empty module list, got: " + body);
    }

    @Test
    void titleUsesExceptionClassAndMessage() {
        CrashSummary summary = CrashSummary.extract(new IllegalStateException("world was null"), LOG_TEXT);

        assertEquals("Crash: IllegalStateException: world was null", summary.buildTitle());
    }

    @Test
    void bodyIncludesTheExceptionExtract() {
        CrashSummary summary = CrashSummary.extract(new RuntimeException("kaboom"), LOG_TEXT);
        String body = summary.buildBody(null);

        assertTrue(body.contains("kaboom"), "Expected the exception message in the body, got: " + body);
        assertTrue(body.contains("RuntimeException"), "Expected the exception type in the body, got: " + body);
    }

    @Test
    void bodyIncludesThePastebinLinkWhenUploaded() throws MalformedURLException {
        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), LOG_TEXT);
        URL link = new URL("https://pastebin.com/abc123");

        String body = summary.buildBody(link);

        assertTrue(body.contains("https://pastebin.com/abc123"), "Expected the PasteBin link in the body, got: " + body);
    }

    @Test
    void bodyNotesWhenUploadWasSkipped() {
        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), LOG_TEXT);

        String body = summary.buildBody(null);

        assertFalse(body.contains("null"), "A skipped upload must not leak the literal string \"null\" into the body: " + body);
        assertTrue(body.contains("not uploaded"), "Expected a note that upload was skipped, got: " + body);
    }

    // Regression: ErrorMessagePanel#getLog() combines every log tab, not just the one that
    // triggered the report, but only the in-process exception ever made it into the pre-filled
    // issue - an exception logged in a different tab (e.g. an earlier init-time failure) was
    // silently left out even though it was right there in the combined text.
    @Test
    void bodyListsExceptionsFoundInOtherLogTabs() {
        String combinedLog = "=== Terasology-init.log ===\n" + LOG_TEXT
                + "\n=== Terasology-game.log ===\n"
                + "10:10:05.123 [main] ERROR o.t.e.core.TerasologyEngine - Uncaught exception in main loop\n"
                + "java.lang.NullPointerException: world was null\n"
                + "\tat org.terasology.engine.core.TerasologyEngine.run(TerasologyEngine.java:200)\n";

        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), combinedLog);
        String body = summary.buildBody(null);

        assertTrue(body.contains("### Other exceptions found in logs"), "Expected a section listing it, got: " + body);
        assertTrue(body.contains("Terasology-game.log: java.lang.NullPointerException: world was null"),
                "Expected the other tab's exception attributed to its tab, got: " + body);
    }

    @Test
    void bodyDoesNotDuplicateThePrimaryExceptionAsAnOtherException() {
        RuntimeException primary = new RuntimeException("boom");
        // The crash is very often also logged (by the crashed process itself) in one of its own
        // log tabs - that's the same exception, not another one, and must not be listed twice.
        String combinedLog = "=== Terasology-game.log ===\n"
                + primary + "\n"
                + "\tat org.terasology.engine.core.TerasologyEngine.run(TerasologyEngine.java:200)\n";

        CrashSummary summary = CrashSummary.extract(primary, combinedLog);
        String body = summary.buildBody(null);

        assertFalse(body.contains("### Other exceptions found in logs"),
                "The primary exception's own log entry must not be listed as an \"other\" exception, got: " + body);
    }

    @Test
    void bodyOmitsTheOtherExceptionsSectionWhenThereAreNone() {
        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), LOG_TEXT);
        String body = summary.buildBody(null);

        assertFalse(body.contains("### Other exceptions found in logs"), "Expected no such section, got: " + body);
    }
}
