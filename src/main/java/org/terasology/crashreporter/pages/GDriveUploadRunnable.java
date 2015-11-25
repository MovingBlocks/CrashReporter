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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Callable;

import org.terasology.crashreporter.logic.GoogleDriveConnector;

import com.google.api.services.drive.model.File;

/**
 * Upload the content to PasteBin
 */
public class GDriveUploadRunnable implements Callable<URL> {

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss.SSS");

    private final String content;

    public GDriveUploadRunnable(String content) {
        this.content = content;
    }

    @Override
    public URL call() throws IOException {
        try {
            GoogleDriveConnector gdrive = new GoogleDriveConnector();
            String name = "terasology_" + DATE_FORMAT.format(new Date()) + ".log";

            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            try (InputStream stream = new ByteArrayInputStream(data)) {
                File uploadedFile = gdrive.uploadFile(stream, name, true);
                gdrive.setPermission(uploadedFile.getId(), null, "anyone", "reader");
                return new URL(uploadedFile.getWebContentLink());
            }
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

}
