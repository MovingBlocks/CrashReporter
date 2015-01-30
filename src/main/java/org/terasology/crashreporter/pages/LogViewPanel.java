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
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.jpaste.exceptions.PasteException;
import org.jpaste.pastebin.PasteExpireDate;
import org.jpaste.pastebin.Pastebin;
import org.jpaste.pastebin.PastebinLink;
import org.jpaste.pastebin.PastebinPaste;
import org.terasology.crashreporter.I18N;

public class LogViewPanel extends JPanel
{
    public LogViewPanel(String logFileContent) {

        JPanel mainPanel = this;
        mainPanel.setLayout(new BorderLayout(0, 10));

        // Replace newline chars. with html newline elements (not needed in most cases)
        mainPanel.setPreferredSize(new Dimension(750, 450));

        mainPanel.add(new JLabel("<html><h3>Upload log file</h3></html>"), BorderLayout.NORTH);

        final JTextArea logArea = new JTextArea();
        logArea.setText(logFileContent);
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        String message = I18N.getMessage("editBeforeUpload");
        int idx = message.indexOf(':');
        if (idx > 0) {
        	message = "<b>" + message.substring(0, idx) + "</b>" + message.substring(idx);
        }
		JLabel editHintLabel = new JLabel("<html>" + message + "</html>");
		add(editHintLabel, BorderLayout.SOUTH);
    }
    
}
