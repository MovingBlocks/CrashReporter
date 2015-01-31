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
import java.awt.Dimension;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.terasology.crashreporter.I18N;

/**
 * Shows the error message plus stack trace
 * @author Martin Steiger
 */
public class ErrorMessagePanel extends JPanel {
    private static final long serialVersionUID = 8449689452512733452L;

    /**
     * @param exception the exception to display
     */
    public ErrorMessagePanel(Throwable exception) {

        JPanel mainPanel = this;
        mainPanel.setLayout(new BorderLayout(0, 20));

        // Replace newline chars. with html newline elements (not needed in most cases)
        String text = "<b>" + exception.getClass().getSimpleName() + "</b>";
        String exMessage = exception.getLocalizedMessage();
        if (exMessage != null) {
            text += ": " + exMessage;
        }

        text = text.replaceAll("\\r?\\n", "<br/>");
        String firstLine = I18N.getMessage("firstLine");
        JLabel message = new JLabel("<html><h3>" + firstLine + "</h3><br/>" + text + "</html>");
        mainPanel.setPreferredSize(new Dimension(750, 450));

        mainPanel.add(message, BorderLayout.NORTH);

        // convert exception stacktrace to string
        StringWriter sw = new StringWriter();
        exception.printStackTrace(new PrintWriter(sw));
        String stacktrace = sw.toString();
        // do not use exception.getStackTrace(), because it does
        // not contain suppressed exception or causes

        // StackTrace tab
        JTextArea stackTraceArea = new JTextArea();
        stackTraceArea.setText(stacktrace);
        stackTraceArea.setEditable(false);
        stackTraceArea.setCaretPosition(0);
        add(new JScrollPane(stackTraceArea), BorderLayout.CENTER);
    }

}
