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

import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.event.ComponentEvent;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

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
        String dialogTitle = I18N.getMessage("dialogTitle");
        String version = Resources.getVersion();

        if (version != null)
            dialogTitle += " " + version;

        RootPanel panel = new RootPanel(t, logFileContent);
		JDialog dialog = new JDialog((Dialog) null, dialogTitle, true);
		dialog.setContentPane(panel);
        dialog.setMinimumSize(new Dimension(550, 350));
//        dialog.setMaximumSize(new Dimension(750, 450));
		dialog.setLocationRelativeTo(null);
        dialog.setResizable(true);      // disabled by default
        dialog.setVisible(true);
        dialog.dispose();
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
