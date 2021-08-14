// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

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
