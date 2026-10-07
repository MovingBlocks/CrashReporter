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
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Upload the content to PasteBin
 */
public class PastebinUploadRunnable implements Callable<URL> {

    static final String PASTEBIN_API_URL = "https://pastebin.com/api/api_post.php";
    static final int TIMEOUT_MILLIS = 30_000;
    private static final long WATCHDOG_POLL_MILLIS = 50;

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

        HttpPost post = new HttpPost(apiUri);
        post.setEntity(new UrlEncodedFormEntity(form, StandardCharsets.UTF_8));

        // Socket timeouts only bound inactivity, so a trickling server could hold the request open;
        // the watchdog aborts it at an overall deadline, or as soon as this thread is interrupted
        // (UploadPanel cancels its Future on timeout) - blocking socket I/O ignores interrupts itself.
        final Thread owner = Thread.currentThread();
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        final AtomicBoolean timedOut = new AtomicBoolean();
        final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "PasteBin upload watchdog");
                thread.setDaemon(true);
                return thread;
            }
        });
        watchdog.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                boolean expired = System.nanoTime() - deadline >= 0;
                if (expired || owner.isInterrupted()) {
                    timedOut.set(expired);
                    post.abort();
                }
            }
        }, WATCHDOG_POLL_MILLIS, WATCHDOG_POLL_MILLIS, TimeUnit.MILLISECONDS);

        try (CloseableHttpClient client = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
                CloseableHttpResponse response = client.execute(post)) {
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8).trim();
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK || !body.startsWith("http")) {
                throw new IOException(body); // PasteBin reports errors as "Bad API request, ..." text
            }
            return new URL(body);
        } catch (IOException e) {
            if (timedOut.get() && !(e instanceof SocketTimeoutException)) {
                SocketTimeoutException timeout = new SocketTimeoutException("PasteBin upload exceeded " + timeoutMillis + " ms");
                timeout.initCause(e);
                throw timeout;
            }
            throw e;
        } finally {
            watchdog.shutdownNow();
        }
    }
}
