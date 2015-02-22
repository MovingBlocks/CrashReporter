/*
 * Copyright 2015 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.crashreporter.pages;

import java.awt.BorderLayout;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.util.Collection;
import java.util.List;
import java.nio.file.attribute.BasicFileAttributes;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

import org.terasology.crashreporter.I18N;
import org.terasology.crashreporter.Resources;

import com.google.common.collect.Lists;

/**
 * Shows the error message plus stack trace
 * @author Martin Steiger
 */
public class ErrorMessagePanel extends JPanel {

    private static final long serialVersionUID = 8449689452512733452L;

    private final JTabbedPane tabPane;
    private final List<JTextArea> textAreas = Lists.newArrayList();
    private final List<Path> logFiles = Lists.newArrayList();

    /**
     * @param exception the exception to display
     */
    public ErrorMessagePanel(Throwable exception, Path logFileFolder) {

        JPanel mainPanel = this;
        mainPanel.setLayout(new BorderLayout(0, 5));

        String text = "<b>" + exception.getClass().getSimpleName() + "</b>";
        String exMessage = exception.getLocalizedMessage();
        if (exMessage != null) {
            text += ": " + exMessage;
        }
        // Replace newline chars. with html newline elements (not needed in most cases)
        text = text.replaceAll("\\r?\\n", "<br/>");

        String firstLine = I18N.getMessage("firstLine");
        Icon titleIcon = Resources.loadIcon("icons/Actions-dialog-close-icon.png");

        String htmlText = "<html><h3>" + firstLine + "</h3>" + text + "</html>";
        JLabel message = new JLabel(htmlText, titleIcon, SwingConstants.LEFT);

        mainPanel.add(message, BorderLayout.NORTH);

        tabPane = new JTabbedPane();
        for (Path logFile : findLogs(logFileFolder)) {
            final String logFileContent = readLogFileContent(logFile);
            JTextArea logArea = new JTextArea();
            logArea.setText(logFileContent);
            String tabName = logFileFolder.relativize(logFile).toString();
            tabPane.addTab(tabName, new JScrollPane(logArea));
            textAreas.add(logArea);
            logFiles.add(logFile);
        }
        add(tabPane, BorderLayout.CENTER);

        String readablePath = logFileFolder.toAbsolutePath().normalize().toString();
        String loc = I18N.getMessage("fileLocation") + ": " + readablePath;

        String editMessage = I18N.getMessage("editBeforeUpload");

        // mark the text before ":" in bold - English NOTE:
        int idx = editMessage.indexOf(':');
        if (idx > 0) {
            editMessage = "<b>" + editMessage.substring(0, idx) + "</b>" + editMessage.substring(idx);
        }
        JLabel editHintLabel = new JLabel("<html>" + loc + "<br/><br/>" + editMessage + "</html>");
        add(editHintLabel, BorderLayout.SOUTH);
    }

    /**
     * @param logFileFolder
     * @return
     */
    private Collection<Path> findLogs(Path logFileFolder) {
        // JAVA8: this should work, too
        // Files.walk(logFileFolder).filter(path -> path.toString().endsWith(".log")).collect(Collectors.toList());

        final List<Path> results = Lists.newArrayList();
        try {
            Files.walkFileTree(logFileFolder, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

                    if (file.toString().endsWith(".log")) {
                        results.add(file);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        return results;
    }

    /**
     * @return the (edited) log file contents
     */
    public String getLog() {
        int idx = tabPane.getSelectedIndex();
        return idx >= 0 ? textAreas.get(idx).getText() : "";
    }

    /**
     * @return the original log file
     */
    public Path getLogFile() {
        int idx = tabPane.getSelectedIndex();
        return idx >= 0 ? logFiles.get(idx) : null;
    }

    private static String readLogFileContent(Path logFile) {
        StringBuilder builder = new StringBuilder();

        if (logFile != null) {
            try {
                List<String> lines = Files.readAllLines(logFile, Charset.defaultCharset());
                for (String line : lines) {
                    builder.append(line);
                    builder.append(System.lineSeparator());
                }
            } catch (Exception e) { // we catch all here, because we want to continue execution in all cases
                e.printStackTrace(System.err);

                StringWriter sw = new StringWriter();
                builder.append("Could not open log file " + logFile.toString() + System.lineSeparator());
                e.printStackTrace(new PrintWriter(sw));
                builder.append(sw.toString());
            }
        }

        return builder.toString();
    }
}
