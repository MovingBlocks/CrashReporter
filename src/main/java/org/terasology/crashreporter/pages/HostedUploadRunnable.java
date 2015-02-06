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

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

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

/**
 * @author Martin Steiger
 *
 */
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