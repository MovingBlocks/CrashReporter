// Copyright 2026 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitHubDeviceLoginTest {

    @Test
    void parseFormBodyDecodesKeysAndValues() {
        Map<String, String> fields = GitHubDeviceLogin.parseFormBody(
                "device_code=abc&user_code=WDJB-MJHT&verification_uri=https%3A%2F%2Fgithub.com%2Flogin%2Fdevice"
                        + "&expires_in=900&interval=5");

        assertEquals("abc", fields.get("device_code"));
        assertEquals("WDJB-MJHT", fields.get("user_code"));
        assertEquals("https://github.com/login/device", fields.get("verification_uri"));
        assertEquals("900", fields.get("expires_in"));
        assertEquals("5", fields.get("interval"));
    }

    @Test
    void parseFormBodyHandlesEmptyBody() {
        assertEquals(0, GitHubDeviceLogin.parseFormBody("").size());
    }

    @Test
    void requestDeviceCodeParsesTheResponse() throws IOException {
        CloseableHttpClient client = mockClientReturning(
                "device_code=abc&user_code=WDJB-MJHT&verification_uri=https%3A%2F%2Fgithub.com%2Flogin%2Fdevice"
                        + "&expires_in=900&interval=5");

        GitHubDeviceLogin.DeviceCode code = GitHubDeviceLogin.requestDeviceCode(client, "client-id");

        assertEquals("WDJB-MJHT", code.getUserCode());
        assertEquals("https://github.com/login/device", code.getVerificationUri());
    }

    @Test
    void requestDeviceCodeThrowsOnError() throws IOException {
        CloseableHttpClient client = mockClientReturning("error=access_denied&error_description=nope");

        assertThrows(IOException.class, () -> GitHubDeviceLogin.requestDeviceCode(client, "client-id"));
    }

    @Test
    void pollForAccessTokenReturnsTokenOnSuccess() throws IOException, InterruptedException {
        CloseableHttpClient client = mockClientReturning("access_token=tok123&token_type=bearer");
        GitHubDeviceLogin.DeviceCode code = fastDeviceCode();

        String token = GitHubDeviceLogin.pollForAccessToken(client, "client-id", code);

        assertEquals("tok123", token);
    }

    @Test
    void pollForAccessTokenRetriesOnAuthorizationPending() throws IOException, InterruptedException {
        CloseableHttpResponse pending = response("error=authorization_pending");
        CloseableHttpResponse granted = response("access_token=tok123");
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(client.execute(any(HttpUriRequest.class))).thenReturn(pending).thenReturn(granted);

        String token = GitHubDeviceLogin.pollForAccessToken(client, "client-id", fastDeviceCode());

        assertEquals("tok123", token);
    }

    @Test
    void pollForAccessTokenThrowsOnAccessDenied() throws IOException {
        CloseableHttpClient client = mockClientReturning("error=access_denied&error_description=User denied access");

        IOException e = assertThrows(IOException.class,
                () -> GitHubDeviceLogin.pollForAccessToken(client, "client-id", fastDeviceCode()));
        assertEquals("User denied access", e.getMessage());
    }

    /** interval=0, one-second window - pollForAccessToken doesn't actually sleep long in a test. */
    private static GitHubDeviceLogin.DeviceCode fastDeviceCode() {
        return new GitHubDeviceLogin.DeviceCode("device-code", "USER-CODE", "https://github.com/login/device", 5, 0);
    }

    private static CloseableHttpClient mockClientReturning(String formBody) throws IOException {
        CloseableHttpResponse resp = response(formBody);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(client.execute(any(HttpUriRequest.class))).thenReturn(resp);
        return client;
    }

    private static CloseableHttpResponse response(String formBody) throws IOException {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        HttpEntity entity = new StringEntity(formBody);
        when(response.getEntity()).thenReturn(entity);
        return response;
    }
}
