// Copyright 2026 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import org.terasology.crashreporter.I18N;

import javax.swing.JOptionPane;
import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;

/**
 * Opens a link in the user's default browser, used by every "open this URL" button in the dialog
 * (Discord, GitHub issue, PasteBin result, ...).
 *
 * <p>Both {@code url} being {@code null}/empty (a {@link org.terasology.crashreporter.GlobalProperties}
 * key that a downstream app never configured, or - for the GitHub button - was never filled in) and
 * {@link java.awt.Desktop#browse} failing (no browser configured, unsupported platform, ...) used to
 * throw straight out of a Swing {@code ActionListener} on the EDT: nothing catches that, so the
 * button just did nothing with no trace anywhere the click ever happened. Every failure here is now
 * caught, printed to stderr, and shown to the user - the same "never silently lose it" fix applied to
 * PasteBin upload failures.
 */
final class BrowserLauncher {

    private BrowserLauncher() {
    }

    static void open(String url) {
        open(url, BrowserLauncher::browseWithDesktop);
    }

    static void open(String url, Consumer<URI> browser) {
        open(url, browser, BrowserLauncher::report);
    }

    /**
     * @param browser how to actually hand the parsed {@link URI} off to the platform, and
     * @param onFailure what to do with a failure (missing/invalid url, or {@code browser} throwing) -
     *         package-private seam so tests can verify the null/blank-url guard and the exception
     *         handling without a real {@link java.awt.Desktop}/browser or popping up a real
     *         {@link JOptionPane}.
     */
    static void open(String url, Consumer<URI> browser, Consumer<Exception> onFailure) {
        if (url == null || url.isEmpty()) {
            onFailure.accept(new IllegalStateException("No link configured"));
            return;
        }
        try {
            browser.accept(new URI(url));
        } catch (URISyntaxException | RuntimeException e) {
            onFailure.accept(e);
        }
    }

    private static void report(Exception e) {
        e.printStackTrace(System.err);
        JOptionPane.showMessageDialog(null, e.getLocalizedMessage(), I18N.getMessage("openLinkFailed"),
                JOptionPane.ERROR_MESSAGE);
    }

    private static void browseWithDesktop(URI uri) {
        if (!Desktop.isDesktopSupported()) {
            throw new IllegalStateException("Desktop integration is not supported on this platform");
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            throw new IllegalStateException("Opening a browser is not supported on this platform");
        }
        try {
            desktop.browse(uri);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
