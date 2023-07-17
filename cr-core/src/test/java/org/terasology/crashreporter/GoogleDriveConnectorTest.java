// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter;

import com.google.api.services.drive.model.File;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.crashreporter.logic.GoogleDriveConnector;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.util.Collections;

public class GoogleDriveConnectorTest {

    private static final Logger logger = LoggerFactory.getLogger(GoogleDriveConnectorTest.class);

    private static GoogleDriveConnector gdrive;

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @BeforeClass
    public static void setupClass() throws GeneralSecurityException, IOException {
        gdrive = new GoogleDriveConnector();
    }

    @Test
    public void listingTest() throws IOException {
        for (File f : gdrive.retrieveAllFiles()) {
            logger.info("Found file: {} - {} bytes: {}", f.getOriginalFilename(), f.getSize(), f.getWebContentLink());
        }
    }

    @Test
    public void downloadTest() throws IOException {
        String id = "0B5l2RT_UOCXRdFlSZ3lNR0VpMW8";
        java.io.File tempFolder = testFolder.newFolder("downloads");

        File bannerTextImage = gdrive.getFileForID(id);
        gdrive.downloadToFile(bannerTextImage, tempFolder, true);
    }

    @Test
    public void uploadTest() throws IOException {
        java.io.File tempFile = testFolder.newFile();
        Files.write(tempFile.toPath(), Collections.singletonList("This is a test file."), StandardOpenOption.WRITE);
        try (InputStream stream = Files.newInputStream(tempFile.toPath())) {
            File uploadedFile = gdrive.uploadFile(stream, tempFile.getName(), true);
            Assert.assertNotNull(uploadedFile);
        }
    }
}
