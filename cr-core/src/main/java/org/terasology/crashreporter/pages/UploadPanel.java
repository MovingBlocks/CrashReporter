// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import org.terasology.crashreporter.GlobalProperties;
import org.terasology.crashreporter.GlobalProperties.KEY;
import org.terasology.crashreporter.I18N;
import org.terasology.crashreporter.Resources;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The panel where the log file content is uploaded to some web storage
 */
public class UploadPanel extends JPanel {

    private static final long serialVersionUID = -8247883237201535146L;

    private static final long DEFAULT_UPLOAD_TIMEOUT_SECONDS = 30;

    private JButton uploadPasteBinButton;
    private boolean isComplete;
    private URL uploadURL;

    private JLabel statusLabel;

    private final Supplier<String> textSupplier;

    private final Supplier<String> logFileNameSupplier;

    private final long uploadTimeoutSeconds;

    private JButton uploadSkipButton;

    private JLabel titleLabel;

    public UploadPanel(GlobalProperties properties, Supplier<String> logTextSupp, Supplier<String> logFileNameSupp) {
        this(properties, logTextSupp, logFileNameSupp, DEFAULT_UPLOAD_TIMEOUT_SECONDS);
    }

    /**
     * @param uploadTimeoutSeconds how long {@link #upload} waits for the upload {@link Callable} before treating it
     *         as failed - package-private constructor so tests can use a short timeout instead of
     *         {@link #DEFAULT_UPLOAD_TIMEOUT_SECONDS}.
     */
    UploadPanel(GlobalProperties properties, Supplier<String> logTextSupp, Supplier<String> logFileNameSupp,
                long uploadTimeoutSeconds) {

        this.textSupplier = logTextSupp;
        this.logFileNameSupplier = logFileNameSupp;
        this.uploadTimeoutSeconds = uploadTimeoutSeconds;
        setLayout(new BorderLayout(50, 20));
        statusLabel = new JLabel(I18N.getMessage("noUpload"), SwingConstants.RIGHT);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        statusLabel.setBorder(new EmptyBorder(0, 5, 0, 5));
        titleLabel = new JLabel(Resources.loadIcon(properties.get(KEY.RES_UPLOAD_TITLE_IMAGE)), SwingConstants.CENTER);
        titleLabel.setBorder(new EmptyBorder(10, 0, 0, 0));
        add(titleLabel, BorderLayout.NORTH);

        Font buttonFont = getFont().deriveFont(Font.BOLD).deriveFont(14f);

        JPanel hosterPanel = new JPanel(new GridLayout(0, 1, 0, 20));
        hosterPanel.setBorder(new EmptyBorder(0, 50, 0, 50));
        uploadPasteBinButton = new JButton("PasteBin", Resources.loadIcon(properties.get(KEY.RES_PASTEBIN_ICON)));
        uploadPasteBinButton.setFont(buttonFont);
        uploadPasteBinButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                statusLabel.setText(I18N.getMessage("waitForUpload"));
                uploadPasteBinButton.setEnabled(false);

                String text = textSupplier.get();
                upload(new PastebinUploadRunnable(text));
            }
        });
        hosterPanel.add(uploadPasteBinButton);

        uploadSkipButton = new JButton(I18N.getMessage("skipUpload"),
                Resources.loadIcon(properties.get(KEY.RES_SKIP_UPLOAD_ICON)));
        uploadSkipButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uploadSkipButton.setEnabled(false);
                UploadPanel.this.firePropertyChange("pageComplete", Boolean.FALSE, Boolean.TRUE);
                isComplete = true;
            }
        });
        uploadSkipButton.setFont(buttonFont);
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

        firePropertyChange("pageComplete", !isComplete, isComplete);

        String fname = logFileNameSupplier.get();
        titleLabel.setText("<html><h3>" + I18N.getMessage("uploadLog2") + "</h3>" + fname + "</html>");
    }

    /**
     * @return the URL of the log file that was uploaded or <code>null</code>
     */
    public URL getUploadedFileURL() {
        return uploadURL;
    }

    /** Package-private test hook - equivalent to a real "PasteBin" click, but with a caller-supplied Callable. */
    void uploadForTesting(Callable<URL> callable) {
        upload(callable);
    }

    /**
     * Runs {@code callable} on its own thread and waits up to {@link #uploadTimeoutSeconds} for it
     * to finish - {@code PastebinUploadRunnable} makes a real HTTP call with no timeout of its own,
     * so without one here a slow or unreachable server leaves the button disabled and the status
     * label reading "please wait" forever, with no way for the user to tell the difference between
     * "still working" and "will never finish".
     */
    private void upload(final Callable<URL> callable) {
        final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "Upload");
                thread.setDaemon(true);
                return thread;
            }
        });
        final Future<URL> future = executor.submit(callable);

        Thread watcher = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    awaitUpload(future, uploadTimeoutSeconds, new Consumer<URL>() {
                        @Override
                        public void accept(URL link) {
                            uploadSuccess(link);
                        }
                    }, new Consumer<Exception>() {
                        @Override
                        public void accept(Exception e) {
                            uploadFailed(e);
                        }
                    });
                } finally {
                    executor.shutdownNow();
                }
            }
        }, "Upload-Watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    /**
     * Waits up to {@code timeoutSeconds} for {@code future}, then dispatches to exactly one of the
     * two callbacks - split out from {@link #upload} as a plain, Swing-free method so the timeout
     * and exception-unwrapping logic can be tested directly against a real {@link Future} without
     * needing a full {@code UploadPanel}/button-click harness.
     */
    static void awaitUpload(Future<URL> future, long timeoutSeconds, Consumer<URL> onSuccess, Consumer<Exception> onFailure) {
        try {
            URL link = future.get(timeoutSeconds, TimeUnit.SECONDS);
            onSuccess.accept(link);
        } catch (TimeoutException e) {
            future.cancel(true);
            onFailure.accept(new IOException(
                    "Upload timed out after " + timeoutSeconds + "s - the server may be unreachable", e));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            onFailure.accept(cause instanceof Exception ? (Exception) cause : e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            onFailure.accept(e);
        }
    }

    private void updateStatus() {
        if (uploadURL != null) {
            String uploadText = I18N.getMessage("uploadComplete");
            statusLabel.setText(String.format("<html>%s <a href=\"%s\">%s</a></html>", uploadText, uploadURL, uploadURL));
            statusLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            statusLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    openInBrowser(uploadURL.toString());
                }
            });
        } else {
            statusLabel.setText(I18N.getMessage("noUpload"));
        }
    }

    private void uploadSuccess(final URL link) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                uploadURL = link;
                updateStatus();
                uploadSkipButton.setEnabled(false);
                uploadPasteBinButton.setEnabled(true);
                firePropertyChange("pageComplete", Boolean.FALSE, Boolean.TRUE);
                isComplete = true;
            }
        });
    }

    private void uploadFailed(final Exception e) {
        // Printed unconditionally, not just shown in the dialog below: a JOptionPane only reaches
        // whoever is watching the screen at that exact moment, and leaves no trace at all once
        // it's dismissed - nothing else in this codebase logs upload failures anywhere. Whoever
        // launched this process (a script, a supervisor, a developer tailing output) needs to be
        // able to find out what happened after the fact, not just the person who happened to be
        // looking right then.
        e.printStackTrace(System.err);

        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                String uploadFailed = I18N.getMessage("uploadFailed");
                JOptionPane.showMessageDialog(null, e.getLocalizedMessage(), uploadFailed, JOptionPane.ERROR_MESSAGE);
                uploadPasteBinButton.setEnabled(true);
                updateStatus();
            }
        });
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
