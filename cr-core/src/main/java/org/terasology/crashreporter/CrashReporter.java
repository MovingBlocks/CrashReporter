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

import javax.swing.JDialog;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import org.terasology.crashreporter.GlobalProperties.KEY;

import java.awt.Dialog;
import java.awt.Dimension;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

/**
 * Displays a detailed error message and provides some options to communicate with devs.
 * Errors are reported to {@link System#err}
 */
public final class CrashReporter {

    public enum MODE {
        CRASH_REPORTER,
        ISSUE_REPORTER,
        FEEDBACK
    }

    private CrashReporter() {
        // don't create any instances
    }

    /**
     * By default, it is a CrashReporter
     * @param throwable     the exception to report
     * @param logFileFolder the log file folder or <code>null</code>
     */
    public static void report(final Throwable throwable, final Path logFileFolder) {
        report(throwable, logFileFolder, MODE.CRASH_REPORTER);
    }

    /**
     * Can be called from any thread.
     * @param throwable the exception to report
     * @param logFileFolder the log file folder or <code>null</code>
     * @param mode crash reporter, issue reporter or feedback window
     */
    public static void report(final Throwable throwable, final Path logFileFolder, final MODE mode) {
        // Swing element methods must be called in the swing thread
        try {
            SwingUtilities.invokeAndWait(new Runnable() {

                @Override
                public void run() {
                    LookAndFeel oldLaF = UIManager.getLookAndFeel();
                    try {
                        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    GlobalProperties properties = new GlobalProperties();
                    showModalDialog(throwable, properties, logFileFolder, mode);
                    try {
                        UIManager.setLookAndFeel(oldLaF);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (InvocationTargetException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected static void showModalDialog(Throwable throwable, GlobalProperties properties, Path logFolder, MODE mode) {
        String dialogTitle;
        switch (mode) {
            case FEEDBACK: dialogTitle = I18N.getMessage("feedbackTitle"); break;//For future feedback use
            case ISSUE_REPORTER: dialogTitle = I18N.getMessage("issueTitle"); break;
            default: dialogTitle = I18N.getMessage("crashTitle"); break;
        }
        String version = Resources.getVersion();

        if (version != null) {
            dialogTitle += " " + version;
        }

        RootPanel panel = new RootPanel(throwable, properties, logFolder, mode);
        JDialog dialog = new JDialog((Dialog) null, dialogTitle, false);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setIconImage(Resources.loadImage(properties.get(KEY.RES_SERVER_ICON)));
        dialog.setContentPane(panel);
        dialog.setMinimumSize(new Dimension(600, 400));
        dialog.setLocationRelativeTo(null);
        dialog.setResizable(true);      // disabled by default
        dialog.setVisible(true);
    }
}
