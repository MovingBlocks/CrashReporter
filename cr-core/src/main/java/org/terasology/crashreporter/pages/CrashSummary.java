// Copyright 2026 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import org.terasology.crashreporter.GlobalProperties;
import org.terasology.crashreporter.GlobalProperties.KEY;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Builds a pre-filled GitHub issue title/body from a crash: the exception itself, plus every other
 * exception found across the crashed process' own log tabs, and the engine version/active module
 * list - the reporter runs in its own JVM (see #52, subprocess isolation) and has no other way to
 * reach any of the log-only information.
 * <p>
 * On macOS, that subprocess isolation means the in-process {@code Throwable} passed to
 * {@link #extract} is a best-effort reconstruction from just its class name and message (see
 * {@code CrashReporter#reconstructThrowable}) - its own stack trace points into the reporter's own
 * relaunch machinery, not the real crash site. Whenever the crash was also logged in one of the log
 * tabs (the normal case for an engine-level crash handler), the trace captured from that log text is
 * the real one and is used instead; the reconstructed exception's own trace is only a fallback for
 * when nothing better is available.
 * <p>
 * What counts as a version line or a module line is the hosting application's business: cr-core
 * is shared by Terasology and Destination Sol, whose logs look nothing alike. A {@link Profile},
 * built from the app's {@code crashreporter.properties}, carries those regexes plus the product
 * name and the issue-form field IDs; with no profile configured the summary degrades to the parts
 * every JVM app has (exceptions, OS, Java) rather than mislabelling another game's crash as a
 * Terasology one. Log formatting is not a published API and can drift; a pattern that stops
 * matching degrades to "unknown"/an empty list rather than failing the report itself.
 */
public final class CrashSummary {

    private static final int MAX_STACK_LINES = 15;
    private static final int MAX_MODULES_LISTED = 30;
    private static final int MAX_TITLE_MESSAGE_LENGTH = 80;
    private static final int MAX_EXCEPTIONS_LISTED = 10;
    private static final int CONTEXT_LINES_BEFORE = 5;
    private static final String NO_TAB_LABEL = "this crash";

    private static final Pattern LOG_TAB_PATTERN = Pattern.compile("(?m)^=== (.*) ===$");
    // A log-formatted stack trace: a "some.FullyQualified.NameException[: message]" header line
    // immediately followed by one or more "at ..."/"Caused by: ..." frame lines - the shape every
    // JVM logging framework prints a Throwable in (Logback's %ex, java.util.logging, a raw
    // printStackTrace()), regardless of which class emits it. "... N more" / "... N common frames
    // omitted" lines count as frames too: they sit between a cause chain's links, and a pattern that
    // stopped at them dropped every "Caused by:" after the first - which is the root cause.
    private static final Pattern STACK_TRACE_HEADER_PATTERN = Pattern.compile(
            "(?m)^([\\w$]+(?:\\.[\\w$]+)+(?:Exception|Error))(:[^\\n]*)?\\n"
                    + "((?:[ \\t]*(?:at |Caused by:|\\.\\.\\. \\d+ (?:more|common frames omitted))[^\\n]*\\n?)+)");

    private final Profile profile;
    private final Throwable exception;
    private final List<String> exceptionBlocks;
    private final int moreExceptionsCount;
    private final String engineVersion;
    private final String displayVersion;
    private final List<String> activeModules;

    private CrashSummary(Profile profile, Throwable exception, List<String> exceptionBlocks, int moreExceptionsCount,
                          String engineVersion, String displayVersion, List<String> activeModules) {
        this.profile = profile;
        this.exception = exception;
        this.exceptionBlocks = exceptionBlocks;
        this.moreExceptionsCount = moreExceptionsCount;
        this.engineVersion = engineVersion;
        this.displayVersion = displayVersion;
        this.activeModules = activeModules;
    }

    /** Extracts with {@link Profile#GENERIC}: exceptions, OS and Java only. */
    public static CrashSummary extract(Throwable exception, String combinedLogText) {
        return extract(exception, combinedLogText, Profile.GENERIC);
    }

    public static CrashSummary extract(Throwable exception, String combinedLogText, Profile profile) {
        String text = combinedLogText != null ? combinedLogText : "";

        List<String> blocks = buildExceptionBlocks(exception, text);
        int moreCount = 0;
        if (blocks.size() > MAX_EXCEPTIONS_LISTED) {
            moreCount = blocks.size() - MAX_EXCEPTIONS_LISTED;
            blocks = new ArrayList<>(blocks.subList(0, MAX_EXCEPTIONS_LISTED));
        }

        return new CrashSummary(profile, exception, blocks, moreCount,
                firstGroup(profile.versionPattern, text), firstGroup(profile.displayVersionPattern, text),
                extractActiveModules(profile.modulePattern, text));
    }

    /**
     * Builds one Markdown block per distinct exception found - the one that triggered this report
     * first, then every other exception found across the log tabs - so a crash whose real cause is
     * an earlier exception logged in a different tab (e.g. during init) isn't left out of the
     * pre-filled issue just because it wasn't the in-process {@code exception} the reporter happened
     * to be invoked with.
     */
    private static List<String> buildExceptionBlocks(Throwable exception, String combinedLogText) {
        String primaryHeader = exception.toString().trim();
        List<ExceptionEntry> found = findAllExceptions(combinedLogText);

        ExceptionEntry primary = null;
        for (ExceptionEntry entry : found) {
            if (entry.header.equals(primaryHeader)) {
                primary = entry;
                break;
            }
        }
        // Not logged anywhere - fall back to the exception object's own trace. On macOS that trace
        // is a best-effort reconstruction (see the class javadoc) rather than the real crash site,
        // but it's all that's available. There's no log text to pull leading context from either.
        if (primary == null) {
            primary = new ExceptionEntry(null, primaryHeader, framesFromThrowable(exception), "");
        }

        List<String> blocks = new ArrayList<>();
        blocks.add(formatBlock(primary));
        for (ExceptionEntry entry : found) {
            if (entry.header.equals(primaryHeader)) {
                continue;
            }
            String block = formatBlock(entry);
            if (!blocks.contains(block)) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    private static String formatBlock(ExceptionEntry entry) {
        String label = entry.tabName != null ? entry.tabName : NO_TAB_LABEL;
        String combined = entry.frames.isEmpty() ? entry.header : entry.header + "\n" + entry.frames;
        String trace = truncateTrace(combined);
        // Context isn't part of the trace itself, so it's not subject to truncateTrace()'s
        // MAX_STACK_LINES cap - a few lines of what led up to the crash shouldn't cost trace detail.
        String content = entry.context.isEmpty() ? trace : entry.context + "\n" + trace;
        return "**" + label + "**\n\n```\n" + content + "\n```";
    }

    private static String truncateTrace(String combined) {
        String[] lines = combined.split("\r?\n");
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(lines.length, MAX_STACK_LINES);
        for (int i = 0; i < limit; i++) {
            builder.append(lines[i]).append('\n');
        }
        if (lines.length > limit) {
            builder.append("... ").append(lines.length - limit).append(" more line(s) - see the full log\n");
        }
        return builder.toString().trim();
    }

    private static String framesFromThrowable(Throwable exception) {
        StringWriter sink = new StringWriter();
        exception.printStackTrace(new PrintWriter(sink));
        String full = sink.toString();
        // printStackTrace()'s first line is exception.toString() - already the header - so only the
        // "at ..."/"Caused by: ..." frames after it are needed here.
        int newlineIndex = full.indexOf('\n');
        return newlineIndex >= 0 ? stripTrailingWhitespace(full.substring(newlineIndex + 1)) : "";
    }

    /**
     * Like {@link String#trim()} but only at the end - frame lines are indented with a leading tab
     * ({@code "\tat ..."}), which a plain {@code trim()} would strip from the first line along with
     * the trailing newline it's actually meant to remove.
     */
    private static String stripTrailingWhitespace(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }

    private static final class ExceptionEntry {
        private final String tabName;
        private final String header;
        private final String frames;
        private final String context;

        private ExceptionEntry(String tabName, String header, String frames, String context) {
            this.tabName = tabName;
            this.header = header;
            this.frames = frames;
            this.context = context;
        }
    }

    private static List<ExceptionEntry> findAllExceptions(String combinedLogText) {
        List<ExceptionEntry> found = new ArrayList<>();

        Matcher tabMatcher = LOG_TAB_PATTERN.matcher(combinedLogText);
        int tabStart = -1;
        String tabName = null;
        while (true) {
            boolean hasNext = tabMatcher.find();
            int nextStart = hasNext ? tabMatcher.start() : combinedLogText.length();
            if (tabName != null) {
                collectExceptionHeaders(combinedLogText.substring(tabStart, nextStart), tabName, found);
            }
            if (!hasNext) {
                break;
            }
            tabName = tabMatcher.group(1);
            // tabMatcher.end() lands right after "===", before that line's own terminator - skip it
            // too, so each tab's text starts at its real first content line instead of with a blank
            // artifact line (which precedingLines() would otherwise count as logged context).
            tabStart = tabMatcher.end();
            if (tabStart < combinedLogText.length() && combinedLogText.charAt(tabStart) == '\r') {
                tabStart++;
            }
            if (tabStart < combinedLogText.length() && combinedLogText.charAt(tabStart) == '\n') {
                tabStart++;
            }
        }
        // No "=== tab ===" headers at all - a single combined-log caller (e.g. a direct test) rather
        // than ErrorMessagePanel#getLog(); scan the whole text with no tab attribution.
        if (tabName == null) {
            collectExceptionHeaders(combinedLogText, null, found);
        }
        return found;
    }

    private static void collectExceptionHeaders(String tabText, String tabName, List<ExceptionEntry> found) {
        Matcher matcher = STACK_TRACE_HEADER_PATTERN.matcher(tabText);
        while (matcher.find()) {
            String header = (matcher.group(1) + (matcher.group(2) != null ? matcher.group(2) : "")).trim();
            String frames = stripTrailingWhitespace(matcher.group(3));
            String context = precedingLines(tabText, matcher.start(), CONTEXT_LINES_BEFORE);
            found.add(new ExceptionEntry(tabName, header, frames, context));
        }
    }

    /**
     * @return up to {@code maxLines} lines of whatever was logged right before {@code beforeIndex} in
     *         {@code text} - what led up to a crash is often as useful for diagnosing it as the trace
     *         itself, and it's only available here (the reporter's own {@link #exception} carries no
     *         log context of its own).
     */
    private static String precedingLines(String text, int beforeIndex, int maxLines) {
        String[] lines = text.substring(0, beforeIndex).split("\r?\n", -1);
        int end = lines.length;
        // A trailing empty element only ever means the substring ended in a newline - i.e. the line
        // right before the match, not a real blank log line - so it isn't context to show.
        if (end > 0 && lines[end - 1].isEmpty()) {
            end--;
        }
        int start = Math.max(0, end - maxLines);
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < end; i++) {
            builder.append(lines[i]).append('\n');
        }
        return stripTrailingWhitespace(builder.toString());
    }

    private static String firstGroup(Pattern pattern, String text) {
        if (pattern == null) {
            return "";
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static List<String> extractActiveModules(Pattern pattern, String text) {
        List<String> modules = new ArrayList<>();
        if (pattern == null) {
            return modules;
        }
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String module = matcher.group(1);
            if (!modules.contains(module)) {
                modules.add(module);
            }
        }
        return modules;
    }

    /**
     * @return a short, single-line issue title: the exception's simple class name, plus a
     *         truncated message if it has one.
     */
    public String buildTitle() {
        StringBuilder title = new StringBuilder("Crash: ").append(exception.getClass().getSimpleName());
        String message = exception.getLocalizedMessage();
        if (message != null && !message.trim().isEmpty()) {
            String trimmed = message.length() > MAX_TITLE_MESSAGE_LENGTH
                    ? message.substring(0, MAX_TITLE_MESSAGE_LENGTH - 3) + "..."
                    : message;
            title.append(": ").append(trimmed);
        }
        return title.toString();
    }

    /**
     * @param pastebinLink the uploaded log link, or {@code null} if the user skipped upload
     * @return a Markdown issue body: every exception found, one labeled code block each (naming the
     *         log tab it was found in), then environment info, then a link to the full logs
     */
    public String buildBody(URL pastebinLink) {
        StringBuilder body = new StringBuilder();

        body.append("### Exceptions\n\n");
        for (String block : exceptionBlocks) {
            body.append(block).append("\n\n");
        }
        if (moreExceptionsCount > 0) {
            body.append("... ").append(moreExceptionsCount).append(" more - see the full log\n\n");
        }

        body.append("### Environment\n\n");
        if (profile.extractsVersion()) {
            body.append("- ").append(profile.versionLabel()).append(": ").append(engineVersion.isEmpty() ? "unknown" : engineVersion);
            if (!displayVersion.isEmpty()) {
                body.append(" (").append(displayVersion).append(')');
            }
            body.append('\n');
        }
        body.append("- OS: ").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.version")).append(" (").append(System.getProperty("os.arch")).append(")\n");
        body.append("- Java: ").append(System.getProperty("java.version")).append('\n');
        if (profile.extractsModules()) {
            body.append("- Active modules:");
            if (activeModules.isEmpty()) {
                body.append(" none found in logs\n");
            } else {
                body.append('\n');
                int shown = Math.min(activeModules.size(), MAX_MODULES_LISTED);
                for (int i = 0; i < shown; i++) {
                    body.append("  - ").append(activeModules.get(i)).append('\n');
                }
                if (activeModules.size() > shown) {
                    body.append("  - ... ").append(activeModules.size() - shown).append(" more\n");
                }
            }
        }

        body.append("\n### Full logs\n\n");
        body.append(pastebinLink != null ? "[PasteBin](" + pastebinLink + ")\n" : "(not uploaded)\n");

        return body.toString();
    }

    /**
     * @param pastebinLink the uploaded log link, or {@code null} if the user skipped upload
     * @return field ID to value for the issue form named by
     *         {@link org.terasology.crashreporter.GlobalProperties.KEY#REPORT_ISSUE_TEMPLATE} - only
     *         meaningful when a downstream app has configured one (see
     *         {@link GitHubIssueLinkBuilder#build(String, String, String, Map)}). The IDs come from
     *         the {@link Profile}; a part with no configured ID, or nothing extractable, is omitted
     *         so the user's own blank field is left for them to fill in, rather than being
     *         pre-filled with something misleading like "unknown".
     */
    public Map<String, String> buildIssueFormFields(URL pastebinLink) {
        Map<String, String> fields = new LinkedHashMap<>();

        if (!engineVersion.isEmpty()) {
            String version = displayVersion.isEmpty() ? engineVersion : engineVersion + " (" + displayVersion + ")";
            putField(fields, Profile.FormField.VERSION, version);
        }
        putField(fields, Profile.FormField.OS, System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")");
        putField(fields, Profile.FormField.JAVA, System.getProperty("java.version"));

        StringBuilder actual = new StringBuilder();
        for (String block : exceptionBlocks) {
            actual.append(block).append("\n\n");
        }
        if (moreExceptionsCount > 0) {
            actual.append("... ").append(moreExceptionsCount).append(" more - see the full log\n");
        }
        putField(fields, Profile.FormField.DETAILS, actual.toString().trim());

        if (pastebinLink != null) {
            putField(fields, Profile.FormField.LOG, "[PasteBin](" + pastebinLink + ")");
        }

        if (!activeModules.isEmpty()) {
            StringBuilder modules = new StringBuilder("Active modules:\n");
            int shown = Math.min(activeModules.size(), MAX_MODULES_LISTED);
            for (int i = 0; i < shown; i++) {
                modules.append("- ").append(activeModules.get(i)).append('\n');
            }
            if (activeModules.size() > shown) {
                modules.append("- ... ").append(activeModules.size() - shown).append(" more\n");
            }
            putField(fields, Profile.FormField.EXTRA, modules.toString().trim());
        }

        return fields;
    }

    private void putField(Map<String, String> fields, Profile.FormField field, String value) {
        String id = profile.fieldId(field);
        if (id != null) {
            fields.put(id, value);
        }
    }

    /**
     * What the hosting application tells CrashSummary about its logs and its issue form. Built
     * from {@link GlobalProperties} by {@link #from}; cr-core's own defaults configure none of it.
     */
    public static final class Profile {

        /** Which of the summary's parts a configured issue-form field ID receives. */
        public enum FormField { VERSION, OS, JAVA, DETAILS, LOG, EXTRA }

        /** No product name, no log patterns, no form fields: exceptions, OS and Java only. */
        public static final Profile GENERIC = new Profile(null, null, null, null, Collections.<FormField, String>emptyMap());

        private final String productName;
        private final Pattern versionPattern;
        private final Pattern displayVersionPattern;
        private final Pattern modulePattern;
        private final Map<FormField, String> formFieldIds;

        /**
         * @param productName shown as "{productName} version" in the body; null for a plain "Version"
         * @param versionRegex regex whose group 1 is the version, or null to extract none
         * @param displayVersionRegex regex whose group 1 is a display name for the version, or null
         * @param moduleRegex regex whose group 1 is one active module, matched repeatedly, or null
         * @param formFieldIds issue-form field ID per part; parts without an ID are not pre-filled
         */
        public Profile(String productName, String versionRegex, String displayVersionRegex, String moduleRegex,
                       Map<FormField, String> formFieldIds) {
            this.productName = productName;
            this.versionPattern = compileOrNull(versionRegex, KEY.CRASH_SUMMARY_VERSION_PATTERN);
            this.displayVersionPattern = compileOrNull(displayVersionRegex, KEY.CRASH_SUMMARY_DISPLAY_VERSION_PATTERN);
            this.modulePattern = compileOrNull(moduleRegex, KEY.CRASH_SUMMARY_MODULE_PATTERN);
            this.formFieldIds = new EnumMap<>(FormField.class);
            for (Map.Entry<FormField, String> entry : formFieldIds.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    this.formFieldIds.put(entry.getKey(), entry.getValue());
                }
            }
        }

        public static Profile from(GlobalProperties properties) {
            Map<FormField, String> fields = new EnumMap<>(FormField.class);
            fields.put(FormField.VERSION, properties.get(KEY.REPORT_ISSUE_FIELD_VERSION));
            fields.put(FormField.OS, properties.get(KEY.REPORT_ISSUE_FIELD_OS));
            fields.put(FormField.JAVA, properties.get(KEY.REPORT_ISSUE_FIELD_JAVA));
            fields.put(FormField.DETAILS, properties.get(KEY.REPORT_ISSUE_FIELD_DETAILS));
            fields.put(FormField.LOG, properties.get(KEY.REPORT_ISSUE_FIELD_LOG));
            fields.put(FormField.EXTRA, properties.get(KEY.REPORT_ISSUE_FIELD_EXTRA));
            return new Profile(properties.get(KEY.CRASH_SUMMARY_PRODUCT_NAME),
                    properties.get(KEY.CRASH_SUMMARY_VERSION_PATTERN),
                    properties.get(KEY.CRASH_SUMMARY_DISPLAY_VERSION_PATTERN),
                    properties.get(KEY.CRASH_SUMMARY_MODULE_PATTERN), fields);
        }

        // A typo in a downstream app's regex must not take the whole crash dialog down with it;
        // it degrades to "not extracted", and says so once on stderr.
        private static Pattern compileOrNull(String regex, KEY key) {
            if (regex == null || regex.isEmpty()) {
                return null;
            }
            try {
                return Pattern.compile(regex);
            } catch (PatternSyntaxException e) {
                System.err.println("Ignoring invalid " + key + " regex: " + e.getMessage());
                return null;
            }
        }

        String versionLabel() {
            return productName != null && !productName.isEmpty() ? productName + " version" : "Version";
        }

        boolean extractsVersion() {
            return versionPattern != null;
        }

        boolean extractsModules() {
            return modulePattern != null;
        }

        String fieldId(FormField field) {
            return formFieldIds.get(field);
        }
    }
}
