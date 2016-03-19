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

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Font;
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

    /**
     * @param log     the current contents of the log file
     * @param logFile the location of the relevant file
     */
    public UserInfoPanel(String log, Path logFile) {
        this.log = log;
        this.logFile = logFile;

        setLayout(new BorderLayout(0, 5));

        String title = I18N.getMessage("additionalInfoTitle");
        String message = I18N.getMessage("additionalInfoMessage");
        String htmlText = "<html><h3>" + title + "</h3>" + message + "</html>";

        Icon icon = Resources.loadIcon("icons/Actions-dialog-info-icon.png");
        requestLabel = new JLabel(htmlText, icon, SwingConstants.LEFT);

        userMessageArea = new JTextArea();
        userMessageArea.setFont(getFont().deriveFont(Font.PLAIN).deriveFont(12f));

        userMessageArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshStatus();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshStatus();
            }

            // TODO not sure if this is ever used?
            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshStatus();
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

    private boolean isEmpty() {
        return userMessageArea.getText().isEmpty();
    }

    @Override
    public void setVisible(boolean aFlag) {
        super.setVisible(aFlag);

        if (!aFlag) {
            return;
        }

        boolean empty = isEmpty();
        firePropertyChange("pageComplete", empty, !empty);
    }

    private void refreshStatus() {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                boolean empty = isEmpty();

                firePropertyChange("pageComplete", empty, !empty);
                if (!empty) {
                    // Set so that the warning label does not recreate the UI when gone
                    statusLabel.setText(" ");
                } else {
                    statusLabel.setText(I18N.getMessage("additionalInfoStatus"));
                }
            }
        });
    }

    public String getLog() {
        if (isEmpty()) {
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
