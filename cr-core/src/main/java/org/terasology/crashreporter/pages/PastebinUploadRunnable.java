// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Upload the content to PasteBin
 */
public class PastebinUploadRunnable implements Callable<URL> {

    static final String PASTEBIN_API_URL = "https://pastebin.com/api/api_post.php";
    static final int TIMEOUT_MILLIS = 30_000;

    /**
     * Username Terasology
     * eMail pastebin@terasology.org
     */
    private static final String PASTEBIN_DEVELOPER_KEY = "1ed92217030bd6c2570fac91bcbfee78";

    private final String content;
    private final URI apiUri;
    private final int timeoutMillis;

    public PastebinUploadRunnable(String content) {
        this(content, URI.create(PASTEBIN_API_URL), TIMEOUT_MILLIS);
    }

    PastebinUploadRunnable(String content, URI apiUri, int timeoutMillis) {
        this.content = content;
        this.apiUri = apiUri;
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public URL call() throws IOException {
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(timeoutMillis)
                .setConnectionRequestTimeout(timeoutMillis)
                .setSocketTimeout(timeoutMillis)
                .build();
        List<NameValuePair> form = Arrays.<NameValuePair>asList(
                new BasicNameValuePair("api_dev_key", PASTEBIN_DEVELOPER_KEY),
                new BasicNameValuePair("api_option", "paste"),
                new BasicNameValuePair("api_paste_code", content),
                new BasicNameValuePair("api_paste_name", "Terasology Error Report"),
                new BasicNameValuePair("api_paste_format", "apache"), // closest to a log file format
                new BasicNameValuePair("api_paste_expire_date", "1M"));

        try (CloseableHttpClient client = HttpClientBuilder.create().setDefaultRequestConfig(config).build()) {
            HttpPost post = new HttpPost(apiUri);
            post.setEntity(new UrlEncodedFormEntity(form, StandardCharsets.UTF_8));
            try (CloseableHttpResponse response = client.execute(post)) {
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8).trim();
                if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK || !body.startsWith("http")) {
                    throw new IOException(body); // PasteBin reports errors as "Bad API request, ..." text
                }
                return new URL(body);
            }
        }
    }
}
