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
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.jpaste.exceptions.PasteException;
import org.jpaste.pastebin.PasteExpireDate;
import org.jpaste.pastebin.Pastebin;
import org.jpaste.pastebin.PastebinLink;
import org.jpaste.pastebin.PastebinPaste;
import org.terasology.crashreporter.I18N;
import org.terasology.crashreporter.Resources;

public class FinalActionsPanel extends JPanel {
    private static final String SUPPORT_FORUM_LINK = "http://forum.terasology.org/forum/support.20/";
    private static final String REPORT_ISSUE_LINK = "https://github.com/MovingBlocks/Terasology/issues/new";
    private static final String JOIN_IRC_LINK = "https://webchat.freenode.net/?channels=terasology";

    public FinalActionsPanel() {

        setLayout(new GridLayout(0, 1, 20, 20));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        Font buttonFont = getFont().deriveFont(Font.BOLD).deriveFont(14f);

        JButton forumButton = new JButton(I18N.getMessage("gotoForum"));
        forumButton.setIcon(Resources.loadIcon("icons/forum.png"));
        forumButton.setFont(buttonFont);
        forumButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                openInBrowser(SUPPORT_FORUM_LINK);
            }
        });
        add(forumButton);

        JButton enterIrc = new JButton(I18N.getMessage("joinIrc"));
        enterIrc.setFont(buttonFont);
        enterIrc.setIcon(Resources.loadIcon("icons/irc.png"));
        enterIrc.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                openInBrowser(JOIN_IRC_LINK);
            }
        });
        add(enterIrc);

        JButton githubIssueButton = new JButton(I18N.getMessage("reportIssue"));
        githubIssueButton.setFont(buttonFont);
        githubIssueButton.setIcon(Resources.loadIcon("icons/github.png"));
        githubIssueButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                openInBrowser(REPORT_ISSUE_LINK);
            }
        });
        add(githubIssueButton);
    }

    protected static void uploadPaste(final PastebinPaste paste) {
        final JLabel label = new JLabel(I18N.getMessage("waitForUpload"));
        label.setPreferredSize(new Dimension(250, 50));

        final JButton closeButton = new JButton(I18N.getMessage("close"), Resources.loadIcon("icons/close.png"));
        closeButton.setEnabled(false);

        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                try {
                    final PastebinLink link = paste.paste();

                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                            closeButton.setEnabled(true);
                            final String url = link.getLink().toString();
                            String uploadText = I18N.getMessage("uploadComplete");
                            label.setText(String.format("<html>%s <a href=\"%s\">%s</a></html>", uploadText, url, url));
                            label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                            label.addMouseListener(new MouseAdapter() {
                                @Override
                                public void mouseClicked(java.awt.event.MouseEvent e) {
                                    openInBrowser(url);
                                }
                            });
                        }
                    });
                } catch (final PasteException e) {
                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                            closeButton.setEnabled(true);
                            String uploadFailed = I18N.getMessage("uploadFailed");
                            label.setText("<html>" + uploadFailed + ":<br/> " + e.getLocalizedMessage() + "</html>");
                        }
                    });
                }
            }
        };

        Thread thread = new Thread(runnable, "Upload paste");
        thread.start();

        JDialog dialog = createDialog(label, closeButton, I18N.getMessage("uploadDialog"), JOptionPane.INFORMATION_MESSAGE);
        dialog.setVisible(true);
        dialog.dispose();
    }

    private static void openInBrowser(String url) {
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();

            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                try {
                    desktop.browse(new URI(url));
                } catch (IOException | URISyntaxException e) {
                    e.printStackTrace(System.err);
                }
            }
        }
    }

    // This is c&p from CrashReporter
    private static JDialog createDialog(Component mainPanel, JButton closeButton, String title, int messageType) {
        Object[] opts = new Object[]{closeButton};

        // The error-message pane
        final JOptionPane pane = new JOptionPane(mainPanel, messageType, JOptionPane.DEFAULT_OPTION, null, opts, opts[0]);
        closeButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                // calling setValue() closes the dialog
                pane.setValue("CLOSE"); // the actual value doesn't matter
            }
        });

        // wrap it all in a dialog
        JDialog dialog = pane.createDialog(title);
        return dialog;
    }

}
