// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import com.google.api.services.drive.model.File;
import org.terasology.crashreporter.logic.GoogleDriveConnector;

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
