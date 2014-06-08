/*
 * Copyright 2014 MovingBlocks
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

package org.terasology.crashreporter;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.jpaste.exceptions.PasteException;
import org.jpaste.pastebin.PasteExpireDate;
import org.jpaste.pastebin.Pastebin;
import org.jpaste.pastebin.PastebinLink;
import org.jpaste.pastebin.PastebinPaste;

/**
 * Displays a detailed error message and provides some options to communicate with devs.
 * Errors are reported to {@link System#err}
 *
 * @author Martin Steiger
 */
public final class CrashReporter {

    /**
     * Username Terasology
     * eMail pastebin@terasology.org
     */
    private static final String PASTEBIN_DEVELOPER_KEY = "1ed92217030bd6c2570fac91bcbfee78";

    private static final String REPORT_ISSUE_LINK = "https://github.com/MovingBlocks/Terasology/issues/new";
    private static final String JOIN_IRC_LINK = "https://webchat.freenode.net/?channels=terasology";

    private CrashReporter() {
        // don't create any instances
    }

    /**
     * @param t the exception
     * @param logFile the log file path or <code>null</code>
     */
    public static void report(final Throwable t, Path logFile) {

        // Swing element methods must be called in the swing thread
        try {
            final String logFileContent = getLogFileContent(logFile);

            SwingUtilities.invokeAndWait(new Runnable() {

                @Override
                public void run() {
                    LookAndFeel oldLaF = UIManager.getLookAndFeel();
                    try {
                        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    } catch (Exception e) {
                        e.printStackTrace(System.err);
                    }
                    showModalDialog(t, logFileContent);
                    try {
                        UIManager.setLookAndFeel(oldLaF);
                    } catch (Exception e) {
                        e.printStackTrace(System.err);
                    }
                }
            });
        } catch (InvocationTargetException | InterruptedException e) {
            e.printStackTrace(System.err);
        }
    }

    private static String getVersionInfo()
    {
        String fname = "versionInfo.properties";
        URL location = CrashReporter.class.getResource(fname);

        if (location == null)
            return "";

        try (InputStream is = location.openStream())
        {
            Properties props = new Properties();
            props.load(is);
            return props.getProperty("displayVersion", "");
        } catch (IOException e) {
            System.err.println("Error reading version info " + fname);
            e.printStackTrace();
            return "";
        }
    }

    private static void showModalDialog(Throwable exception, final String logFileContent) {

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout(0, 20));

        // Replace newline chars. with html newline elements (not needed in most cases)
        String text = exception.toString().replaceAll("\\r?\\n", "<br/>");
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

        // Tab pane
        JPanel centerPanel = new JPanel(new BorderLayout()); 
        final JTabbedPane tabPane = new JTabbedPane();

        // StackTrace tab 
        JTextArea stackTraceArea = new JTextArea();
        stackTraceArea.setText(stacktrace);
        stackTraceArea.setEditable(false);
        stackTraceArea.setCaretPosition(0);
        tabPane.addTab(I18N.getMessage("stackTrace"), new JScrollPane(stackTraceArea));

        // Logfile tab
        final JTextArea logArea = new JTextArea();
        logArea.setText(logFileContent);
        tabPane.addTab(I18N.getMessage("logFile"), new JScrollPane(logArea));

        mainPanel.add(centerPanel, BorderLayout.CENTER);
        centerPanel.add(tabPane, BorderLayout.CENTER);
        centerPanel.add(new JLabel(I18N.getMessage("editBeforeUpload")), BorderLayout.SOUTH);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(1, 3, 20, 0));
        final JButton pastebinUpload = new JButton(I18N.getMessage("uploadLog"));
        pastebinUpload.setIcon(loadIcon("icons/pastebin.png"));
        pastebinUpload.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent event) {

                String title = "Terasology Error Report";
                PastebinPaste paste = Pastebin.newPaste(PASTEBIN_DEVELOPER_KEY, logArea.getText(), title);
                paste.setPasteFormat("apache"); // Apache Log File Format - this is the closest I could find
                paste.setPasteExpireDate(PasteExpireDate.ONE_MONTH);
                uploadPaste(paste);
            }
        });
        // disable upload if log area text field is empty
        logArea.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void removeUpdate(DocumentEvent e) {
                update();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                update();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                update();
            }

            private void update() {
                pastebinUpload.setEnabled(!logArea.getText().isEmpty());
            }
        });
        pastebinUpload.setEnabled(!logArea.getText().isEmpty());        // initial update of the button

        buttonPanel.add(pastebinUpload);
        JButton githubIssueButton = new JButton(I18N.getMessage("reportIssue"));
        githubIssueButton.setIcon(loadIcon("icons/github.png"));
        githubIssueButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                openInBrowser(REPORT_ISSUE_LINK);
            }
        });
        buttonPanel.add(githubIssueButton);
        JButton enterIrc = new JButton(I18N.getMessage("joinIrc"));
        enterIrc.setIcon(loadIcon("icons/irc.png"));
        enterIrc.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                openInBrowser(JOIN_IRC_LINK);
            }
        });
        buttonPanel.add(enterIrc);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Custom close button
        JButton closeButton = new JButton(I18N.getMessage("close"), loadIcon("icons/close.png"));

        String dialogTitle = I18N.getMessage("dialogTitle");
        String version = getVersionInfo();

        if (version != null)
            dialogTitle += " " + version;

        JDialog dialog = createDialog(mainPanel, closeButton, dialogTitle, JOptionPane.ERROR_MESSAGE);
        dialog.setMinimumSize(new Dimension(450, 350));
        dialog.setResizable(true);      // disabled by default
        dialog.setVisible(true);
        dialog.dispose();
    }

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

    private static Icon loadIcon(String fname) {
        try {
            String fullPath = "/" + fname;
            URL rsc = CrashReporter.class.getResource(fullPath);
            if (rsc == null) {
                throw new FileNotFoundException(fullPath);
            }
            BufferedImage image = ImageIO.read(rsc);
            return new ImageIcon(image);
        } catch (IOException e) {
            e.printStackTrace(System.err);
            return null;
        }
    }

    protected static void uploadPaste(final PastebinPaste paste) {
        final JLabel label = new JLabel(I18N.getMessage("waitForUpload"));
        label.setPreferredSize(new Dimension(250, 50));

        final JButton closeButton = new JButton(I18N.getMessage("close"), loadIcon("icons/close.png"));
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
                                public void mouseClicked(java.awt.event.MouseEvent e) {
                                    openInBrowser(url);
                                }

                                ;
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

    private static String getLogFileContent(Path logFile) {
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
