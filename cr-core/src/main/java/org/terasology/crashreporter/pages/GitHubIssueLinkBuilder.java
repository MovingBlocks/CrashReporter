// Copyright 2026 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

/**
 * Builds a GitHub new-issue URL, pre-filled either via the classic {@code title}/{@code body} query
 * parameters (works against any repo, regardless of what issue templates it has - the safe default),
 * or, when a downstream app has one, via an issue *form*'s own field IDs (see
 * {@link org.terasology.crashreporter.GlobalProperties.KEY#REPORT_ISSUE_TEMPLATE}) - a
 * {@code template=} query parameter plus one parameter per field ID, landing the crash summary in
 * the repo's own real template instead of overwriting it with a bespoke body.
 */
public final class GitHubIssueLinkBuilder {

    private GitHubIssueLinkBuilder() {
    }

    public static String build(String baseUrl, String title, String body) {
        if (baseUrl == null) {
            return null;
        }
        String query = "title=" + encode(title) + "&body=" + encode(body);
        return baseUrl + (baseUrl.contains("?") ? "&" : "?") + query;
    }

    /**
     * @param template the issue form's filename (e.g. {@code "crash-bug-report.yml"}), as it appears
     *         under {@code .github/ISSUE_TEMPLATE/} in the target repo
     * @param fields field ID to value - entries with a {@code null}/empty value are omitted, leaving
     *         that field for the user to fill in themselves rather than pre-filling it blank
     */
    public static String build(String baseUrl, String template, String title, Map<String, String> fields) {
        if (baseUrl == null) {
            return null;
        }
        StringBuilder query = new StringBuilder("template=").append(encode(template))
                .append("&title=").append(encode(title));
        for (Map.Entry<String, String> field : fields.entrySet()) {
            String value = field.getValue();
            if (value == null || value.isEmpty()) {
                continue;
            }
            query.append('&').append(field.getKey()).append('=').append(encode(value));
        }
        return baseUrl + (baseUrl.contains("?") ? "&" : "?") + query;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is a standard charset every JVM implementation is required to support.
            throw new AssertionError(e);
        }
    }
}
