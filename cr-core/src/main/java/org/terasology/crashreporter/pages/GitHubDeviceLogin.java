// Copyright 2026 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GitHub's OAuth Device Flow - no client secret, no redirect URI, made for apps like this one.
 * Needs an OAuth App registered at github.com/settings/developers with Device Flow enabled;
 * its client ID goes in {@link org.terasology.crashreporter.GlobalProperties.KEY#REPORT_ISSUE_OAUTH_CLIENT_ID}.
 * Endpoints reply form-urlencoded by default, so no JSON parsing needed here.
 */
public final class GitHubDeviceLogin {

    private static final String DEVICE_CODE_URL = "https://github.com/login/device/code";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String SCOPE = "public_repo";

    private GitHubDeviceLogin() {
    }

    public static DeviceCode requestDeviceCode(CloseableHttpClient client, String clientId) throws IOException {
        Map<String, String> fields = post(client, DEVICE_CODE_URL,
                param("client_id", clientId), param("scope", SCOPE));
        failOnError(fields);
        return new DeviceCode(fields.get("device_code"), fields.get("user_code"), fields.get("verification_uri"),
                Integer.parseInt(fields.get("expires_in")), Integer.parseInt(fields.get("interval")));
    }

    /** Blocks until authorized, denied, or expired. Call off the UI thread. */
    public static String pollForAccessToken(CloseableHttpClient client, String clientId, DeviceCode code)
            throws IOException, InterruptedException {
        int interval = code.intervalSeconds;
        long deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(interval * 1000L);
            Map<String, String> fields = post(client, TOKEN_URL,
                    param("client_id", clientId), param("device_code", code.deviceCode),
                    param("grant_type", "urn:ietf:params:oauth:grant-type:device_code"));
            String token = fields.get("access_token");
            if (token != null) {
                return token;
            }
            String error = fields.get("error");
            if ("authorization_pending".equals(error)) {
                continue;
            }
            if ("slow_down".equals(error)) {
                interval += 5;
                continue;
            }
            failOnError(fields);
        }
        throw new IOException("Device code expired");
    }

    private static void failOnError(Map<String, String> fields) throws IOException {
        String error = fields.get("error");
        if (error != null) {
            throw new IOException(fields.getOrDefault("error_description", error));
        }
    }

    private static Map<String, String> post(CloseableHttpClient client, String url, NameValuePair... params)
            throws IOException {
        HttpPost post = new HttpPost(url);
        post.setHeader("Accept", "application/x-www-form-urlencoded");
        List<NameValuePair> paramList = new ArrayList<>();
        for (NameValuePair param : params) {
            paramList.add(param);
        }
        post.setEntity(new UrlEncodedFormEntity(paramList, StandardCharsets.UTF_8));
        try (CloseableHttpResponse response = client.execute(post)) {
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            return parseFormBody(body);
        }
    }

    private static NameValuePair param(String name, String value) {
        return new BasicNameValuePair(name, value);
    }

    static Map<String, String> parseFormBody(String body) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            result.put(decode(key), decode(value));
        }
        return result;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    public static final class DeviceCode {
        final String deviceCode;
        final String userCode;
        final String verificationUri;
        final int expiresInSeconds;
        final int intervalSeconds;

        DeviceCode(String deviceCode, String userCode, String verificationUri, int expiresInSeconds, int intervalSeconds) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUri = verificationUri;
            this.expiresInSeconds = expiresInSeconds;
            this.intervalSeconds = intervalSeconds;
        }

        public String getUserCode() {
            return userCode;
        }

        public String getVerificationUri() {
            return verificationUri;
        }
    }
}
