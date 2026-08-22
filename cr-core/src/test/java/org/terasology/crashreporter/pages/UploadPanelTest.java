// Copyright 2026 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Regression tests for the PasteBin upload hang found while manually testing the reporter dialog:
 * {@code PastebinUploadRunnable} makes a real HTTP call with no timeout of its own, so a slow or
 * unreachable server left the upload button disabled and the status label reading "please wait"
 * forever, with no way to tell "still working" from "never finishing".
 */
class UploadPanelTest {

    private ExecutorService executor;

    @AfterEach
    void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void aSlowUploadFailsWithATimeoutInsteadOfHangingForever() throws Exception {
        executor = Executors.newSingleThreadExecutor();
        Future<URL> future = executor.submit(new Callable<URL>() {
            @Override
            public URL call() throws InterruptedException, MalformedURLException {
                // Longer than the 1-second timeout below - simulates the observed hang.
                Thread.sleep(5_000);
                return new URL("https://pastebin.com/never-reached");
            }
        });

        AtomicReference<URL> successResult = new AtomicReference<>();
        AtomicReference<Exception> failureResult = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        UploadPanel.awaitUpload(future, 1, link -> {
            successResult.set(link);
            done.countDown();
        }, e -> {
            failureResult.set(e);
            done.countDown();
        });

        assertTrue(done.await(1, TimeUnit.SECONDS), "awaitUpload must return once its own timeout elapses");
        assertNull(successResult.get(), "a timed-out upload must not report success");
        assertTrue(failureResult.get() instanceof IOException, "expected a timeout to surface as an IOException, got: " + failureResult.get());
        assertTrue(failureResult.get().getMessage().contains("timed out"),
                "expected a message naming the timeout, got: " + failureResult.get().getMessage());
        assertTrue(future.isCancelled(), "the underlying upload task should be cancelled once it's timed out");
    }

    @Test
    void aFastUploadReportsSuccess() throws Exception {
        executor = Executors.newSingleThreadExecutor();
        final URL expected = new URL("https://pastebin.com/abc123");
        Future<URL> future = executor.submit(new Callable<URL>() {
            @Override
            public URL call() {
                return expected;
            }
        });

        AtomicReference<URL> successResult = new AtomicReference<>();
        UploadPanel.awaitUpload(future, 5, successResult::set, e -> fail("expected success, got: " + e));

        assertEquals(expected, successResult.get());
    }

    @Test
    void anUnderlyingFailureIsUnwrappedFromExecutionException() throws Exception {
        executor = Executors.newSingleThreadExecutor();
        final IOException realCause = new IOException("invalid API key");
        Future<URL> future = executor.submit(new Callable<URL>() {
            @Override
            public URL call() throws IOException {
                throw realCause;
            }
        });

        AtomicReference<Exception> failureResult = new AtomicReference<>();
        UploadPanel.awaitUpload(future, 5, link -> fail("expected failure"), failureResult::set);

        assertEquals(realCause, failureResult.get(),
                "expected the real cause unwrapped from ExecutionException, not the wrapper itself");
    }
}
