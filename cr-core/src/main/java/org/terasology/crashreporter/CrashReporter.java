// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter;

import javax.swing.JDialog;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import org.terasology.crashreporter.GlobalProperties.KEY;

import java.awt.Dialog;
import java.awt.Dimension;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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
        if (requiresProcessIsolation()) {
            reportInSubprocess(throwable, logFileFolder, mode);
        } else {
            reportInProcess(throwable, logFileFolder, mode);
        }
    }

    private static void reportInProcess(final Throwable throwable, final Path logFileFolder, final MODE mode) {
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

    /**
     * Name of the system property that, when set to {@code true}, forces
     * {@link #requiresProcessIsolation()} to return {@code true} regardless of platform - lets
     * the subprocess path be exercised on non-macOS platforms too, e.g. for testing.
     * <p>
     * Only the property <em>name</em> is a constant here - {@code static final} on this String
     * has no bearing on how live the check is. {@link #requiresProcessIsolation()} calls
     * {@link Boolean#getBoolean(String)} (which reads {@link System#getProperty(String)}) fresh
     * on every invocation; nothing caches the resolved value at class-load time. So this can be
     * set any time before {@link #report(Throwable, java.nio.file.Path)} is actually called -
     * via a {@code -D} JVM launch flag, or programmatically via
     * {@link System#setProperty(String, String)} at runtime.
     */
    private static final String FORCE_PROCESS_ISOLATION_PROPERTY = "org.terasology.crashreporter.forceProcessIsolation";

    /**
     * On macOS, a process launched with {@code -XstartOnFirstThread} (required by GLFW-based
     * games such as Terasology and Destination Sol, so they can create their window) permanently
     * claims the OS's single native UI thread for its own run loop. AWT's native AppKit toolkit
     * needs that very same thread to initialize, and blocks forever if it's already claimed - so
     * a Swing dialog can never appear in such a process.
     * <p>
     * {@code -XstartOnFirstThread} itself isn't visible via
     * {@link ManagementFactory#getRuntimeMXBean()}'s input arguments - it's consumed by the
     * native launcher before the JVM's own argument list is populated - so it can't be detected
     * directly, and not every caller pairs it with a reliably-visible signal like
     * {@code -Djava.awt.headless=true} (Terasology does; Destination Sol doesn't). Rather than
     * depend on a convention only some callers follow, isolate unconditionally on macOS - the
     * cost of an unnecessary subprocess is small, and a caller that isn't actually
     * {@code -XstartOnFirstThread}-constrained just gets the dialog shown from a fresh process
     * instead of in-process, with no functional difference either way.
     */
    private static boolean requiresProcessIsolation() {
        if (Boolean.getBoolean(FORCE_PROCESS_ISOLATION_PROPERTY)) {
            return true;
        }
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase().contains("mac");
    }

    private static void reportInSubprocess(Throwable throwable, Path logFileFolder, MODE mode) {
        String javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        String message = throwable.getLocalizedMessage();

        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(CrashReporter.class.getName());
        command.add(throwable.getClass().getName());
        command.add(message == null ? "" : message);
        command.add(logFileFolder == null ? "" : logFileFolder.toAbsolutePath().toString());
        command.add(mode.name());

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.INHERIT);
            // -XstartOnFirstThread is exactly the constraint requiresProcessIsolation() isolates
            // against - every JVM reads these two env vars at startup automatically, so if either
            // carries that flag, ProcessBuilder's default environment inheritance would hand the
            // exact same constraint straight back to the subprocess we're spawning to escape it.
            processBuilder.environment().remove("JAVA_TOOL_OPTIONS");
            processBuilder.environment().remove("_JAVA_OPTIONS");
            processBuilder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Entry point used internally to relaunch the reporter in an isolated process, see
     * {@link #reportInSubprocess}. Not intended to be invoked directly by callers of this
     * library.
     */
    public static void main(String[] args) {
        String throwableClassName = args[0];
        String message = args[1].isEmpty() ? null : args[1];
        Path logFileFolder = args[2].isEmpty() ? null : Paths.get(args[2]);
        MODE mode = MODE.valueOf(args[3]);

        reportInProcess(reconstructThrowable(throwableClassName, message), logFileFolder, mode);
    }

    /**
     * Best-effort reconstruction of the original throwable's identity in the new process: a real
     * exception can't be passed across a process boundary, and
     * {@link org.terasology.crashreporter.pages.ErrorMessagePanel} only ever reads
     * {@code getClass().getSimpleName()} and {@code getLocalizedMessage()} for display, so
     * that's all that needs to survive the trip.
     */
    private static Throwable reconstructThrowable(String className, String message) {
        try {
            // Don't initialize (run static initializers of) a class from an arbitrary name
            // before confirming it's actually a Throwable subtype we intend to instantiate.
            Class<?> throwableClass = Class.forName(className, false, CrashReporter.class.getClassLoader());
            if (Throwable.class.isAssignableFrom(throwableClass)) {
                return (Throwable) throwableClass.getConstructor(String.class).newInstance(message);
            }
        } catch (ReflectiveOperationException e) {
            // fall through to the generic Throwable below
        }
        return new Throwable(message == null ? className : className + ": " + message);
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
