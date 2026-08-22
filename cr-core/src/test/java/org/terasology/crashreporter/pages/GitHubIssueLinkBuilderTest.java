// Copyright 2026 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubIssueLinkBuilderTest {

    @Test
    void returnsNullWhenBaseUrlIsNotConfigured() {
        // REPORT_ISSUE_LINK is only set by downstream apps (cr-terasology, cr-destsol, ...), not by
        // cr-core's own defaults - build() must not silently produce a broken "null?title=..." link.
        assertNull(GitHubIssueLinkBuilder.build(null, "t", "b"));
        assertNull(GitHubIssueLinkBuilder.build(null, "template.yml", "t", new LinkedHashMap<>()));
    }

    @Test
    void appendsTitleAndBodyAsQueryParameters() {
        String link = GitHubIssueLinkBuilder.build("https://github.com/MovingBlocks/Terasology/issues/new",
                "Crash: NullPointerException", "some body text");

        assertTrue(link.startsWith("https://github.com/MovingBlocks/Terasology/issues/new?"));
        assertTrue(link.contains("title=Crash%3A+NullPointerException"), link);
        assertTrue(link.contains("body=some+body+text"), link);
    }

    @Test
    void encodesSpecialCharactersInTitleAndBody() {
        String link = GitHubIssueLinkBuilder.build("https://example.com/issues/new", "a & b", "line1\nline2");

        assertTrue(link.contains("title=a+%26+b"), link);
        assertTrue(link.contains("body=line1%0Aline2"), link);
    }

    @Test
    void usesAmpersandWhenBaseUrlAlreadyHasAQueryString() {
        String link = GitHubIssueLinkBuilder.build("https://example.com/issues/new?template=bug", "t", "b");

        assertEquals("https://example.com/issues/new?template=bug&title=t&body=b", link);
    }

    @Test
    void formBuildAppendsTemplateTitleAndEachFieldAsItsOwnQueryParameter() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("terasology_version", "5.4.0-SNAPSHOT");
        fields.put("operating_system", "Mac OS X");

        String link = GitHubIssueLinkBuilder.build("https://github.com/MovingBlocks/Terasology/issues/new",
                "crash-bug-report.yml", "Crash: NullPointerException", fields);

        assertTrue(link.contains("template=crash-bug-report.yml"), link);
        assertTrue(link.contains("title=Crash%3A+NullPointerException"), link);
        assertTrue(link.contains("terasology_version=5.4.0-SNAPSHOT"), link);
        assertTrue(link.contains("operating_system=Mac+OS+X"), link);
    }

    @Test
    void formBuildOmitsFieldsWithNoValueInsteadOfPreFillingThemBlank() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("terasology_version", "");
        fields.put("java_version", null);
        fields.put("operating_system", "Linux");

        String link = GitHubIssueLinkBuilder.build("https://example.com/issues/new", "crash-bug-report.yml", "t", fields);

        assertFalse(link.contains("terasology_version="), link);
        assertFalse(link.contains("java_version="), link);
        assertTrue(link.contains("operating_system=Linux"), link);
    }
}
