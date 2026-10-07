// Copyright 2026 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loopback-only: no test touches the real network. */
class PastebinUploadRunnableTest {

    private HttpServer server;
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    }

    @AfterEach
    void stopServer() {
        release.countDown();
        server.stop(0);
    }

    private URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/api_post.php");
    }

    @Test
    void postsFormAndReturnsPasteUrl() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/api/api_post.php", exchange -> {
            requestBody.set(new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8));
            reply(exchange, 200, "https://pastebin.com/abc123");
        });
        server.start();

        URL url = new PastebinUploadRunnable("a log & more", uri(), 5000).call();

        assertEquals("https://pastebin.com/abc123", url.toString());
        assertTrue(requestBody.get().contains("api_option=paste"));
        assertTrue(requestBody.get().contains("api_paste_code=a+log+%26+more"));
    }

    @Test
    void apiErrorTextBecomesIoException() {
        server.createContext("/api/api_post.php", exchange -> reply(exchange, 200, "Bad API request, invalid api_dev_key"));
        server.start();

        IOException e = assertThrows(IOException.class, () -> new PastebinUploadRunnable("x", uri(), 5000).call());
        assertEquals("Bad API request, invalid api_dev_key", e.getMessage());
    }

    @Test
    void hungServerTimesOutInsteadOfBlocking() {
        server.createContext("/api/api_post.php", exchange -> {
            try {
                release.await(); // never answers until the test ends
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();

        long start = System.nanoTime();
        assertThrows(SocketTimeoutException.class, () -> new PastebinUploadRunnable("x", uri(), 300).call());
        assertTrue((System.nanoTime() - start) / 1_000_000 < 5000, "timeout must actually stop the request");
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int code, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static byte[] readAll(java.io.InputStream in) throws IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n = in.read(chunk);
        while (n > 0) {
            buf.write(chunk, 0, n);
            n = in.read(chunk);
        }
        return buf.toByteArray();
    }
}
