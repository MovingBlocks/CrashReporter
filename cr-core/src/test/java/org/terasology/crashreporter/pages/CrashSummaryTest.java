// Copyright 2026 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /**
     * The same patterns and field IDs cr-terasology's crashreporter.properties declares - kept in
     * step by hand, since cr-core's tests cannot see that module's resources.
     */
    private static CrashSummary.Profile terasology() {
        Map<CrashSummary.Profile.FormField, String> fields = new EnumMap<>(CrashSummary.Profile.FormField.class);
        fields.put(CrashSummary.Profile.FormField.VERSION, "terasology_version");
        fields.put(CrashSummary.Profile.FormField.OS, "operating_system");
        fields.put(CrashSummary.Profile.FormField.JAVA, "java_version");
        fields.put(CrashSummary.Profile.FormField.DETAILS, "actual_behavior");
        fields.put(CrashSummary.Profile.FormField.LOG, "log_details");
        fields.put(CrashSummary.Profile.FormField.EXTRA, "additional_context");
        return new CrashSummary.Profile("Terasology", "engineVersion=([^,\\]]*)", "displayVersion=([^,\\]]*)",
                "Activating module: (\\S+:\\S+)", fields);
    }

    @Test
    void genericProfileMentionsNoProductAndPreFillsNoFormFields() {
        // cr-core alone, or an app like cr-destsol with no log patterns configured: the body must
        // not claim "Terasology version: unknown" or "Active modules: none found in logs".
        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), LOG_TEXT);
        String body = summary.buildBody(null);

        assertFalse(body.contains("Terasology"), "Expected no product name under the generic profile, got: " + body);
        assertFalse(body.contains("version:"), "Expected no version line without a version pattern, got: " + body);
        assertFalse(body.contains("Active modules"), "Expected no module list without a module pattern, got: " + body);
        assertTrue(body.contains("- OS: ") && body.contains("- Java: "), "Expected OS and Java lines, got: " + body);
        assertTrue(summary.buildIssueFormFields(null).isEmpty(), "Expected no form fields without configured IDs");
    }

    @Test
    void profileFromCoreDefaultsIsGeneric() {
        // cr-core's own crashreporter_defaults.properties declares none of the CRASH_SUMMARY_* or
        // REPORT_ISSUE_FIELD_* keys, so a standalone cr-core gets exactly the generic behaviour.
        CrashSummary.Profile profile = CrashSummary.Profile.from(new org.terasology.crashreporter.GlobalProperties());
        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), LOG_TEXT, profile);

        assertFalse(summary.buildBody(null).contains("version:"));
        assertTrue(summary.buildIssueFormFields(null).isEmpty());
    }

    @Test
    void productNameLabelsTheVersionLine() {
        CrashSummary.Profile destSol = new CrashSummary.Profile("Destination Sol", "version=(\\S+)", null, null,
                new EnumMap<>(CrashSummary.Profile.FormField.class));
        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), "startup version=2.1.0 ok", destSol);

        assertTrue(summary.buildBody(null).contains("- Destination Sol version: 2.1.0"), summary.buildBody(null));
    }

    @Test
    void anInvalidConfiguredRegexDegradesToNotExtractedInsteadOfThrowing() {
        CrashSummary.Profile broken = new CrashSummary.Profile("X", "version=(unclosed", null, null,
                new EnumMap<>(CrashSummary.Profile.FormField.class));
        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), LOG_TEXT, broken);

        assertFalse(summary.buildBody(null).contains("version:"));
    }

    @Test
    void extractsEngineAndDisplayVersion() {
        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), LOG_TEXT, terasology());
        String body = summary.buildBody(null);

        assertTrue(body.contains("5.4.0-SNAPSHOT"), "Expected the engine version in the body, got: " + body);
        assertTrue(body.contains("Aeternum"), "Expected the display version in the body, got: " + body);
    }

    @Test
    void extractsActiveModulesAndDeduplicates() {
        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), LOG_TEXT, terasology());
        String body = summary.buildBody(null);

        assertTrue(body.contains("engine:5.4.0-SNAPSHOT"), "Expected engine module, got: " + body);
        // Occurs twice in the log (duplicate "Activating module" line) - should appear once in the body.
        int occurrences = body.split("CoreAssets:2\\.4\\.0", -1).length - 1;
        assertEquals(1, occurrences, "Expected the duplicate module line deduplicated, got: " + body);
    }

    @Test
    void missingVersionAndModulesDegradeGracefullyInsteadOfFailing() {
        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), "no relevant lines here", terasology());
        String body = summary.buildBody(null);

        assertTrue(body.contains("unknown"), "Expected a fallback for a missing version, got: " + body);
        assertTrue(body.contains("none found in logs"), "Expected a fallback for an empty module list, got: " + body);
    }

    @Test
    void titleUsesExceptionClassAndMessage() {
        CrashSummary summary = CrashSummary.extract(new IllegalStateException("world was null"), LOG_TEXT, terasology());

        assertEquals("Crash: IllegalStateException: world was null", summary.buildTitle());
    }

    @Test
    void bodyIncludesTheExceptionExtract() {
        CrashSummary summary = CrashSummary.extract(new RuntimeException("kaboom"), LOG_TEXT, terasology());
        String body = summary.buildBody(null);

        assertTrue(body.contains("kaboom"), "Expected the exception message in the body, got: " + body);
        assertTrue(body.contains("RuntimeException"), "Expected the exception type in the body, got: " + body);
    }

    @Test
    void bodyIncludesThePastebinLinkWhenUploaded() throws MalformedURLException {
        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), LOG_TEXT, terasology());
        URL link = new URL("https://pastebin.com/abc123");

        String body = summary.buildBody(link);

        assertTrue(body.contains("https://pastebin.com/abc123"), "Expected the PasteBin link in the body, got: " + body);
    }

    @Test
    void bodyNotesWhenUploadWasSkipped() {
        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), LOG_TEXT, terasology());

        String body = summary.buildBody(null);

        assertFalse(body.contains("null"), "A skipped upload must not leak the literal string \"null\" into the body: " + body);
        assertTrue(body.contains("not uploaded"), "Expected a note that upload was skipped, got: " + body);
    }

    // Regression: ErrorMessagePanel#getLog() combines every log tab, not just the one that
    // triggered the report, but only the in-process exception ever made it into the pre-filled
    // issue - an exception logged in a different tab (e.g. an earlier init-time failure) was
    // silently left out even though it was right there in the combined text. All exceptions - the
    // primary one and every other one found - are listed together, one labeled code block each
    // (full trace, not just a one-line header - a real fix needs the actual trace), before
    // "### Environment", not split across two separate sections.
    @Test
    void bodyListsExceptionsFoundInOtherLogTabsWithTheirFullTraceNamingTheTab() {
        String combinedLog = "=== Terasology-init.log ===\n" + LOG_TEXT
                + "\n=== Terasology-game.log ===\n"
                + "10:10:05.123 [main] ERROR o.t.e.core.TerasologyEngine - Uncaught exception in main loop\n"
                + "java.lang.NullPointerException: world was null\n"
                + "\tat org.terasology.engine.core.TerasologyEngine.run(TerasologyEngine.java:200)\n";

        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), combinedLog, terasology());
        String body = summary.buildBody(null);

        assertTrue(body.contains("### Exceptions"), "Expected a single unified exceptions section, got: " + body);
        assertTrue(body.contains("**Terasology-game.log**\n\n```\n"
                        + "10:10:05.123 [main] ERROR o.t.e.core.TerasologyEngine - Uncaught exception in main loop\n"
                        + "java.lang.NullPointerException: world was null\n"
                        + "\tat org.terasology.engine.core.TerasologyEngine.run(TerasologyEngine.java:200)\n```"),
                "Expected the other tab's exception as its own labeled block, with the line logged right before it and the full "
                        + "trace, got: " + body);
        int exceptionsIndex = body.indexOf("### Exceptions");
        int environmentIndex = body.indexOf("### Environment");
        assertTrue(exceptionsIndex >= 0 && environmentIndex > exceptionsIndex,
                "Expected \"### Exceptions\" before \"### Environment\", got: " + body);
    }

    @Test
    void bodyAttributesThePrimaryExceptionToItsOwnTabInsteadOfListingItTwice() {
        RuntimeException primary = new RuntimeException("boom");
        // The crash is very often also logged (by the crashed process itself) in one of its own
        // log tabs - that's the same exception, not another one, and must not be listed twice. It's
        // also the *real* trace (see the class javadoc on macOS reconstruction), so it must be used
        // in preference to the exception object's own (possibly fake) trace.
        String combinedLog = "=== Terasology-game.log ===\n"
                + primary + "\n"
                + "\tat org.terasology.engine.core.TerasologyEngine.run(TerasologyEngine.java:200)\n";

        CrashSummary summary = CrashSummary.extract(primary, combinedLog, terasology());
        String body = summary.buildBody(null);

        assertTrue(body.contains("**Terasology-game.log**\n\n```\njava.lang.RuntimeException: boom\n"
                        + "\tat org.terasology.engine.core.TerasologyEngine.run(TerasologyEngine.java:200)\n```"),
                "Expected the primary exception's block attributed to the tab it was found in, using that tab's real trace, got: "
                        + body);
        int blocks = body.split("\\*\\*Terasology-game\\.log\\*\\*", -1).length - 1;
        assertEquals(1, blocks, "Expected exactly one block - the primary exception must not also be listed as an \"other\" one: "
                + body);
    }

    @Test
    void bodyIncludesOnlyTheLastFiveLinesLoggedBeforeTheException() {
        StringBuilder combinedLog = new StringBuilder("=== Terasology-game.log ===\n");
        for (int i = 1; i <= 8; i++) {
            combinedLog.append("log line ").append(i).append('\n');
        }
        combinedLog.append("java.lang.NullPointerException: world was null\n")
                .append("\tat org.terasology.engine.core.TerasologyEngine.run(TerasologyEngine.java:200)\n");

        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), combinedLog.toString(), terasology());
        String body = summary.buildBody(null);

        assertFalse(body.contains("log line 1\n") || body.contains("log line 2\n") || body.contains("log line 3\n"),
                "Expected only the last 5 lines of context, not all 8, got: " + body);
        assertTrue(body.contains("log line 4\nlog line 5\nlog line 6\nlog line 7\nlog line 8\n"
                        + "java.lang.NullPointerException: world was null"),
                "Expected the last 5 lines directly before the exception's header, got: " + body);
    }

    @Test
    void bodyKeepsTheRootCauseOfAChainedExceptionAfterAMoreFramesLine() {
        // Logback and printStackTrace() both print "... N more" between a cause chain's links. A
        // frame pattern that stops there drops every "Caused by:" after the first - the root cause.
        String combinedLog = "=== Terasology-game.log ===\n"
                + "java.lang.IllegalStateException: wrapper\n"
                + "\tat org.terasology.engine.core.TerasologyEngine.run(TerasologyEngine.java:200)\n"
                + "\t... 12 more\n"
                + "Caused by: java.io.IOException: disk full\n"
                + "\tat java.base/java.io.FileOutputStream.write(FileOutputStream.java:100)\n"
                + "\t... 3 common frames omitted\n";

        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), combinedLog, terasology());
        String body = summary.buildBody(null);

        assertTrue(body.contains("Caused by: java.io.IOException: disk full"),
                "Expected the root cause after the '... N more' line, got: " + body);
        assertTrue(body.contains("... 3 common frames omitted"), "Expected the trailing omitted-frames line, got: " + body);
    }

    @Test
    void bodyFallsBackToTheExceptionsOwnTraceWhenNotFoundInAnyTab() {
        RuntimeException primary = new RuntimeException("boom");
        CrashSummary summary = CrashSummary.extract(primary, LOG_TEXT);
        String body = summary.buildBody(null);

        assertTrue(body.contains("**this crash**\n\n```\njava.lang.RuntimeException: boom\n"),
                "Expected a fallback label and the exception's own trace when it isn't found in any log tab, got: " + body);
        assertTrue(body.contains(CrashSummaryTest.class.getName()),
                "Expected this test's own stack frame in the fallback trace, got: " + body);
    }

    // buildIssueFormFields() feeds GitHubIssueLinkBuilder's template-based overload (see
    // GitHubIssueLinkBuilderTest) - used instead of buildBody() when a downstream app has configured
    // REPORT_ISSUE_TEMPLATE, landing the summary in that issue form's own fields.
    @Test
    void issueFormFieldsIncludeVersionOsAndTheExceptionBlocks() {
        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), LOG_TEXT, terasology());
        Map<String, String> fields = summary.buildIssueFormFields(null);

        assertEquals("5.4.0-SNAPSHOT (Aeternum)", fields.get("terasology_version"));
        assertTrue(fields.get("operating_system").contains(System.getProperty("os.name")), fields.get("operating_system"));
        assertEquals(System.getProperty("java.version"), fields.get("java_version"));
        assertTrue(fields.get("actual_behavior").contains("RuntimeException: boom"), fields.get("actual_behavior"));
    }

    @Test
    void issueFormFieldsOmitVersionWhenNotFoundInsteadOfSayingUnknown() {
        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), "no relevant lines here", terasology());
        Map<String, String> fields = summary.buildIssueFormFields(null);

        // Unlike buildBody()'s "unknown" fallback, an omitted field is left blank in the actual issue
        // form for the user to fill in themselves - "unknown" pre-filled into a real form field would
        // read as if the reporter deliberately couldn't tell, not as an untouched field.
        assertNull(fields.get("terasology_version"), "Expected no terasology_version entry, got: " + fields);
    }

    @Test
    void issueFormFieldsOmitLogDetailsWhenUploadWasSkipped() {
        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), LOG_TEXT, terasology());
        Map<String, String> fields = summary.buildIssueFormFields(null);

        assertNull(fields.get("log_details"), "Expected no log_details entry when nothing was uploaded, got: " + fields);
    }

    @Test
    void issueFormFieldsIncludeThePastebinLinkWhenUploaded() throws MalformedURLException {
        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), LOG_TEXT, terasology());
        URL link = new URL("https://pastebin.com/abc123");

        Map<String, String> fields = summary.buildIssueFormFields(link);

        assertTrue(fields.get("log_details").contains("https://pastebin.com/abc123"), fields.get("log_details"));
    }

    @Test
    void issueFormFieldsIncludeActiveModulesUnderAdditionalContext() {
        CrashSummary summary = CrashSummary.extract(new RuntimeException("boom"), LOG_TEXT, terasology());
        Map<String, String> fields = summary.buildIssueFormFields(null);

        assertTrue(fields.get("additional_context").contains("engine:5.4.0-SNAPSHOT"), fields.get("additional_context"));
    }
}
