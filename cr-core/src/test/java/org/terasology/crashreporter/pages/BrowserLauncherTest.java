// Copyright 2026 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the "Join Discord"/"File an issue on GitHub" buttons silently doing nothing -
 * found while manually testing the reporter dialog. Both buttons call this class with a
 * {@code GlobalProperties} key that cr-core's own defaults never set (only downstream apps like
 * cr-terasology configure real Discord/issue-tracker links), so {@code url} was {@code null}; the old
 * code passed that straight into {@code new URI(null)}, which throws an uncaught
 * {@code NullPointerException} out of the button's {@code ActionListener} on the EDT - nothing catches
 * that, so the click just did nothing, with no trace anywhere it happened.
 */
class BrowserLauncherTest {

    @Test
    void aMissingUrlIsReportedNotThrown() {
        List<Exception> failures = new ArrayList<>();
        BrowserLauncher.open(null, uri -> unexpected(uri), failures::add);

        assertEquals(1, failures.size());
    }

    @Test
    void aBlankUrlIsReportedNotThrown() {
        List<Exception> failures = new ArrayList<>();
        BrowserLauncher.open("", uri -> unexpected(uri), failures::add);

        assertEquals(1, failures.size());
    }

    @Test
    void anInvalidUrlIsReportedNotThrown() {
        List<Exception> failures = new ArrayList<>();
        // an unescaped space makes this an invalid URI, not just an unreachable one
        BrowserLauncher.open("not a valid uri", uri -> unexpected(uri), failures::add);

        assertEquals(1, failures.size());
    }

    @Test
    void aBrowserFailureIsReportedNotThrown() {
        List<Exception> failures = new ArrayList<>();
        RuntimeException browserFailure = new IllegalStateException("no browser configured");
        BrowserLauncher.open("https://github.com/MovingBlocks/CrashReporter", uri -> {
            throw browserFailure;
        }, failures::add);

        assertEquals(1, failures.size());
        assertTrue(failures.get(0) == browserFailure);
    }

    @Test
    void aValidUrlIsHandedToTheBrowser() {
        List<URI> opened = new ArrayList<>();
        BrowserLauncher.open("https://github.com/MovingBlocks/CrashReporter", opened::add, failure -> unexpected(failure));

        assertEquals(1, opened.size());
        assertEquals("https://github.com/MovingBlocks/CrashReporter", opened.get(0).toString());
    }

    private static void unexpected(Object value) {
        throw new AssertionError("Did not expect this to be reached: " + value);
    }
}
