// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.logic;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.SecurityUtils;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Files;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;
import com.google.api.services.drive.model.Permission;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A connector for Google Drive
 */
public class GoogleDriveConnector {

  /**
   * Be sure to specify the name of your application. If the application name is {@code null} or
   * blank, the application will log a warning. Suggested format is "MyCompany-ProductName/1.0".
   */
  private static final String APPLICATION_NAME = "Terasology-GooeyDrive/1.0";

  /**
   * Global instance of the JSON factory.
   */
  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

  /**
   * Global instance of the HTTP transport.
   */
  private final HttpTransport httpTransport;

  /**
   * Global Drive API client.
   */
  private final Drive drive;

  private final Credential credential;

    public GoogleDriveConnector() throws GeneralSecurityException, IOException {
        httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        credential = authorize(httpTransport);
        drive = new Drive.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME).build();
    }

    /**
     * Uploads a file using either resumable or direct media upload.
     * @param file the file to upload
     * @param useDirectUpload true for direct media upload, false for resumable uploads
     * @return the uploaded file
     * @throws IOException if the upload fails
     */
    public File uploadFile(java.io.File file, boolean useDirectUpload) throws IOException {
        String contentType = null;
        FileContent mediaContent = new FileContent(contentType, file);
        return uploadFile(mediaContent, contentType, useDirectUpload);
    }

    /**
     * Uploads a file using either resumable or direct media upload.
     * @param contentStream the file content stream
     * @param name the filename
     * @param useDirectUpload true for direct media upload, false for resumable uploads
     * @return the uploaded file
     * @throws IOException if the upload fails
     */
    public File uploadFile(InputStream contentStream, String name, boolean useDirectUpload) throws IOException {
        String contentType = null;
        InputStreamContent content = new InputStreamContent(contentType, contentStream);
        return uploadFile(content, name, useDirectUpload);
    }

    /**
     * Set a new permission.
     *
     * @param fileId ID of the file to insert permission for.
     * @param value User or group e-mail address, domain name or {@code null} "default" type.
     * @param type The value "user", "group", "domain" or "anyone".
     * @param role The value "owner", "writer" or "reader".
     * @throws IOException if the permission could not be set
     */
    public void setPermission(String fileId, String value, String type, String role) throws IOException {
        Permission newPermission = new Permission();

        newPermission.setValue(value);
        newPermission.setType(type);
        newPermission.setRole(role);
        drive.permissions().insert(fileId, newPermission).execute();
    }

    /**
     * Downloads a file using either resumable or direct media download.
     * @param file the file to download
     * @param localFolder the local target folder
     * @param useDirectDownload true for direct media download, false for resumable downloads
     * @throws IOException if the download fails
     */
    public void downloadToFile(File file, java.io.File localFolder, boolean useDirectDownload) throws IOException {

        java.io.File localFile = new java.io.File(localFolder, file.getOriginalFilename());
        try (OutputStream out = new FileOutputStream(localFile)) {
            HttpRequestInitializer initializer = drive.getRequestFactory().getInitializer();
            MediaHttpDownloader downloader = new MediaHttpDownloader(httpTransport, initializer);
            downloader.setDirectDownloadEnabled(useDirectDownload);
            downloader.download(new GenericUrl(file.getDownloadUrl()), out);
        }
    }

    public File getFileForID(String fileId) throws IOException {

          File file = drive.files().get(fileId).execute();
          return file;
      }

    /**
     * Retrieve a list of File resources.
     * @return List of File resources.
     * @throws IOException if the directory listing fails
     */
    public List<File> retrieveAllFiles() throws IOException {
      List<File> result = new ArrayList<File>();
      Files.List request = drive.files().list();

      String token = null;

      do {
          FileList files = request.execute();
          result.addAll(files.getItems());
          token = files.getNextPageToken();
          request.setPageToken(token);
      } while (token != null && token.length() > 0);

      return result;
    }

    private static Credential authorize(HttpTransport httpTransport) throws GeneralSecurityException, IOException {

        PrivateKey serviceAccountPrivateKey = getPrivateKey();

        GoogleCredential credential = new GoogleCredential.Builder().setTransport(httpTransport)
                .setJsonFactory(JSON_FACTORY)
                .setServiceAccountId("454164381957-9fnum5600ia5bp94jgkmbrrlhicjb1vo@developer.gserviceaccount.com")
                .setServiceAccountScopes(Collections.singleton(DriveScopes.DRIVE))
                .setServiceAccountPrivateKey(serviceAccountPrivateKey)
                .build();

        return credential;
    }

    private static PrivateKey getPrivateKey() throws IOException, GeneralSecurityException {
        URL website = new URL("https://jenkins.terasology.io/cjoc/userContent/gooey-drive-371f4f7d4d08.p12");

        try (InputStream keyStream = website.openStream()) {
            return SecurityUtils.loadPrivateKeyFromKeyStore(
                    SecurityUtils.getPkcs12KeyStore(),
                    keyStream, "notasecret",
                    "privatekey", "notasecret");
        }
    }

    private File uploadFile(AbstractInputStreamContent content, String name, boolean useDirectUpload) throws IOException {
        ParentReference parent = new ParentReference();
        parent.setId("19pDHxle0sDArV40mfU9Xui2oEJnUS5qa");

        File fileMetadata = new File();
        fileMetadata.setTitle(name);
        fileMetadata.setParents(Arrays.asList(parent));

        Drive.Files.Insert insert = drive.files().insert(fileMetadata, content);
        MediaHttpUploader uploader = insert.getMediaHttpUploader();
        uploader.setDirectUploadEnabled(useDirectUpload);
        insert.set("supportsAllDrives", "true");
        return insert.execute();
    }
}
