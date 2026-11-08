package com.altf4.hush.data

import com.altf4.hush.dns.ListCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {
    private val sites = SocialSites.all.map { it.id }

    private fun sample(): SettingsSnapshot = SettingsSnapshot(
        theme = ThemeMode.Light,
        nightEnabled = true,
        nightStart = 23 * 60,
        nightEnd = 6 * 60 + 30,
        nightDays = 0b0011111,
        strict = true,
        apps = setOf("com.example.social", "org.example.video_player"),
        social = sites.associateWith { SocialMode.Off } +
            mapOf(sites[0] to SocialMode.Always, sites[1] to SocialMode.Night),
        custom = setOf("example.com", "news.example.org"),
        allowed = setOf("bank.example"),
        regexBlock = setOf("^ads[0-9]+\\."),
        regexAllow = setOf("^cdn\\.example\\.com$"),
        lists = setOf(ListCatalog.all.first().id),
        uncloak = true,
        rebinding = false,
        bypass = true,
        weekly = true,
    )

    private fun decoded(text: String): BackupResult = SettingsBackup.decode(text)

    private fun refused(text: String): Boolean = decoded(text) is BackupResult.Refused

    /** The encoded sample with every line starting with [key] replaced by [replacement] lines. */
    private fun edited(key: String, vararg replacement: String): String {
        val kept = SettingsBackup.encode(sample()).lines().filter { !it.startsWith("$key\t") }
        return (kept + replacement).joinToString("\n")
    }

    private fun withExtra(line: String): String = SettingsBackup.encode(sample()) + line + "\n"

    @Test fun roundTripKeepsEverything() {
        val result = decoded(SettingsBackup.encode(sample()))
        assertTrue(result is BackupResult.Ok)
        assertEquals(sample(), (result as BackupResult.Ok).snapshot)
    }

    @Test fun emptySetsRoundTrip() {
        val empty = sample().copy(
            apps = emptySet(), custom = emptySet(), allowed = emptySet(),
            regexBlock = emptySet(), regexAllow = emptySet(), lists = emptySet(),
            social = sites.associateWith { SocialMode.Off },
        )
        val result = decoded(SettingsBackup.encode(empty)) as BackupResult.Ok
        assertEquals(empty, result.snapshot)
    }

    @Test fun windowsLineEndingsAndBlankLinesAreFine() {
        val text = SettingsBackup.encode(sample()).replace("\n", "\r\n\r\n")
        assertTrue(decoded(text) is BackupResult.Ok)
    }

    @Test fun presetsNotMentionedStayOff() {
        val text = edited("social", "social\t${sites[0]}:always")
        val snapshot = (decoded(text) as BackupResult.Ok).snapshot
        assertEquals(SocialMode.Always, snapshot.social[sites[0]])
        assertEquals(SocialMode.Off, snapshot.social[sites[1]])
        assertEquals(sites.size, snapshot.social.size)
    }

    @Test fun wrongOrMissingHeaderIsRefused() {
        assertTrue(refused(""))
        assertTrue(refused("hello"))
        assertTrue(refused(SettingsBackup.encode(sample()).replace("hush-settings 1", "hush-settings 2")))
        assertTrue(refused(SettingsBackup.encode(sample()).lines().drop(1).joinToString("\n")))
    }

    @Test fun oversizedFileIsRefused() {
        val big = SettingsBackup.encode(sample()) + "x".repeat(SettingsBackup.MAX_CHARS)
        assertTrue(refused(big))
    }

    @Test fun incompleteFileIsRefused() {
        for (key in listOf("theme", "night_enabled", "night_start", "night_end", "night_days", "strict", "weekly")) {
            assertTrue("missing $key", refused(edited(key)))
        }
    }

    @Test fun repeatedScalarIsRefused() {
        assertTrue(refused(withExtra("strict\t0")))
    }

    @Test fun unknownKeyIsRefused() {
        assertTrue(refused(withExtra("shell\trm -rf")))
    }

    @Test fun badNumbersAndSwitchesAreRefused() {
        assertTrue(refused(edited("night_start", "night_start\t1440")))
        assertTrue(refused(edited("night_start", "night_start\t-1")))
        assertTrue(refused(edited("night_end", "night_end\tabc")))
        assertTrue(refused(edited("night_days", "night_days\t128")))
        assertTrue(refused(edited("strict", "strict\t2")))
        assertTrue(refused(edited("theme", "theme\tneon")))
    }

    @Test fun badAppNamesAreRefused() {
        for (bad in listOf("notapackage", "../etc/passwd", "com.example app", "1com.example", "a..b")) {
            assertTrue("app $bad", refused(withExtra("app\t$bad")))
        }
    }

    @Test fun sitesMustAlreadyBeCleanDomains() {
        for (bad in listOf("https://example.com/path", "Example.COM", "www.example.com", "localhost", "a b.com")) {
            assertTrue("custom $bad", refused(withExtra("custom\t$bad")))
            assertTrue("allowed $bad", refused(withExtra("allowed\t$bad")))
        }
    }

    @Test fun dangerousOrBrokenPatternsAreRefused() {
        assertTrue(refused(withExtra("regex_block\t(a+)+$")))
        assertTrue(refused(withExtra("regex_block\t(a)\\1")))
        assertTrue(refused(withExtra("regex_allow\t[unclosed")))
        assertTrue(refused(withExtra("regex_allow\t" + "a".repeat(500))))
    }

    @Test fun unknownChoicesAreRefused() {
        assertTrue(refused(withExtra("social\tnosuchsite:always")))
        assertTrue(refused(withExtra("social\t${sites[2]}:sometimes")))
        assertTrue(refused(withExtra("list\tnosuchlist")))
        // The sample already has a choice for sites[0], so a second one is a duplicate.
        assertTrue(refused(withExtra("social\t${sites[0]}:off")))
    }

    @Test fun valuesWithTabsOrNoValueAreRefused() {
        assertTrue(refused(withExtra("app\tcom.example\textra")))
        assertTrue(refused(withExtra("app\t")))
        assertTrue(refused(withExtra("justtext")))
        assertTrue(refused(withExtra("\tvalue")))
    }

    @Test fun tooManyEntriesAreRefused() {
        val apps = (0..SettingsBackup.MAX_APPS).joinToString("") { "app\tcom.example.a$it\n" }
        assertTrue(refused(SettingsBackup.encode(sample().copy(apps = emptySet())) + apps))
        val patterns = (0..com.altf4.hush.dns.SafeRegex.MAX_RULES).joinToString("") { "regex_block\t^a$it\\.\n" }
        assertTrue(refused(SettingsBackup.encode(sample().copy(regexBlock = emptySet())) + patterns))
    }

    @Test fun encodeSkipsValuesThatCannotBeWrittenSafely() {
        val text = SettingsBackup.encode(sample().copy(regexBlock = setOf("a\tb", "^ok\\.")))
        val snapshot = (decoded(text) as BackupResult.Ok).snapshot
        assertEquals(setOf("^ok\\."), snapshot.regexBlock)
    }

    @Test fun fileHoldsNoCountersOrListContents() {
        val text = SettingsBackup.encode(sample())
        val keys = text.lines().drop(1).filter { it.isNotEmpty() }.map { it.substringBefore('\t') }.toSet()
        val allowedKeys = setOf(
            "theme", "night_enabled", "night_start", "night_end", "night_days", "strict", "uncloak",
            "rebinding", "bypass", "weekly", "social", "list", "app", "custom", "allowed",
            "regex_block", "regex_allow",
        )
        assertTrue(allowedKeys.containsAll(keys))
    }
}
