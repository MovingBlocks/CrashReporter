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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Collections;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Files.List;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;

/**
 * A sample application that runs multiple requests against the Drive API. The requests this sample
 * makes are:
 * <ul>
 * <li>Does a resumable media upload</li>
 * <li>Updates the uploaded file by renaming it</li>
 * <li>Does a resumable media download</li>
 * <li>Does a direct media upload</li>
 * <li>Does a direct media download</li>
 * </ul>
 *
 * @author rmistry@google.com (Ravi Mistry)
 */
public class DriveSample {

  /**
   * Be sure to specify the name of your application. If the application name is {@code null} or
   * blank, the application will log a warning. Suggested format is "MyCompany-ProductName/1.0".
   */
  private static final String APPLICATION_NAME = "Terasology-GooeyDrive/1.0";

  private static final String UPLOAD_FILE_PATH = "banner-text.png";

  /** Global instance of the HTTP transport. */
  private static HttpTransport httpTransport;

  /** Global instance of the JSON factory. */
  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

  /** Global Drive API client. */
  private static Drive drive;

//  private key secret: notasecret

  private static Credential authorize() throws GeneralSecurityException, IOException, URISyntaxException {
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        java.io.File keyFile = getPrivateKey();

        GoogleCredential credential = new GoogleCredential.Builder().setTransport(httpTransport)
                .setJsonFactory(jsonFactory)
//                .setServiceAccountId("255915530003-n0lls505djp8n5v7bs34qvoibpfhvv63@developer.gserviceaccount.com")
                .setServiceAccountId("454164381957-9fnum5600ia5bp94jgkmbrrlhicjb1vo@developer.gserviceaccount.com")
                .setServiceAccountScopes(Collections.singleton(DriveScopes.DRIVE))
                .setServiceAccountPrivateKeyFromP12File(keyFile)
                .build();
        return credential;
  }

    /**
    * @return
    * @throws URISyntaxException
    */
    private static java.io.File getPrivateKey() throws URISyntaxException {
        //    URL website = new URL("http://www.website.com/information.asp");
        //    ReadableByteChannel rbc = Channels.newChannel(website.openStream());
        //    try (FileOutputStream fos = new FileOutputStream("information.html")) {
        //        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        //    }
        URL resource = DriveSample.class.getResource("/GooeyDrive-24506b2f1f34.p12");
        java.io.File keyFile = new java.io.File(resource.toURI());

        return keyFile;
    }

    public static void main(String[] args) {
        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            Credential credential = authorize();
            System.out.println("Refresh: " + credential.refreshToken());
            drive = new Drive.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();

            // run commands

            View.header1("Starting Resumable Media Upload");
            File uploadedFile = uploadFile(false);
            insertPermission(drive, uploadedFile.getId(), null, "anyone", "reader");
            System.out.println(uploadedFile.getWebContentLink());

            View.header1("Updating Uploaded File Name");
            File updatedFile = updateFileWithTestSuffix(uploadedFile.getId());
            System.out.println(updatedFile.getWebContentLink());

            View.header1("Starting Resumable Media Download");
            downloadFile(false, updatedFile);

            View.header1("Starting Simple Media Upload");
            uploadedFile = uploadFile(true);

            View.header1("Listing all files");
            List files = drive.files().list();
            System.out.println(files);

            View.header1("Starting Simple Media Download");
            downloadFile(true, uploadedFile);

            View.header1("Success!");
            return;
        } catch (IOException e) {
            System.err.println(e.getMessage());
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * Uploads a file using either resumable or direct media upload.
     * @throws URISyntaxException
     */
    private static File uploadFile(boolean useDirectUpload) throws IOException, URISyntaxException {
        File fileMetadata = new File();
        fileMetadata.setTitle(UPLOAD_FILE_PATH);

        java.io.File image = new java.io.File(DriveSample.class.getResource("/" + UPLOAD_FILE_PATH).toURI());
        FileContent mediaContent = new FileContent("image/png", image);

        Drive.Files.Insert insert = drive.files().insert(fileMetadata, mediaContent);
        MediaHttpUploader uploader = insert.getMediaHttpUploader();
        uploader.setDirectUploadEnabled(useDirectUpload);
        uploader.setProgressListener(new FileUploadProgressListener());
        return insert.execute();
    }

    /**
     * Insert a new permission.
     *
     * @param service Drive API service instance.
     * @param fileId ID of the file to insert permission for.
     * @param value User or group e-mail address, domain name or {@code null}
                    "default" type.
     * @param type The value "user", "group", "domain" or "anyone".
     * @param role The value "owner", "writer" or "reader".
     * @return The inserted permission if successful, {@code null} otherwise.
     */
    private static Permission insertPermission(Drive service, String fileId, String value, String type, String role) {
        Permission newPermission = new Permission();

        newPermission.setValue(value);
        newPermission.setType(type);
        newPermission.setRole(role);
        try {
            return service.permissions().insert(fileId, newPermission).execute();
        } catch (IOException e) {
            System.out.println("An error occurred: " + e);
        }
        return null;
    }

    /**
     * Updates the name of the uploaded file to have a "drivetest-" prefix.
     */
    private static File updateFileWithTestSuffix(String id) throws IOException {
        File fileMetadata = new File();
        fileMetadata.setTitle("drivetest-" + UPLOAD_FILE_PATH);

        Drive.Files.Update update = drive.files().update(id, fileMetadata);
        return update.execute();
    }

    /**
     * Downloads a file using either resumable or direct media download.
     */
    private static void downloadFile(boolean useDirectDownload, File uploadedFile) throws IOException {
        // create parent directory (if necessary)

        java.io.File localFile = new java.io.File(uploadedFile.getTitle());
        try (OutputStream out = new FileOutputStream(localFile)) {
            HttpRequestInitializer initializer = drive.getRequestFactory().getInitializer();
            MediaHttpDownloader downloader = new MediaHttpDownloader(httpTransport, initializer);
            downloader.setDirectDownloadEnabled(useDirectDownload);
            downloader.setProgressListener(new FileDownloadProgressListener());
            downloader.download(new GenericUrl(uploadedFile.getDownloadUrl()), out);
            System.out.println("Downloaded file: " + localFile.getCanonicalPath());
        }
    }
}
