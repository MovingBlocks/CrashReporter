// Copyright 2026 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Builds a GitHub new-issue URL pre-filled via the {@code title}/{@code body} query parameters
 * GitHub's issue-creation form accepts.
 */
public final class GitHubIssueLinkBuilder {

    private GitHubIssueLinkBuilder() {
    }

    public static String build(String baseUrl, String title, String body) {
        String query = "title=" + encode(title) + "&body=" + encode(body);
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
