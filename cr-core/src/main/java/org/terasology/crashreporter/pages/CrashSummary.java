// Copyright 2026 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * The version/module regexes here mirror two fixed, narrow log lines the engine emits at startup -
 * see {@code TerasologyEngine#logEnvironmentInfo} ({@code TerasologyVersion#toString}'s
 * {@code [buildNumber=..., ..., engineVersion=X, displayVersion=Y]} format) and
 * {@code RegisterMods} ({@code "Activating module: <id>:<version>"}, once per active module).
 * Log formatting is not a published API and can drift; a change there degrades this to a blank
 * "unknown"/empty-list extract rather than failing the report itself.
 */
public final class CrashSummary {

    private static final int MAX_STACK_LINES = 15;
    private static final int MAX_MODULES_LISTED = 30;
    private static final int MAX_TITLE_MESSAGE_LENGTH = 80;
    private static final int MAX_EXCEPTIONS_LISTED = 10;
    private static final int CONTEXT_LINES_BEFORE = 5;
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
    private final List<String> exceptionBlocks;
    private final int moreExceptionsCount;
    private final String engineVersion;
    private final String displayVersion;
    private final List<String> activeModules;

    private CrashSummary(Throwable exception, List<String> exceptionBlocks, int moreExceptionsCount,
                          String engineVersion, String displayVersion, List<String> activeModules) {
        this.exception = exception;
        this.exceptionBlocks = exceptionBlocks;
        this.moreExceptionsCount = moreExceptionsCount;
        this.engineVersion = engineVersion;
        this.displayVersion = displayVersion;
        this.activeModules = activeModules;
    }

    public static CrashSummary extract(Throwable exception, String combinedLogText) {
        String text = combinedLogText != null ? combinedLogText : "";

        List<String> blocks = buildExceptionBlocks(exception, text);
        int moreCount = 0;
        if (blocks.size() > MAX_EXCEPTIONS_LISTED) {
            moreCount = blocks.size() - MAX_EXCEPTIONS_LISTED;
            blocks = new ArrayList<>(blocks.subList(0, MAX_EXCEPTIONS_LISTED));
        }

        return new CrashSummary(exception, blocks, moreCount,
                firstGroup(ENGINE_VERSION_PATTERN, text), firstGroup(DISPLAY_VERSION_PATTERN, text),
                extractActiveModules(text));
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
