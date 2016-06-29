/*
 * Copyright 2015 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
