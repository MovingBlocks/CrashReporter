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
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import org.terasology.crashreporter.I18N;
import org.terasology.crashreporter.Resources;
import org.terasology.crashreporter.Supplier;

/**
 * Lists a few actions before closing the dialog
 * @author Martin Steiger
 */
public class FinalActionsPanel extends JPanel {

    private static final long serialVersionUID = 2639334979749507943L;

    private static final String SUPPORT_FORUM_LINK = "http://forum.terasology.org/forum/support.20/";
    private static final String REPORT_ISSUE_LINK = "https://github.com/MovingBlocks/Terasology/issues/new";
    private static final String JOIN_IRC_LINK = "https://webchat.freenode.net/?channels=terasology";

    private final Supplier<URL> uploadedFile;

    private final JTextArea linkText;

    private final JButton copyLinkButton;

    private boolean pageComplete;

    public FinalActionsPanel(Supplier<URL> uploadedFile) {

        this.uploadedFile = uploadedFile;

        setLayout(new BorderLayout(0, 10));
        setBorder(new EmptyBorder(0, 10, 10, 10));

        String firstLine = I18N.getMessage("reportProblem");
        String htmlText = "<html><h3>" + firstLine + "</h3></html>";
        Icon titleIcon = Resources.loadIcon("icons/Actions-irc-voice-icon.png");
        JLabel message = new JLabel(htmlText, titleIcon, SwingConstants.LEFT);

        add(message, BorderLayout.NORTH);

        JPanel gridPanel = new JPanel();
        gridPanel.setLayout(new GridLayout(0, 1, 0, 10));
//        gridPanel.setBorder(new EmptyBorder(20, 10, 20, 10));

        Font buttonFont = getFont().deriveFont(Font.BOLD).deriveFont(14f);

        JButton forumButton = new JButton(I18N.getMessage("gotoForum"));
        forumButton.setIcon(Resources.loadIcon("icons/forum.png"));
        forumButton.setFont(buttonFont);
        forumButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                openInBrowser(SUPPORT_FORUM_LINK);
                pageComplete = true;
                firePropertyChange("pageComplete", !pageComplete, pageComplete);
            }
        });
        forumButton.setToolTipText(SUPPORT_FORUM_LINK);
        gridPanel.add(forumButton);

        JButton enterIrc = new JButton(I18N.getMessage("joinIrc"));
        enterIrc.setFont(buttonFont);
        enterIrc.setIcon(Resources.loadIcon("icons/irc.png"));
        enterIrc.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                openInBrowser(JOIN_IRC_LINK);
                pageComplete = true;
                firePropertyChange("pageComplete", !pageComplete, pageComplete);
            }
        });
        enterIrc.setToolTipText(JOIN_IRC_LINK);
        gridPanel.add(enterIrc);

        JButton githubIssueButton = new JButton(I18N.getMessage("reportIssue"));
        githubIssueButton.setFont(buttonFont);
        githubIssueButton.setIcon(Resources.loadIcon("icons/github.png"));
        githubIssueButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                openInBrowser(REPORT_ISSUE_LINK);
                pageComplete = true;
                firePropertyChange("pageComplete", !pageComplete, pageComplete);
            }
        });
        githubIssueButton.setToolTipText(REPORT_ISSUE_LINK);
        gridPanel.add(githubIssueButton);

        add(gridPanel, BorderLayout.CENTER);

        // -------- link text -------

        JPanel linkPanel = new JPanel();
        linkPanel.setLayout(new BorderLayout(5, 0));

        JLabel linkLabel = new JLabel(I18N.getMessage("logFileUrl"));
        linkPanel.add(linkLabel, BorderLayout.WEST);

        linkText = new JTextArea();
        linkText.setPreferredSize(new Dimension(250, 25));
        linkText.setBorder(BorderFactory.createEtchedBorder());
        linkText.setEditable(false);
        linkPanel.add(linkText, BorderLayout.CENTER);

        copyLinkButton = new JButton(Resources.loadIcon("icons/Actions-edit-paste-icon.png"));
        copyLinkButton.setToolTipText(I18N.getMessage("copyToClipboard"));
        copyLinkButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                StringSelection stringSelection = new StringSelection(linkText.getText());
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(stringSelection, null);
            }
        });
        linkPanel.add(copyLinkButton, BorderLayout.EAST);

        add(linkPanel, BorderLayout.SOUTH);

    }

    @Override
    public void setVisible(boolean aFlag) {
        super.setVisible(aFlag);

        if (!aFlag) {
            return;
        }

        firePropertyChange("pageComplete", !pageComplete, pageComplete);

        URL log = uploadedFile.get();
        String text = log != null ? log.toString() : I18N.getMessage("noUploadLinkText");
        copyLinkButton.setEnabled(log != null);
        linkText.setText(text);
        linkText.setEnabled(log != null);
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
}
