package dev.jonbarrow.officialserverlist

import net.minecraft.resources.Identifier

object SocialIcons {
	private const val NAMESPACE = "official-server-list"

	fun textureFor(socialMedia: String?): Identifier? {
		val filename = when (SocialMedia.fromString(socialMedia)) {
			SocialMedia.EMAIL -> "email"
			SocialMedia.WEBSITE -> "website"
			SocialMedia.STORE -> "store"
			SocialMedia.PATREON -> "patreon"
			SocialMedia.DISCORD -> "discord"
			SocialMedia.FACEBOOK -> "facebook"
			SocialMedia.INSTAGRAM -> "instagram"
			SocialMedia.TWITTER -> "twitter"
			SocialMedia.TWITCH -> "twitch"
			SocialMedia.YOUTUBE -> "youtube"
			SocialMedia.TIKTOK -> "tiktok"
			SocialMedia.TEAMSPEAK -> "teamspeak"
			SocialMedia.BLUESKY -> "bluesky"
			null -> return null
		}

		return Identifier.fromNamespaceAndPath(NAMESPACE, "textures/gui/socials/$filename.png")
	}
}