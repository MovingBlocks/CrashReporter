// Copyright 2026 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.terasology.crashreporter.GlobalProperties;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for an upload failure that previously left no trace anywhere once its
 * {@code JOptionPane} was dismissed - found while manually testing the reporter dialog: a real
 * exception (a missing-Jackson {@code NoClassDefFoundError} - see the jackson-bom fix elsewhere in
 * this commit) only ever showed up in a popup, with nothing printed to stderr for whoever launched
 * the process to find afterward.
 */
class UploadPanelFailureLoggingTest {

    private PrintStream originalErr;
    private ByteArrayOutputStream capturedErr;

    @BeforeEach
    void redirectStderr() throws UnsupportedEncodingException {
        originalErr = System.err;
        capturedErr = new ByteArrayOutputStream();
        // String charset overloads: the Charset ones are Java 10+, and cr-core targets Java 8.
        System.setErr(new PrintStream(capturedErr, true, StandardCharsets.UTF_8.name()));
    }

    @AfterEach
    void restoreStderr() {
        System.setErr(originalErr);
    }

    @Test
    void aFailedUploadIsPrintedToStderrNotJustShownInAPopup() throws UnsupportedEncodingException {
        UploadPanel panel = new UploadPanel(new GlobalProperties(), () -> "log text", () -> "log.txt");

        // Synchronous stderr print, no thread/timing dance needed.
        panel.uploadFailed(new IllegalStateException("upload failed: missing Jackson class"));

        String stderr = capturedErr.toString(StandardCharsets.UTF_8.name());
        assertTrue(stderr.contains("IllegalStateException"), "Expected the exception type on stderr, got: " + stderr);
        assertTrue(stderr.contains("upload failed: missing Jackson class"),
                "Expected the exception message on stderr, got: " + stderr);
    }
}
