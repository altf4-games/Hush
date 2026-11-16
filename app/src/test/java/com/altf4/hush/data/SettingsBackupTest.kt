package com.altf4.hush.data

import com.altf4.hush.dns.DnsProvider
import com.altf4.hush.dns.ListCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {
    private val sites = SocialSites.all.map { it.id }

    private fun sample(): SettingsSnapshot = SettingsSnapshot(
        theme = ThemeMode.Light,
        social = sites.associateWith { SocialMode.Off } +
            mapOf(sites[0] to SocialMode.Always),
        custom = setOf("example.com", "news.example.org"),
        allowed = setOf("bank.example"),
        regexBlock = setOf("^ads[0-9]+\\."),
        regexAllow = setOf("^cdn\\.example\\.com$"),
        lists = setOf(ListCatalog.all.first().id),
        uncloak = true,
        rebinding = false,
        bypass = true,
        weekly = true,
        dns = DnsProvider.Cloudflare,
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
            custom = emptySet(), allowed = emptySet(),
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
        for (key in listOf("theme", "uncloak", "rebinding", "bypass", "weekly")) {
            assertTrue("missing $key", refused(edited(key)))
        }
    }

    @Test fun everyDnsServerRoundTrips() {
        for (p in DnsProvider.entries) {
            val result = decoded(SettingsBackup.encode(sample().copy(dns = p))) as BackupResult.Ok
            assertEquals(p, result.snapshot.dns)
        }
    }

    @Test fun olderFileWithoutDnsServerUsesTheDefault() {
        val snapshot = (decoded(edited("dns")) as BackupResult.Ok).snapshot
        assertEquals(DnsProvider.DEFAULT, snapshot.dns)
    }

    @Test fun unknownOrRepeatedDnsServerIsRefused() {
        assertTrue(refused(edited("dns", "dns\texample")))
        assertTrue(refused(withExtra("dns\tgoogle")))
    }

    @Test fun repeatedScalarIsRefused() {
        assertTrue(refused(withExtra("weekly\t0")))
    }

    @Test fun unknownKeyIsRefused() {
        assertTrue(refused(withExtra("shell\trm -rf")))
    }

    @Test fun badNumbersAndSwitchesAreRefused() {
        assertTrue(refused(edited("weekly", "weekly\t2")))
        assertTrue(refused(edited("bypass", "bypass\tyes")))
        assertTrue(refused(edited("theme", "theme\tneon")))
    }

    @Test fun filesFromVersionsWithQuietHoursStillLoad() {
        val old = edited("social", "social\t${sites[0]}:always", "social\t${sites[1]}:night") +
            "\nnight_enabled\t1\nnight_start\t1380\nnight_end\t390\nnight_days\t31\nstrict\t1\n" +
            "app\tcom.example.social\napp\tcom.example.video\n"
        val snapshot = (decoded(old) as BackupResult.Ok).snapshot
        assertEquals(SocialMode.Always, snapshot.social[sites[0]])
        assertEquals(SocialMode.Off, snapshot.social[sites[1]])
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
        assertTrue(refused(withExtra("custom\texample.com\textra")))
        assertTrue(refused(withExtra("custom\t")))
        assertTrue(refused(withExtra("justtext")))
        assertTrue(refused(withExtra("\tvalue")))
    }

    @Test fun tooManyEntriesAreRefused() {
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
            "theme", "uncloak", "rebinding", "bypass", "weekly", "dns", "social", "list", "custom",
            "allowed", "regex_block", "regex_allow",
        )
        assertTrue(allowedKeys.containsAll(keys))
    }
}
