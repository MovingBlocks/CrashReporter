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

    // GitHub rejects the whole URL past this. See github/docs#5136, crashreporter#58.
    private static final int GITHUB_URL_BYTE_LIMIT = 8191;
    // Safety margin.
    private static final int URL_BYTE_BUDGET = GITHUB_URL_BYTE_LIMIT - 200;
    private static final String TRUNCATED_SUFFIX = "\n... truncated, see the full log";

    private GitHubIssueLinkBuilder() {
    }

    public static String build(String baseUrl, String title, String body) {
        if (baseUrl == null) {
            return null;
        }
        String separator = baseUrl.contains("?") ? "&" : "?";
        String encodedTitle = encode(title);
        int budget = URL_BYTE_BUDGET - baseUrl.length() - separator.length()
                - "title=".length() - encodedTitle.length() - "&body=".length();
        String encodedBody = fitToBudget(body, budget);
        String query = "title=" + encodedTitle + "&body=" + (encodedBody != null ? encodedBody : "");
        return baseUrl + separator + query;
    }

    /**
     * @param template issue form filename under {@code .github/ISSUE_TEMPLATE/}
     * @param fields field ID to value. Null/empty value: field omitted. Too long: truncated or
     *         dropped, whichever fits.
     */
    public static String build(String baseUrl, String template, String title, Map<String, String> fields) {
        if (baseUrl == null) {
            return null;
        }
        String separator = baseUrl.contains("?") ? "&" : "?";
        StringBuilder query = new StringBuilder("template=").append(encode(template))
                .append("&title=").append(encode(title));
        int budget = URL_BYTE_BUDGET - baseUrl.length() - separator.length() - query.length();

        for (Map.Entry<String, String> field : fields.entrySet()) {
            String value = field.getValue();
            if (value == null || value.isEmpty()) {
                continue;
            }
            String key = field.getKey();
            int overhead = key.length() + 2; // '&' + key + '='
            String encoded = fitToBudget(value, budget - overhead);
            if (encoded == null) {
                continue;
            }
            query.append('&').append(key).append('=').append(encoded);
            budget -= overhead + encoded.length();
        }
        return baseUrl + separator + query;
    }

    /**
     * URL-encodes {@code value}, truncating with {@link #TRUNCATED_SUFFIX} to fit {@code maxBytes}.
     * Null if even the suffix doesn't fit. Truncates the raw text first, then encodes - never
     * splits mid-escape.
     */
    private static String fitToBudget(String value, int maxBytes) {
        String encoded = encode(value);
        if (encoded.length() <= maxBytes) {
            return encoded;
        }
        String suffix = encode(TRUNCATED_SUFFIX);
        if (suffix.length() > maxBytes) {
            return null;
        }
        String truncated = value;
        String withSuffix;
        do {
            truncated = truncated.substring(0, truncated.length() - 1);
            withSuffix = encode(truncated) + suffix;
        } while (!truncated.isEmpty() && withSuffix.length() > maxBytes);
        return truncated.isEmpty() ? null : withSuffix;
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
