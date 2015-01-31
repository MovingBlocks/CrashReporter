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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

import org.terasology.crashreporter.I18N;
import org.terasology.crashreporter.Resources;

/**
 * Displays the content of the log file.
 * @author Martin Steiger
 */
public class LogViewPanel extends JPanel {

    private static final long serialVersionUID = -4556843268269818376L;

    private final JTextArea logArea;

    public LogViewPanel(Path logFile) {

        final String logFileContent = readLogFileContent(logFile);

        JPanel mainPanel = this;
        mainPanel.setLayout(new BorderLayout(0, 5));

        String caption = I18N.getMessage("viewLog");
        String readablePath = logFile.toAbsolutePath().normalize().toString();
        String loc = I18N.getMessage("fileLocation") + ": " + readablePath;

        Icon titleIcon = Resources.loadIcon("icons/Actions-document-properties-icon.png");
        String htmlText = "<html><h3>" + caption + "</h3><p>" + loc + "</p></html>";
        JLabel title = new JLabel(htmlText, titleIcon, SwingConstants.LEFT);
        mainPanel.add(title, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setText(logFileContent);
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        String message = I18N.getMessage("editBeforeUpload");

        // mark the text before ":" in bold - English NOTE:
        int idx = message.indexOf(':');
        if (idx > 0) {
            message = "<b>" + message.substring(0, idx) + "</b>" + message.substring(idx);
        }
        JLabel editHintLabel = new JLabel("<html>" + message + "</html>");
        add(editHintLabel, BorderLayout.SOUTH);
    }

    /**
     * @return the (edited) log file contents
     */
    public String getLog() {
        return logArea.getText();
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
