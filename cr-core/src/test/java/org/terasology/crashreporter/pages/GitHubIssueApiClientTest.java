// Copyright 2026 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import org.apache.http.HttpEntity;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitHubIssueApiClientTest {

    @Test
    void parseOwnerRepoExtractsBothParts() {
        assertArrayEquals(new String[] {"MovingBlocks", "Terasology"},
                GitHubIssueApiClient.parseOwnerRepo("https://github.com/MovingBlocks/Terasology/issues/new"));
    }

    @Test
    void parseOwnerRepoReturnsNullForNonGithubLink() {
        assertNull(GitHubIssueApiClient.parseOwnerRepo("https://example.com/issues/new"));
        assertNull(GitHubIssueApiClient.parseOwnerRepo(null));
    }

    @Test
    void createIssueReturnsTheHtmlUrlOnSuccess() throws IOException {
        CloseableHttpResponse resp = response(201, "{\"html_url\": \"https://github.com/MovingBlocks/Terasology/issues/123\"}");
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(client.execute(any(HttpUriRequest.class))).thenReturn(resp);

        URL issueUrl = GitHubIssueApiClient.createIssue(client, "token", "MovingBlocks", "Terasology", "t", "b");

        assertEquals("https://github.com/MovingBlocks/Terasology/issues/123", issueUrl.toString());
    }

    @Test
    void createIssueThrowsOnNon201Response() throws IOException {
        CloseableHttpResponse resp = response(401, "{\"message\": \"Bad credentials\"}");
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(client.execute(any(HttpUriRequest.class))).thenReturn(resp);

        assertThrows(IOException.class,
                () -> GitHubIssueApiClient.createIssue(client, "bad-token", "o", "r", "t", "b"));
    }

    private static CloseableHttpResponse response(int statusCode, String jsonBody) throws IOException {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        HttpEntity entity = new StringEntity(jsonBody);
        when(response.getEntity()).thenReturn(entity);
        StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), statusCode, "");
        when(response.getStatusLine()).thenReturn(statusLine);
        return response;
    }
}
