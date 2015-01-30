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

    protected static void showModalDialog(Throwable t, String logFileContent)
	{
        // Custom close button
        JButton closeButton = new JButton(I18N.getMessage("close"), Resources.loadIcon("icons/close.png"));

        String dialogTitle = I18N.getMessage("dialogTitle");
        String version = Resources.getVersion();

        if (version != null)
            dialogTitle += " " + version;

        MainPanel panel = new MainPanel(t, logFileContent);
		JDialog dialog = createDialog(panel, closeButton, dialogTitle, JOptionPane.ERROR_MESSAGE);
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
