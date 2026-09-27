// Copyright 2026 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.terasology.crashreporter.CrashReporter;
import org.terasology.crashreporter.GlobalProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for #53: PasteBin upload only included whichever log tab happened to be
 * selected, silently dropping every other log file present.
 *
 * <p>Every panel is closed before the test returns: it keeps a reader open per log file plus a
 * watch on the folder, and on Windows that stops JUnit from deleting the {@code @TempDir}.
 */
class ErrorMessagePanelTest {

    @Test
    void getLogIncludesEveryLogFileNotJustTheSelectedTab(@TempDir Path logFolder) throws IOException {
        writeLog(logFolder, "Terasology-init.log", "INIT LOG CONTENT");
        writeLog(logFolder, "Terasology-menu.log", "MENU LOG CONTENT");

        try (ErrorMessagePanel panel = new ErrorMessagePanel(new GlobalProperties(), new RuntimeException("boom"),
                logFolder, CrashReporter.MODE.CRASH_REPORTER)) {
            String log = panel.getLog();
            assertTrue(log.contains("INIT LOG CONTENT"), "Expected the init log's content in the combined log, got: " + log);
            assertTrue(log.contains("MENU LOG CONTENT"), "Expected the menu log's content in the combined log, got: " + log);
            assertTrue(log.contains("Terasology-init.log"), "Expected a header naming the init log tab, got: " + log);
            assertTrue(log.contains("Terasology-menu.log"), "Expected a header naming the menu log tab, got: " + log);
        }
    }

    @Test
    void getLogStillWorksWithASingleLogFile(@TempDir Path logFolder) throws IOException {
        writeLog(logFolder, "Terasology.log", "SOLO LOG CONTENT");

        try (ErrorMessagePanel panel = new ErrorMessagePanel(new GlobalProperties(), new RuntimeException("boom"),
                logFolder, CrashReporter.MODE.CRASH_REPORTER)) {
            assertTrue(panel.getLog().contains("SOLO LOG CONTENT"));
        }
    }

    @Test
    void closeReleasesTheLogFilesSoTheFolderCanBeDeleted(@TempDir Path logFolder) throws IOException {
        Path log = writeLog(logFolder, "Terasology.log", "LOG CONTENT");

        ErrorMessagePanel panel = new ErrorMessagePanel(new GlobalProperties(), new RuntimeException("boom"),
                logFolder, CrashReporter.MODE.CRASH_REPORTER);
        panel.close();

        // Only Windows actually refuses this while a reader is open, so on other platforms this
        // documents the contract rather than proving it.
        Files.delete(log);
        assertTrue(Files.notExists(log), "Expected the log file to be deletable once the panel is closed");
    }

    private static Path writeLog(Path folder, String name, String content) throws IOException {
        return Files.write(folder.resolve(name), content.getBytes(StandardCharsets.UTF_8));
    }
}
