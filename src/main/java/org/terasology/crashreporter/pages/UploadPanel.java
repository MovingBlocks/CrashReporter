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
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.jpaste.exceptions.PasteException;
import org.jpaste.pastebin.PasteExpireDate;
import org.jpaste.pastebin.Pastebin;
import org.jpaste.pastebin.PastebinLink;
import org.jpaste.pastebin.PastebinPaste;
import org.terasology.crashreporter.I18N;
import org.terasology.crashreporter.Resources;
import org.terasology.crashreporter.Supplier;

public class UploadPanel extends JPanel {

    private static final long serialVersionUID = -8247883237201535146L;

    /**
     * Username Terasology
     * eMail pastebin@terasology.org
     */
    private static final String PASTEBIN_DEVELOPER_KEY = "1ed92217030bd6c2570fac91bcbfee78";

    private JButton uploadPasteBinButton;
    private String prevUpload;

    private JLabel statusLabel;

    private Supplier<String> textSupplier;

    public UploadPanel(Supplier<String> supplier) {

        this.textSupplier = supplier;
        setLayout(new BorderLayout(50, 20));
        statusLabel = new JLabel(I18N.getMessage("noUpload"), SwingConstants.RIGHT);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        statusLabel.setBorder(new EmptyBorder(0, 5, 0, 5));
        String title = "<html><h3>" + I18N.getMessage("uploadLog") + "</h></html>";
        JLabel titleLabel = new JLabel(title, Resources.loadIcon("icons/Arrow-up-icon.png"), SwingConstants.CENTER);
        titleLabel.setBorder(new EmptyBorder(10, 0, 0, 0));
        add(titleLabel, BorderLayout.NORTH);

        JPanel hosterPanel = new JPanel(new GridLayout(2, 1, 0, 20));
        hosterPanel.setBorder(new EmptyBorder(0, 50, 0, 50));
        uploadPasteBinButton = new JButton("PasteBin", Resources.loadIcon("icons/pastebin.png"));
        uploadPasteBinButton.setPreferredSize(new Dimension(250, 50));
        hosterPanel.add(uploadPasteBinButton);

        uploadPasteBinButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uploadPasteBinButton.setText(I18N.getMessage("waitForUpload"));
                uploadPasteBinButton.setEnabled(false);

                String text = textSupplier.get();
                if (!text.equals(prevUpload)) {
                    upload(text);
                }
            }
        });
        hosterPanel.add(uploadPasteBinButton);

        JButton uploadSkipButton = new JButton(I18N.getMessage("skipUpload"), Resources.loadIcon("icons/Actions-edit-delete-icon.png"));
        uploadSkipButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uploadSkipButton.setEnabled(false);

            }
        });
        hosterPanel.add(uploadSkipButton);

        add(hosterPanel, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    @Override
    public void setVisible(boolean aFlag) {

        super.setVisible(aFlag);

        if (!aFlag) {
            return;
        }

        String text = textSupplier.get();
        uploadPasteBinButton.setEnabled(text != null && !text.isEmpty() && !text.equals(prevUpload));
    }

    private void upload(String content) {
        String title = "Terasology Error Report";
        PastebinPaste paste = Pastebin.newPaste(PASTEBIN_DEVELOPER_KEY, content, title);
        paste.setPasteFormat("apache"); // Apache Log File Format - this is the closest I could find
        paste.setPasteExpireDate(PasteExpireDate.ONE_MONTH);

        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                try {
                    final PastebinLink link = paste.paste();

                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                            uploadSuccess(link, content);
                        }
                    });
                } catch (final PasteException e) {
                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                            uploadFailed(e);
                        }
                    });
                }
            }
        };

        Thread thread = new Thread(runnable, "Upload paste");
        thread.setName("Upload");
        thread.start();
    }

    private void uploadSuccess(PastebinLink link, String content) {
        final String url = link.getLink().toString();
        String uploadText = I18N.getMessage("uploadComplete");
        statusLabel.setText(String.format("<html>%s <a href=\"%s\">%s</a></html>", uploadText, url, url));
        statusLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        statusLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                openInBrowser(url);
            }
        });

        prevUpload = content;
    }

    private void uploadFailed(Exception e) {
        String uploadFailed = I18N.getMessage("uploadFailed");
        statusLabel.setText("<html>" + uploadFailed + ":<br/> " + e.getLocalizedMessage() + "</html>");
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
