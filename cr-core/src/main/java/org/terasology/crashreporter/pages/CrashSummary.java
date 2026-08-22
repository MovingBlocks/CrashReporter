// Copyright 2026 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a pre-filled GitHub issue title/body from a crash: the exception itself (available
 * directly, no parsing needed), plus every other exception found across the crashed process' own
 * log tabs, and the engine version/active module list - the reporter runs in its own JVM (see #52,
 * subprocess isolation) and has no other way to reach any of the log-only information.
 * <p>
 * The regexes here mirror two fixed, narrow log lines the engine emits at startup - see
 * {@code TerasologyEngine#logEnvironmentInfo} ({@code TerasologyVersion#toString}'s
 * {@code [buildNumber=..., ..., engineVersion=X, displayVersion=Y]} format) and
 * {@code RegisterMods} ({@code "Activating module: <id>:<version>"}, once per active module).
 * Log formatting is not a published API and can drift; a change there degrades this to a blank
 * "unknown"/empty-list extract rather than failing the report itself.
 */
public final class CrashSummary {

    private static final int MAX_MODULES_LISTED = 30;
    private static final int MAX_TITLE_MESSAGE_LENGTH = 80;
    private static final int MAX_EXCEPTIONS_LISTED = 10;
    private static final String NO_TAB_LABEL = "this crash";

    private static final Pattern ENGINE_VERSION_PATTERN = Pattern.compile("engineVersion=([^,\\]]*)");
    private static final Pattern DISPLAY_VERSION_PATTERN = Pattern.compile("displayVersion=([^,\\]]*)");
    private static final Pattern ACTIVE_MODULE_PATTERN = Pattern.compile("Activating module: (\\S+:\\S+)");
    private static final Pattern LOG_TAB_PATTERN = Pattern.compile("(?m)^=== (.*) ===$");
    // A log-formatted stack trace: a "some.FullyQualified.NameException[: message]" header line
    // immediately followed by one or more "at ..."/"Caused by: ..." frame lines - the shape every
    // JVM logging framework prints a Throwable in (Logback's %ex, java.util.logging, a raw
    // printStackTrace()), regardless of which class emits it.
    private static final Pattern STACK_TRACE_HEADER_PATTERN = Pattern.compile(
            "(?m)^([\\w$]+(?:\\.[\\w$]+)+(?:Exception|Error))(:[^\\n]*)?\\n((?:[ \\t]*(?:at |Caused by:)[^\\n]*\\n?)+)");

    private final Throwable exception;
    private final List<String> exceptionRows;
    private final int moreExceptionsCount;
    private final String engineVersion;
    private final String displayVersion;
    private final List<String> activeModules;

    private CrashSummary(Throwable exception, List<String> exceptionRows, int moreExceptionsCount,
                          String engineVersion, String displayVersion, List<String> activeModules) {
        this.exception = exception;
        this.exceptionRows = exceptionRows;
        this.moreExceptionsCount = moreExceptionsCount;
        this.engineVersion = engineVersion;
        this.displayVersion = displayVersion;
        this.activeModules = activeModules;
    }

    public static CrashSummary extract(Throwable exception, String combinedLogText) {
        String text = combinedLogText != null ? combinedLogText : "";

        List<String> rows = buildExceptionRows(exception, text);
        int moreCount = 0;
        if (rows.size() > MAX_EXCEPTIONS_LISTED) {
            moreCount = rows.size() - MAX_EXCEPTIONS_LISTED;
            rows = new ArrayList<>(rows.subList(0, MAX_EXCEPTIONS_LISTED));
        }

        return new CrashSummary(exception, rows, moreCount,
                firstGroup(ENGINE_VERSION_PATTERN, text), firstGroup(DISPLAY_VERSION_PATTERN, text),
                extractActiveModules(text));
    }

    /**
     * Builds one row per distinct exception found - the one that triggered this report first
     * (attributed to whichever log tab also logged it, if any - see {@link #NO_TAB_LABEL}), then
     * every other exception found across the log tabs, so a crash whose real cause is an earlier
     * exception logged in a different tab (e.g. during init) isn't left out of the pre-filled issue
     * just because it wasn't the in-process {@code exception} the reporter happened to be invoked
     * with.
     */
    private static List<String> buildExceptionRows(Throwable exception, String combinedLogText) {
        String primaryHeader = exception.toString().trim();
        List<ExceptionEntry> found = findAllExceptions(combinedLogText);

        String primaryTab = null;
        for (ExceptionEntry entry : found) {
            if (entry.header.equals(primaryHeader)) {
                primaryTab = entry.tabName;
                break;
            }
        }

        List<String> rows = new ArrayList<>();
        rows.add(formatRow(primaryTab, primaryHeader));
        for (ExceptionEntry entry : found) {
            if (entry.header.equals(primaryHeader)) {
                continue;
            }
            String row = formatRow(entry.tabName, entry.header);
            if (!rows.contains(row)) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static String formatRow(String tabName, String header) {
        String label = tabName != null ? tabName : NO_TAB_LABEL;
        String message = header.length() > MAX_TITLE_MESSAGE_LENGTH
                ? header.substring(0, MAX_TITLE_MESSAGE_LENGTH - 3) + "..."
                : header;
        return "**" + label + "**: `" + message + "`";
    }

    private static final class ExceptionEntry {
        private final String tabName;
        private final String header;

        private ExceptionEntry(String tabName, String header) {
            this.tabName = tabName;
            this.header = header;
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
            tabStart = tabMatcher.end();
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
            found.add(new ExceptionEntry(tabName, header));
        }
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static List<String> extractActiveModules(String text) {
        List<String> modules = new ArrayList<>();
        Matcher matcher = ACTIVE_MODULE_PATTERN.matcher(text);
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
     * @return a Markdown issue body: every exception found (one row each, naming the log tab it was
     *         found in), then environment info, then a link to the full logs
     */
    public String buildBody(URL pastebinLink) {
        StringBuilder body = new StringBuilder();

        body.append("### Exceptions\n\n");
        for (String row : exceptionRows) {
            body.append("- ").append(row).append('\n');
        }
        if (moreExceptionsCount > 0) {
            body.append("- ... ").append(moreExceptionsCount).append(" more - see the full log\n");
        }
        body.append('\n');

        body.append("### Environment\n\n");
        body.append("- Terasology version: ").append(engineVersion.isEmpty() ? "unknown" : engineVersion);
        if (!displayVersion.isEmpty()) {
            body.append(" (").append(displayVersion).append(')');
        }
        body.append('\n');
        body.append("- OS: ").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.version")).append(" (").append(System.getProperty("os.arch")).append(")\n");
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

        body.append("\n### Full logs\n\n");
        body.append(pastebinLink != null ? "[PasteBin](" + pastebinLink + ")\n" : "(not uploaded)\n");

        return body.toString();
    }
}
