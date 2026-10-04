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
        // Client ID of a GitHub App (not an OAuth App) with Device Flow enabled and Issues write
        // permission. Unset by default - see GitHubDeviceLogin's javadoc for why a GitHub App.
        // When unset, the "submit directly" button is hidden.
        REPORT_ISSUE_OAUTH_CLIENT_ID,
        // Field IDs of the issue form named by REPORT_ISSUE_TEMPLATE, one per thing CrashSummary
        // can pre-fill. Unset means that field is left for the user. Form-specific, so these live
        // with the template in the downstream app's properties, never in cr-core's defaults.
        REPORT_ISSUE_FIELD_VERSION,
        REPORT_ISSUE_FIELD_OS,
        REPORT_ISSUE_FIELD_JAVA,
        REPORT_ISSUE_FIELD_DETAILS,
        REPORT_ISSUE_FIELD_LOG,
        REPORT_ISSUE_FIELD_EXTRA,

        // What CrashSummary knows about the hosting application's logs. The regexes each capture
        // one group from the combined log text; unset means that line is not extracted or shown.
        // Log formats are app-specific and not a published API, which is why they are configured
        // by the app (cr-terasology, cr-destsol) rather than hardcoded in cr-core.
        CRASH_SUMMARY_PRODUCT_NAME,
        CRASH_SUMMARY_VERSION_PATTERN,
        CRASH_SUMMARY_DISPLAY_VERSION_PATTERN,
        CRASH_SUMMARY_MODULE_PATTERN,

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
