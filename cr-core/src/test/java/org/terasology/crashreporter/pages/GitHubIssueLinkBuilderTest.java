// Copyright 2026 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubIssueLinkBuilderTest {

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
}
