// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * A service that provides access to properties that are specified in external files.
 */
public final class GlobalProperties {

    private final Properties properties = new Properties();

    public enum KEY {
        SUPPORT_FORUM_LINK,
        JOIN_DISCORD_LINK,
        REPORT_ISSUE_LINK,

        RES_BANNER_IMAGE,
        RES_SERVER_ICON,
        RES_ARROW_PREV,
        RES_ARROW_NEXT,
        RES_EXIT_ICON,
        RES_ERROR_TITLE_IMAGE,
        RES_INFO_TITLE_IMAGE,
        RES_PASTEBIN_ICON,
        RES_GDRIVE_ICON,
        RES_SKIP_UPLOAD_ICON,
        RES_COPY_ICON,
        RES_FINAL_TITLE_IMAGE,
        RES_UPLOAD_TITLE_IMAGE,
        RES_GITHUB_ICON,
        RES_FORUM_ICON,
        RES_DISCORD_ICON
    }

    public GlobalProperties() {
        String propsUrl = "/crashreporter.properties";
        String defaultPropsUrl = "/crashreporter_defaults.properties";
        try (InputStream stream = CrashReporter.class.getResourceAsStream(defaultPropsUrl)) {
            properties.load(stream);
        } catch (IOException e) {
            // this should never go wrong
            System.err.println("Unable to load default properties");
        }
        try (InputStream stream = CrashReporter.class.getResourceAsStream(propsUrl)) {
            properties.load(stream);
        } catch (IOException e) {
            System.err.println("Unable to load " + propsUrl);
        }
    }

    public String get(KEY key) {
        return properties.getProperty(key.name());
    }
}
