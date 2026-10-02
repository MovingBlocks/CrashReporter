// Copyright 2026 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.crashreporter.pages;

import com.google.common.base.Strings;
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

    @Test
    void bodyBuildStaysUnderGitHubsUrlByteLimit() {
        // GitHub rejects the whole thing past 8191 bytes. Must truncate.
        String hugeBody = Strings.repeat("x", 50_000);

        String link = GitHubIssueLinkBuilder.build("https://github.com/MovingBlocks/Terasology/issues/new",
                "Crash: NullPointerException", hugeBody);

        assertTrue(link.length() < 8191, "link was " + link.length() + " bytes: " + link);
        assertTrue(link.contains("truncated"), link);
    }

    @Test
    void formBuildStaysUnderGitHubsUrlByteLimit() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("terasology_version", "5.4.0-SNAPSHOT");
        fields.put("operating_system", "Mac OS X");
        fields.put("actual_behavior", Strings.repeat("x", 50_000));
        fields.put("additional_context", Strings.repeat("y", 50_000));

        String link = GitHubIssueLinkBuilder.build("https://github.com/MovingBlocks/Terasology/issues/new",
                "crash-bug-report.yml", "Crash: NullPointerException", fields);

        assertTrue(link.length() < 8191, "link was " + link.length() + " bytes: " + link);
        // Early fields stay full, only the overflowing tail gets trimmed.
        assertTrue(link.contains("terasology_version=5.4.0-SNAPSHOT"), link);
        assertTrue(link.contains("operating_system=Mac+OS+X"), link);
    }

    @Test
    void fitToBudgetKeepsTheLongestPrefixThatFits() {
        // 10 'x' encode to 10 bytes; the suffix is fixed. The result must land exactly on the budget,
        // not one short of it - the old drop-one-char loop and a binary search can disagree here.
        String suffix = "%0A...+truncated%2C+see+the+full+log";
        int budget = 10 + suffix.length();

        String fitted = GitHubIssueLinkBuilder.fitToBudget(Strings.repeat("x", 100), budget);

        assertEquals("xxxxxxxxxx" + suffix, fitted);
        assertEquals(budget, fitted.length());
    }

    @Test
    void fitToBudgetReturnsNullWhenNotEvenTheSuffixFits() {
        assertNull(GitHubIssueLinkBuilder.fitToBudget(Strings.repeat("x", 100), 5));
    }

    @Test
    void truncationNeverSplitsASurrogatePair() {
        // U+1F600 is one code point but two Java chars; cutting between them yields a lone high
        // surrogate, which URLEncoder renders as "%3F" ('?') - a mangled trailing character.
        String emoji = new String(Character.toChars(0x1F600));
        String value = Strings.repeat(emoji, 2_000);

        String fitted = GitHubIssueLinkBuilder.fitToBudget(value, 1_000);

        assertFalse(fitted.contains("%3F"), "lone surrogate leaked as '?': " + fitted);
        assertTrue(fitted.length() <= 1_000, "over budget: " + fitted.length());
    }

    @Test
    void formBuildKeepsSmallLaterFieldsWhenAnEarlierFieldIsOversized() {
        // The full-log link is the one field a truncated trace points at, and it comes after the
        // trace in CrashSummary's field order. A greedy allocation dropped it.
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("actual_behavior", Strings.repeat("x", 50_000));
        fields.put("log_details", "[PasteBin](https://pastebin.com/abc123)");
        fields.put("additional_context", "Active modules:\n- engine:5.4.0");

        String link = GitHubIssueLinkBuilder.build("https://example.com/issues/new", "t.yml", "t", fields);

        assertTrue(link.contains("log_details=%5BPasteBin%5D%28https%3A%2F%2Fpastebin.com%2Fabc123%29"), link);
        assertTrue(link.contains("additional_context="), link);
        assertTrue(link.contains("actual_behavior=xxx"), link);
        assertTrue(link.length() < 8191, "link was " + link.length() + " bytes");
        // Output order is still the map's order, so the form reads the way CrashSummary wrote it.
        assertTrue(link.indexOf("actual_behavior=") < link.indexOf("log_details="), link);
    }

    @Test
    void truncationNeverSplitsAPercentEscape() {
        Map<String, String> fields = new LinkedHashMap<>();
        // Every char is a 3-byte "%XX" escape, so a mid-escape cut would hide here.
        fields.put("actual_behavior", Strings.repeat("&", 50_000));

        String link = GitHubIssueLinkBuilder.build("https://example.com/issues/new", "t.yml", "t", fields);

        int valueStart = link.indexOf("actual_behavior=") + "actual_behavior=".length();
        String value = link.substring(valueStart);
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '%') {
                assertTrue(i + 2 < value.length(), "truncated escape at end of value: " + value);
            }
        }
    }
}
