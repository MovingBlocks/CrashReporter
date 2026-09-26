// Copyright 2026 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates a GitHub issue via the REST API - a POST body, not a URL query string, so no length
 * limit (unlike {@link GitHubIssueLinkBuilder}). Needs an access token from
 * {@link GitHubDeviceLogin}.
 */
public final class GitHubIssueApiClient {

    private static final Pattern OWNER_REPO_PATTERN = Pattern.compile("github\\.com/([^/]+)/([^/]+)/");

    private GitHubIssueApiClient() {
    }

    /**
     * @return the created issue's URL
     */
    public static URL createIssue(CloseableHttpClient client, String token, String owner, String repo,
                                   String title, String body) throws IOException {
        HttpPost post = new HttpPost("https://api.github.com/repos/" + owner + "/" + repo + "/issues");
        post.setHeader("Authorization", "Bearer " + token);
        post.setHeader("Accept", "application/vnd.github+json");
        post.setHeader("X-GitHub-Api-Version", "2022-11-28");
        JSONObject requestJson = new JSONObject();
        requestJson.put("title", title);
        requestJson.put("body", body);
        post.setEntity(new StringEntity(requestJson.toString(), ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse response = client.execute(post)) {
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_CREATED) {
                throw new IOException("GitHub API error " + response.getStatusLine().getStatusCode() + ": " + responseBody);
            }
            return new URL(new JSONObject(responseBody).getString("html_url"));
        }
    }

    /**
     * @return {@code {owner, repo}} parsed from a link like
     *         {@code https://github.com/MovingBlocks/Terasology/issues/new}, or {@code null} if
     *         it doesn't look like a github.com repo link
     */
    public static String[] parseOwnerRepo(String reportIssueLink) {
        if (reportIssueLink == null) {
            return null;
        }
        Matcher matcher = OWNER_REPO_PATTERN.matcher(reportIssueLink);
        return matcher.find() ? new String[] {matcher.group(1), matcher.group(2)} : null;
    }
}
