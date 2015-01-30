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
import org.terasology.crashreporter.Resources;

public class UploadPanel extends JPanel
{
    /**
     * Username Terasology
     * eMail pastebin@terasology.org
     */
    private static final String PASTEBIN_DEVELOPER_KEY = "1ed92217030bd6c2570fac91bcbfee78";

	private JButton uploadButton;
	
	private JLabel label;

    public UploadPanel(String logFileContent) {
        setLayout(new BorderLayout(20, 20));
    	label = new JLabel("2343");
        label.setPreferredSize(new Dimension(250, 50));
        add(label, BorderLayout.NORTH);
        
        uploadButton = new JButton("Upload", Resources.loadIcon("icons/Arrow-up-icon.png"));
        uploadButton.setPreferredSize(new Dimension(250, 50));
        add(uploadButton, BorderLayout.CENTER);
        
        uploadButton.setEnabled(logFileContent != null  && !logFileContent.isEmpty());
        uploadButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				uploadButton.setText(I18N.getMessage("waitForUpload"));
				uploadButton.setEnabled(false);
			}
		});
    }
    
    @Override
    public void setVisible(boolean aFlag)
    {
    	super.setVisible(aFlag);
    	
    	if (!aFlag) {
    		return;
    	}
    	
    	// TODO: check if another upload is necessary/possible
    }
    
    private void upload(String content) {
        String title = "Terasology Error Report";
        PastebinPaste paste = Pastebin.newPaste(PASTEBIN_DEVELOPER_KEY, content, title);
        paste.setPasteFormat("apache"); // Apache Log File Format - this is the closest I could find
        paste.setPasteExpireDate(PasteExpireDate.ONE_MONTH);

        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                try {
                    final PastebinLink link = paste.paste();

                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                        	uploadSuccess(link);
                        }
                    });
                } catch (final PasteException e) {
                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                        	uploadFailed(e);
                        }
                    });
                }
            }
        };

        Thread thread = new Thread(runnable, "Upload paste");
        thread.start();
    }
    
    private void uploadSuccess(PastebinLink link) {
        final String url = link.getLink().toString();
        String uploadText = I18N.getMessage("uploadComplete");
        label.setText(String.format("<html>%s <a href=\"%s\">%s</a></html>", uploadText, url, url));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
			public void mouseClicked(java.awt.event.MouseEvent e) {
                openInBrowser(url);
            }
        });
    }
    
    private void uploadFailed(Exception e) {
        String uploadFailed = I18N.getMessage("uploadFailed");
        label.setText("<html>" + uploadFailed + ":<br/> " + e.getLocalizedMessage() + "</html>");
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
