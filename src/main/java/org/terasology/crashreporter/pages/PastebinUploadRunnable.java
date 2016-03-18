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

import org.jpaste.exceptions.PasteException;
import org.jpaste.pastebin.PasteExpireDate;
import org.jpaste.pastebin.Pastebin;
import org.jpaste.pastebin.PastebinLink;
import org.jpaste.pastebin.PastebinPaste;

import java.net.URL;
import java.util.concurrent.Callable;

/**
 * Upload the content to PasteBin
 */
public class PastebinUploadRunnable implements Callable<URL> {

    /**
     * Username Terasology
     * eMail pastebin@terasology.org
     */
    private static final String PASTEBIN_DEVELOPER_KEY = "1ed92217030bd6c2570fac91bcbfee78";

    private final String content;

    public PastebinUploadRunnable(String content) {
        this.content = content;
    }

    @Override
    public URL call() throws PasteException {
        String title = "Terasology Error Report";
        final PastebinPaste paste = Pastebin.newPaste(PASTEBIN_DEVELOPER_KEY, content, title);
        paste.setPasteFormat("apache"); // Apache Log File Format - this is the closest I could find
        paste.setPasteExpireDate(PasteExpireDate.ONE_MONTH);

        PastebinLink link = paste.paste();
        final URL url = link.getLink();
        return url;
    }
}
