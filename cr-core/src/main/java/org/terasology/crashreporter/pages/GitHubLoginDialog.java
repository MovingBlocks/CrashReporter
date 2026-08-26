// Copyright 2026 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.terasology.crashreporter.I18N;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Desktop;
import java.awt.Window;
import java.io.IOException;
import java.net.URI;
import java.net.URL;

/**
 * Logs in via {@link GitHubDeviceLogin}, lets the user review/edit the pre-filled title and body,
 * then submits via {@link GitHubIssueApiClient} - no URL length limit, unlike
 * {@link GitHubIssueLinkBuilder}'s browser-prefill link.
 */
public class GitHubLoginDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JLabel waitingLabel = new JLabel();
    private final JTextField titleField = new JTextField();
    private final JTextArea bodyArea = new JTextArea();
    private final JLabel resultLabel = new JLabel();

    private final String clientId;
    private final String owner;
    private final String repo;

    private volatile String accessToken;

    public GitHubLoginDialog(Window ownerWindow, String clientId, String owner, String repo,
                              String title, String body) {
        super(ownerWindow, I18N.getMessage("githubLoginTitle"), ModalityType.APPLICATION_MODAL);
        this.clientId = clientId;
        this.owner = owner;
        this.repo = repo;

        setSize(500, 400);
        setLocationRelativeTo(ownerWindow);
        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);

        content.add(buildWaitingCard(), "waiting");
        content.add(buildReviewCard(title, body), "review");
        content.add(buildResultCard(), "result");
        cards.show(content, "waiting");

        startLogin();
    }

    private JPanel buildWaitingCard() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        waitingLabel.setText(I18N.getMessage("githubLoginRequesting"));
        waitingLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(waitingLabel, BorderLayout.CENTER);
        JButton cancel = new JButton(I18N.getMessage("githubLoginCancel"));
        cancel.addActionListener(e -> dispose());
        panel.add(cancel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildReviewCard(String title, String body) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        titleField.setText(title);
        bodyArea.setText(body);
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);

        JPanel top = new JPanel(new BorderLayout(5, 0));
        top.add(new JLabel(I18N.getMessage("githubLoginReviewTitleLabel")), BorderLayout.WEST);
        top.add(titleField, BorderLayout.CENTER);
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(bodyArea), BorderLayout.CENTER);

        JButton submit = new JButton(I18N.getMessage("githubLoginSubmit"));
        submit.addActionListener(e -> submitIssue());
        JButton cancel = new JButton(I18N.getMessage("githubLoginCancel"));
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel();
        buttons.add(cancel);
        buttons.add(submit);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildResultCard() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        resultLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(resultLabel, BorderLayout.CENTER);
        JButton close = new JButton(I18N.getMessage("close"));
        close.addActionListener(e -> dispose());
        panel.add(close, BorderLayout.SOUTH);
        return panel;
    }

    private void startLogin() {
        Thread thread = new Thread(() -> {
            try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
                GitHubDeviceLogin.DeviceCode code = GitHubDeviceLogin.requestDeviceCode(client, clientId);
                SwingUtilities.invokeLater(() -> showCode(code));
                accessToken = GitHubDeviceLogin.pollForAccessToken(client, clientId, code);
                SwingUtilities.invokeLater(() -> cards.show(content, "review"));
            } catch (IOException | InterruptedException e) {
                SwingUtilities.invokeLater(() -> showFailure(e));
            }
        }, "GitHubDeviceLogin");
        thread.setDaemon(true);
        thread.start();
    }

    private void showCode(GitHubDeviceLogin.DeviceCode code) {
        openInBrowser(code.getVerificationUri());
        waitingLabel.setText("<html><center>" + I18N.getMessage("githubLoginWaiting",
                code.getVerificationUri(), code.getUserCode()) + "</center></html>");
    }

    private void submitIssue() {
        cards.show(content, "waiting");
        waitingLabel.setText(I18N.getMessage("githubLoginSubmitting"));
        String title = titleField.getText();
        String body = bodyArea.getText();
        Thread thread = new Thread(() -> {
            try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
                URL issueUrl = GitHubIssueApiClient.createIssue(client, accessToken, owner, repo, title, body);
                SwingUtilities.invokeLater(() -> showSuccess(issueUrl));
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> showFailure(e));
            }
        }, "GitHubIssueSubmit");
        thread.setDaemon(true);
        thread.start();
    }

    private void showSuccess(URL issueUrl) {
        resultLabel.setText("<html>" + I18N.getMessage("githubLoginSuccess", issueUrl) + "</html>");
        cards.show(content, "result");
        openInBrowser(issueUrl.toString());
    }

    private void showFailure(Exception e) {
        resultLabel.setText("<html>" + I18N.getMessage("githubLoginFailed", e.getLocalizedMessage()) + "</html>");
        cards.show(content, "result");
    }

    private static void openInBrowser(String url) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
    }
}
