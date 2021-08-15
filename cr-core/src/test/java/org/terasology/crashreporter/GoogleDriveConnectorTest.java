// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter;

import com.google.api.services.drive.model.File;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.crashreporter.logic.GoogleDriveConnector;

import java.io.IOException;
import java.security.GeneralSecurityException;

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
            logger.info("Found file: {} - {} bytes: {}", f.getOriginalFilename(), f.getFileSize(),  f.getWebContentLink());
        }
    }

    @Test
    public void downloadTest() throws IOException {
        String id = "0B5l2RT_UOCXRdFlSZ3lNR0VpMW8";
        java.io.File tempFolder = testFolder.newFolder("downloads");

        File bannerTextImage = gdrive.getFileForID(id);
        gdrive.downloadToFile(bannerTextImage, tempFolder, true);
    }
}
