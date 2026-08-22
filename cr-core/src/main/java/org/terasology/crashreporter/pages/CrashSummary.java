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
 * Builds a pre-filled GitHub issue title/body from a crash: the exception itself (available
 * directly, no parsing needed), plus the engine version and active module list, which only exist
 * in the crashed process' own log output - the reporter runs in its own JVM (see #52, subprocess
 * isolation) and has no other way to reach them.
 * <p>
 * The regexes here mirror two fixed, narrow log lines the engine emits at startup - see
 * {@code TerasologyEngine#logEnvironmentInfo} ({@code TerasologyVersion#toString}'s
 * {@code [buildNumber=..., ..., engineVersion=X, displayVersion=Y]} format) and
 * {@code RegisterMods} ({@code "Activating module: <id>:<version>"}, once per active module).
 * Log formatting is not a published API and can drift; a change there degrades this to a blank
 * "unknown"/empty-list extract rather than failing the report itself.
 */
public final class CrashSummary {

    private static final int MAX_STACK_LINES = 15;
    private static final int MAX_MODULES_LISTED = 30;
    private static final int MAX_TITLE_MESSAGE_LENGTH = 80;

    private static final Pattern ENGINE_VERSION_PATTERN = Pattern.compile("engineVersion=([^,\\]]*)");
    private static final Pattern DISPLAY_VERSION_PATTERN = Pattern.compile("displayVersion=([^,\\]]*)");
    private static final Pattern ACTIVE_MODULE_PATTERN = Pattern.compile("Activating module: (\\S+:\\S+)");

    private final Throwable exception;
    private final String stackTraceExtract;
    private final String engineVersion;
    private final String displayVersion;
    private final List<String> activeModules;

    private CrashSummary(Throwable exception, String stackTraceExtract, String engineVersion,
                          String displayVersion, List<String> activeModules) {
        this.exception = exception;
        this.stackTraceExtract = stackTraceExtract;
        this.engineVersion = engineVersion;
        this.displayVersion = displayVersion;
        this.activeModules = activeModules;
    }

    public static CrashSummary extract(Throwable exception, String combinedLogText) {
        String text = combinedLogText != null ? combinedLogText : "";
        return new CrashSummary(exception, extractStackTrace(exception),
                firstGroup(ENGINE_VERSION_PATTERN, text), firstGroup(DISPLAY_VERSION_PATTERN, text),
                extractActiveModules(text));
    }

    private static String extractStackTrace(Throwable exception) {
        StringWriter sink = new StringWriter();
        exception.printStackTrace(new PrintWriter(sink));
        String[] lines = sink.toString().split("\r?\n");
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
     * @return a Markdown issue body with the exception extract, environment info, and a link to
     *         the full logs
     */
    public String buildBody(URL pastebinLink) {
        StringBuilder body = new StringBuilder();
        body.append("### Exception\n\n```\n").append(stackTraceExtract).append("\n```\n\n");

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
