// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import com.google.common.collect.Lists;

import org.terasology.crashreporter.CrashReporter;
import org.terasology.crashreporter.GlobalProperties;
import org.terasology.crashreporter.GlobalProperties.KEY;
import org.terasology.crashreporter.I18N;
import org.terasology.crashreporter.Resources;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Shows the error message plus stack trace.
 */
public class ErrorMessagePanel extends JPanel {

    private static final long serialVersionUID = 8449689452512733452L;

    private final JTabbedPane tabPane;
    private final List<JTextArea> textAreas = Lists.newArrayList();
    private final List<Path> logFiles;

    private final LogUpdateWorker logUpdateWorker;

    // logReaders is the list of each log file's reader
    private final List<RandomAccessFile> logReaders = Lists.newArrayList();

    /**
     * @param exception     the exception to display
     * @param logFileFolder the folder that contains the relevant log files
     * @param properties    the properties for this dialog wizard
     * @param mode          crash reporter, issue reporter or feedback window
     */
    public ErrorMessagePanel(GlobalProperties properties, Throwable exception, Path logFileFolder, CrashReporter.MODE mode) {

        JPanel mainPanel = this;
        mainPanel.setLayout(new BorderLayout(0, 5));

        String text = "<b>" + exception.getClass().getSimpleName() + "</b>";
        String exMessage = exception.getLocalizedMessage();
        if (exMessage != null) {
            text += ": " + exMessage;
        }
        // Replace newline chars. with html newline elements (not needed in most cases)
        text = text.replaceAll("\\r?\\n", "<br/>");

        String firstLine;
        Icon titleIcon;
        switch (mode) {
            case FEEDBACK:
                //For future feedback mode
                firstLine = I18N.getMessage("firstLineFeedback");
                titleIcon = Resources.loadIcon(properties.get(KEY.RES_INFO_TITLE_IMAGE));
                break;
            case ISSUE_REPORTER:
                firstLine = I18N.getMessage("firstLineIssue");
                titleIcon = Resources.loadIcon(properties.get(KEY.RES_INFO_TITLE_IMAGE));
                break;
            default:
                firstLine = I18N.getMessage("firstLineCrash");
                titleIcon = Resources.loadIcon(properties.get(KEY.RES_ERROR_TITLE_IMAGE));
                break;
        }
        String htmlText = "<html><h3>" + firstLine + "</h3>" + text + "</html>";
        JLabel message = new JLabel(htmlText, titleIcon, SwingConstants.LEFT);

        mainPanel.add(message, BorderLayout.NORTH);

        logFiles = findLogs(logFileFolder);
        sortLogFiles(logFiles);
        tabPane = new JTabbedPane();

        if (!logFiles.isEmpty()) {
            for (Path logFile : logFiles) {
                final String logFileContent = readLogFileContent(logFile);
                JTextArea logArea = new JTextArea();
                logArea.setText(logFileContent);
                String tabName = logFileFolder.relativize(logFile).toString();
                tabPane.addTab(tabName, new JScrollPane(logArea));
                textAreas.add(logArea);

                // Add reader of each log file in logReaders
                try {
                    File file = logFile.toFile();
                    RandomAccessFile logReader = new RandomAccessFile(file, "r");
                    logReader.seek(file.length());
                    logReaders.add(logReader);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            add(tabPane, BorderLayout.CENTER);
        } else {
            JLabel missingFilesLabel = new JLabel(I18N.getMessage("noLogFiles"), SwingConstants.CENTER);
            missingFilesLabel.setFont(missingFilesLabel.getFont().deriveFont(Font.BOLD, 14f));
            missingFilesLabel.setBorder(BorderFactory.createEtchedBorder());
            add(missingFilesLabel, BorderLayout.CENTER);
        }

        String readablePath = logFileFolder != null
                ? logFileFolder.toAbsolutePath().normalize().toString()
                : I18N.getMessage("notSpecified");

        String loc = I18N.getMessage("fileLocation") + ": " + readablePath;

        String editMessage = I18N.getMessage("editBeforeUpload");

        // mark the text before ":" in bold - English NOTE:
        int idx = editMessage.indexOf(':');
        if (idx > 0) {
            editMessage = "<b>" + editMessage.substring(0, idx) + "</b>" + editMessage.substring(idx);
        }
        JLabel editHintLabel = new JLabel("<html>" + loc + "<br/><br/>" + editMessage + "</html>");
        add(editHintLabel, BorderLayout.SOUTH);

        // Initialize log folder watching
        logUpdateWorker = new LogUpdateWorker(logFileFolder);
        PropertyChangeListener logChangeListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName() == LogUpdateWorker.CREATED) {
                    Path newLogPath = (Path)evt.getNewValue();
                    addNewTab(logFileFolder, newLogPath);
                }
                else if (evt.getPropertyName() == LogUpdateWorker.MODIFIED) {
                    Path changedLogPath = (Path)evt.getNewValue();
                    updateLog(changedLogPath);
                }
            }
        };
        logUpdateWorker.addPropertyChangeListener(logChangeListener);
        logUpdateWorker.execute();
    }

    private static void sortLogFiles(List<Path> files) {
        files.sort(new Comparator<Path>() {

            @Override
            public int compare(Path p0, Path p1) {
                try {
                    BasicFileAttributes attr0 = Files.readAttributes(p0, BasicFileAttributes.class);
                    BasicFileAttributes attr1 = Files.readAttributes(p1, BasicFileAttributes.class);
                    FileTime time0 = attr0.creationTime();
                    FileTime time1 = attr1.creationTime();
                    return time0.compareTo(time1);
                } catch (Exception e) {
                    // ignore silently
                    return 0;
                }
            }

        }.reversed());  // invert sort order
    }

    @Override
    public void setVisible(boolean aFlag) {
        super.setVisible(aFlag);

        if (!aFlag) {
            return;
        }

        if (logFiles.isEmpty()) {
            firePropertyChange("pageComplete", true, false);
        }
    }

    /**
     * @param logFileFolder
     * @return
     */
    private List<Path> findLogs(Path logFileFolder) {
        // JAVA8: this should work, too
        // Files.walk(logFileFolder).filter(path -> path.toString().endsWith(".log")).collect(Collectors.toList());

        final List<Path> results = Lists.newArrayList();

        SimpleFileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

                if (file.toString().endsWith(".log")) {
                    results.add(file);
                }

                return FileVisitResult.CONTINUE;
            }
        };

        if (logFileFolder != null) {
            try {
                // maxDepth == 1 means that only the current folder is searched
                Files.walkFileTree(logFileFolder, EnumSet.noneOf(FileVisitOption.class), 2, visitor);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return results;
    }

    /**
     * @return the (edited) log file contents
     */
    public String getLog() {
        int idx = tabPane.getSelectedIndex();
        return idx >= 0 ? textAreas.get(idx).getText() : "";
    }

    /**
     * @return the original log file
     */
    public Path getLogFile() {
        int idx = tabPane.getSelectedIndex();
        return idx >= 0 ? logFiles.get(idx) : null;
    }

    private static String readLogFileContent(Path logFile) {
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

    /**
     * Add a new Tab when there is a new log file
     * @param logFileFolder log folder
     * @param newLogPath    path of the new log file
     */
    private void addNewTab(Path logFileFolder, Path newLogPath) {
        logFiles.add(newLogPath);
        sortLogFiles(logFiles);
        int index = logFiles.indexOf(newLogPath);

        String tabName = logFileFolder.relativize(newLogPath).toString();
        final String logFileContent = readLogFileContent(newLogPath);
        JTextArea logArea = new JTextArea();
        logArea.setText(logFileContent);
        textAreas.add(index,logArea);
        tabPane.insertTab(tabName, null, new JScrollPane(logArea), null, index);
        tabPane.updateUI();

        File file = newLogPath.toFile();
        try {
            RandomAccessFile logReader = new RandomAccessFile(file, "r");
            logReader.seek(file.length());
            logReaders.add(index, logReader);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update log information
     * @param changedLogPath path of the changed log file
     */
    private void updateLog(Path changedLogPath) {
        int index = logFiles.indexOf(changedLogPath);
        if (index != -1) {
            RandomAccessFile logReader = logReaders.get(index);
            JTextArea jTextArea = textAreas.get(index);
            try {
                String line = logReader.readLine();
                while (line != null) {
                    jTextArea.append(line);
                    jTextArea.append(System.lineSeparator());
                    line = logReader.readLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.updateUI();
    }
}
