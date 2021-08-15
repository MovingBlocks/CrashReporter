// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.FormBodyPartBuilder;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

public class HostedUploadRunnable implements Callable<URL> {
    private final String content;
    private final URI postUri;

    public HostedUploadRunnable(URI postUri, String content) {
        this.postUri = postUri;
        this.content = content;
    }

    @Override
    public URL call() throws IOException {
        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {

            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            HttpPost post = new HttpPost(postUri);
            ByteArrayBody body = new ByteArrayBody(data, "terasology.log");
            FormBodyPart bodyPart = FormBodyPartBuilder.create().setBody(body).setName("logFile").build();
            HttpEntity entity = MultipartEntityBuilder.create().addPart(bodyPart).build();
            post.setEntity(entity);
            try (CloseableHttpResponse response = client.execute(post)) {
                int code = response.getStatusLine().getStatusCode();
                String responseText = EntityUtils.toString(response.getEntity(), "UTF-8");
                if (code != HttpStatus.SC_OK) {
//                    System.err.println("ERROR: HTTP status code: " + code);
//                    System.err.println(responseText);
//                    throw new IOException("HTTP status code: " + code);
                    throw new IOException(responseText);
                }
                return new URL(responseText);
            }
        }
    }
}
