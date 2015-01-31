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

package org.terasology.crashreporter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

import org.terasology.crashreporter.pages.ErrorMessagePanel;
import org.terasology.crashreporter.pages.FinalActionsPanel;
import org.terasology.crashreporter.pages.LogViewPanel;
import org.terasology.crashreporter.pages.UploadPanel;
import org.terasology.layout.RXCardLayout;

/**
 * The central panel that contains the wizard pages.
 * @author Martin Steiger
 */
public class RootPanel extends JPanel {
    private static final long serialVersionUID = -907008390086125919L;

    /**
     * @param exception the exception that occurred
     * @param logFileContent the content of the log file or <code>null</code>
     */
    public RootPanel(Throwable exception, Path logFile) {

        setLayout(new BorderLayout());
        Font buttonFont = getFont().deriveFont(Font.BOLD).deriveFont(14f);

        Icon prevIcon = Resources.loadIcon("icons/Arrow-Prev-icon.png");
        Icon nextIcon = Resources.loadIcon("icons/Arrow-Next-icon.png");
        Icon closeIcon = Resources.loadIcon("icons/Actions-dialog-close-icon.png");

        List<JComponent> pages = new ArrayList<>();
        pages.add(new ErrorMessagePanel(exception));
        pages.add(new LogViewPanel(logFile));
        pages.add(new UploadPanel("sdfsdfd"));
        pages.add(new FinalActionsPanel());

        JPanel mainPanel = new JPanel();
        RXCardLayout cards = new RXCardLayout(5, 5);
        mainPanel.setLayout(cards);

        for (JComponent page : pages) {
            mainPanel.add(page);
        }

        JLabel image = new JLabel(Resources.loadIcon("icons/banner.jpg"));
        add(image, BorderLayout.WEST);
        add(mainPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        Border lineBorder = new MatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY);
        buttonPanel.setBorder(BorderFactory.createCompoundBorder(lineBorder, new EmptyBorder(10, 25, 10, 25)));
        buttonPanel.setLayout(new GridLayout(1, 0, 200, 0));

        JButton prevButton = new JButton(I18N.getMessage("prev"), prevIcon);
        JButton nextButton = new JButton(I18N.getMessage("next"), nextIcon);

        prevButton.setFont(buttonFont);
        prevButton.setHorizontalAlignment(SwingConstants.LEFT);
        prevButton.setEnabled(false);
        prevButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (cards.isPreviousCardAvailable()) {
                    cards.previous(mainPanel);
                    prevButton.setEnabled(cards.isPreviousCardAvailable());
                    nextButton.setText(I18N.getMessage("next"));
                    nextButton.setIcon(nextIcon);
                }
            }
        });

        nextButton.setFont(buttonFont);
        nextButton.setHorizontalAlignment(SwingConstants.RIGHT);
        nextButton.setHorizontalTextPosition(SwingConstants.LEFT);
        nextButton.setEnabled(cards.isNextCardAvailable());
        nextButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (cards.isNextCardAvailable()) {
                    cards.next(mainPanel);
                    prevButton.setEnabled(true);
                    if (!cards.isNextCardAvailable()) {
                        nextButton.setText(I18N.getMessage("close"));
                        nextButton.setIcon(closeIcon);
                    }

                } else {
                    Window wnd = (Window) RootPanel.this.getTopLevelAncestor();
                    wnd.dispatchEvent(new WindowEvent(wnd, WindowEvent.WINDOW_CLOSING));
                }
            }
        });

        buttonPanel.add(prevButton);
        buttonPanel.add(nextButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

}
