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
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for #53 item 1: log tabs sorted by file creation time (newest first) instead of
 * alphabetically, which read as arbitrary/broken with more than one log file present.
 */
class ErrorMessagePanelTabOrderTest {

    @Test
    void tabsAreOrderedAlphabeticallyRegardlessOfCreationOrder(@TempDir Path logFolder) throws IOException, InterruptedException {
        // Written out of alphabetical order, with a real gap between creation times so a
        // creation-time-based sort (the old behavior) would disagree with alphabetical order.
        writeLog(logFolder, "Terasology-menu.log", "MENU");
        Thread.sleep(10);
        writeLog(logFolder, "Terasology-init.log", "INIT");

        ErrorMessagePanel panel = new ErrorMessagePanel(new GlobalProperties(), new RuntimeException("boom"),
                logFolder, CrashReporter.MODE.CRASH_REPORTER);

        List<String> titles = panel.getTabTitles();
        assertEquals(Arrays.asList("Terasology-init.log", "Terasology-menu.log"), titles,
                "Expected tabs in alphabetical order regardless of which file was created first, got: " + titles);
    }

    private static void writeLog(Path folder, String name, String content) throws IOException {
        Files.write(folder.resolve(name), content.getBytes(StandardCharsets.UTF_8));
    }
}
