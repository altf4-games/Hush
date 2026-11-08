package com.altf4.hush.data

/** How a preset is applied. Night-only presets are evaluated against the quiet window. */
enum class SocialMode(val key: String) {
    Off("off"),
    Always("always"),
    Night("night");

    companion object {
        fun fromKey(key: String?): SocialMode = entries.firstOrNull { it.key == key } ?: Off
    }
}

/** A named group of domains (the site plus its CDN/API hosts). Matched by suffix. */
data class SocialSite(val id: String, val name: String, val domains: List<String>)

/**
 * Built-in presets for common social sites. Static data: nothing here is fetched or logged.
 * DNS blocking is best-effort for apps that already hold open connections or cached addresses.
 */
object SocialSites {
    val all: List<SocialSite> = listOf(
        SocialSite(
            "instagram", "Instagram",
            listOf("instagram.com", "cdninstagram.com", "instagr.am"),
        ),
        SocialSite(
            "facebook", "Facebook",
            listOf("facebook.com", "fbcdn.net", "facebook.net", "fb.com", "fb.me", "messenger.com"),
        ),
        SocialSite("threads", "Threads", listOf("threads.net")),
        SocialSite(
            "tiktok", "TikTok",
            listOf(
                "tiktok.com", "tiktokv.com", "tiktokcdn.com", "tiktokcdn-us.com",
                "byteoversea.com", "ibytedtos.com", "musical.ly",
            ),
        ),
        SocialSite("x", "X (Twitter)", listOf("x.com", "twitter.com", "twimg.com", "t.co")),
        SocialSite(
            "reddit", "Reddit",
            listOf("reddit.com", "redd.it", "redditmedia.com", "redditstatic.com"),
        ),
        SocialSite(
            "youtube", "YouTube",
            listOf("youtube.com", "youtu.be", "ytimg.com", "googlevideo.com", "youtube-nocookie.com"),
        ),
        SocialSite("snapchat", "Snapchat", listOf("snapchat.com", "sc-cdn.net", "snapkit.com")),
        SocialSite("pinterest", "Pinterest", listOf("pinterest.com", "pinimg.com")),
        SocialSite("linkedin", "LinkedIn", listOf("linkedin.com", "licdn.com")),
        SocialSite(
            "twitch", "Twitch",
            listOf("twitch.tv", "ttvnw.net", "jtvnw.net", "twitchcdn.net"),
        ),
        SocialSite(
            "discord", "Discord",
            listOf("discord.com", "discordapp.com", "discordapp.net", "discord.gg", "discord.media"),
        ),
    )
}
