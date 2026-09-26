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
        REPORT_ISSUE_TEMPLATE,
        // GitHub OAuth App client ID with Device Flow enabled. Unset by default - see
        // GitHubDeviceLogin's javadoc. When unset, the "submit directly" button is hidden.
        REPORT_ISSUE_OAUTH_CLIENT_ID,

        RES_BANNER_IMAGE,
        RES_SERVER_ICON,
        RES_ARROW_PREV,
        RES_ARROW_NEXT,
        RES_EXIT_ICON,
        RES_ERROR_TITLE_IMAGE,
        RES_INFO_TITLE_IMAGE,
        RES_PASTEBIN_ICON,
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
        loadIfPresent(defaultPropsUrl);
        // Only cr-core's downstream consumers (cr-terasology, cr-destsol, ...) ship this file -
        // it's absent when cr-core is used standalone, which getResourceAsStream signals with
        // null rather than an IOException, so that has to be checked explicitly.
        loadIfPresent(propsUrl);
    }

    private void loadIfPresent(String resourceUrl) {
        try (InputStream stream = CrashReporter.class.getResourceAsStream(resourceUrl)) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException e) {
            System.err.println("Unable to load " + resourceUrl);
        }
    }

    public String get(KEY key) {
        return properties.getProperty(key.name());
    }
}
