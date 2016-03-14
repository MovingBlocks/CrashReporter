/*
 * Copyright 2016 MovingBlocks
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

import org.terasology.crashreporter.I18N;
import org.terasology.crashreporter.Resources;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.nio.file.Path;

/**
 * The panel that requests some additional info from the user
 */
public class UserInfoPanel extends JPanel {
    private static final long serialVersionUID = -1714121731354180405L;

    private JLabel requestLabel;
    private final JTextArea userMessageArea;
    private JLabel statusLabel;

    private String log;
    private final Path logFile;

    private Boolean additionalInfoPresent;

    /**
     * @param log     the current contents of the log file
     * @param logFile the location of the relevant file
     */
    public UserInfoPanel(String log, Path logFile) {
        this.log = log;
        this.logFile = logFile;
        additionalInfoPresent = false;

        setLayout(new BorderLayout(50, 20));

        String htmlText = "<html><h3>" + I18N.getMessage("additionalInfoTitle") + "</h3>" + I18N.getMessage("additionalInfoMessage") + "</html>";

        requestLabel = new JLabel(htmlText, Resources.loadIcon("icons/Actions-dialog-info-icon.png"), SwingConstants.CENTER);
        requestLabel.setBorder(new EmptyBorder(10, 0, 0, 0));

        userMessageArea = new JTextArea();
        userMessageArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        userMessageArea.setFont(getFont().deriveFont(Font.PLAIN).deriveFont(12f));

        userMessageArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                additionalInfoPresent = true;
                changeStatus();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                if (userMessageArea.getText().isEmpty()) {
                    additionalInfoPresent = false;
                    changeStatus();
                }
            }

            // TODO not sure if this is ever used?
            @Override
            public void changedUpdate(DocumentEvent e) {
                additionalInfoPresent = true;
                changeStatus();
            }
        });

        JScrollPane areaScrollPane = new JScrollPane(userMessageArea);

        statusLabel = new JLabel(I18N.getMessage("additionalInfoStatus"), SwingConstants.RIGHT);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        statusLabel.setBorder(new EmptyBorder(0, 5, 0, 5));

        add(requestLabel, BorderLayout.NORTH);
        add(areaScrollPane, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    @Override
    public void setVisible(boolean aFlag) {
        super.setVisible(aFlag);

        if (!aFlag) {
            return;
        }

        firePropertyChange("pageComplete", (Boolean) !additionalInfoPresent, additionalInfoPresent);
    }

    private void changeStatus() {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                firePropertyChange("pageComplete", (Boolean) !additionalInfoPresent, additionalInfoPresent);
                if (additionalInfoPresent) {
                    // Set so that the warning label does not recreate the UI when gone
                    statusLabel.setText(" ");
                } else {
                    statusLabel.setText(I18N.getMessage("additionalInfoStatus"));
                }
            }
        });
    }

    public String getLog() {
        if (!additionalInfoPresent) {
            return log;
        } else {
            // probably don't want to apply i18n to this
            return new StringBuilder("USER-GIVEN INFO:")
                    .append("\n")
                    .append(userMessageArea.getText())
                    .append("\n\n")
                    .append("ERROR STACK TRACE:")
                    .append("\n")
                    .append(log)
                    .toString();
        }
    }

    public Path getLogFile() {
        return logFile;
    }
}
