package dev.jonbarrow.officialserverlist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

typealias Unknown = JsonElement

@Serializable
enum class TrackingEventType {
	@SerialName("view-page-home") VIEW_PAGE_HOME, // * Triggered when visiting the home page
	@SerialName("click-discover-server") CLICK_DISCOVER_SERVER, // * Triggered when visiting "/servers/{SLUG}". Takes in a server slug in the tracking call
	@SerialName("copy-ip") COPY_IP, // * Triggered when copying a server's IP. Takes in a server slug in the tracking call
	@SerialName("view-page-servers-search") VIEW_PAGE_SERVERS_SEARCH, // * Triggered when viewing server search results
	@SerialName("open-add-your-server-page") OPEN_ADD_YOUR_SERVER_PAGE, // * Triggered when visiting "/add-your-server"
	@SerialName("open-contact-page") OPEN_CONTACT_PAGE, // * Triggered when visiting "/contact"
	@SerialName("search-servers") SEARCH_SERVERS, // * Triggered when searching for servers
	@SerialName("open-badges-explained-page") OPEN_BADGES_EXPLAINED_PAGE, // * Triggered when visiting "/badges-explained"
	@SerialName("open-for-parents-page") OPEN_FOR_PARENTS_PAGE, // * Triggered when visiting "/parents"
	@SerialName("open-admin-panel") OPEN_ADMIN_PANEL, // * Triggered when visiting "/admin-panel". Same as open-add-your-server-page?
	@SerialName("home-alert-all-ages-search") HOME_ALERT_ALL_AGES_SEARCH,
	@SerialName("open-home-quick-search") OPEN_HOME_QUICK_SEARCH,
	@SerialName("open-for-events-page") OPEN_FOR_EVENTS_PAGE, // * Triggered when visiting "/event/{SLUG}". Takes in a server slug in the tracking call
	@SerialName("open-events-calendar-page") OPEN_EVENTS_CALENDAR_PAGE, // * Triggered when visiting "/events"
	@SerialName("open-site-rules") OPEN_SITE_RULES, // * Triggered when visiting "/rules"
	@SerialName("browse-all-servers") BROWSE_ALL_SERVERS, // * Triggered when visiting "/servers"
	@SerialName("login-started") LOGIN_STARTED, // * Triggered when starting the login flow
	@SerialName("open-your-profile") OPEN_YOUR_PROFILE // * Triggered when visiting "/profile"
}

@Serializable
enum class Platform {
	@SerialName("Java") Java,
	@SerialName("Bedrock") Bedrock
}

@Serializable
enum class TagType {
	KEYWORD,
	VERSION,
	LOCATION,
	LANGUAGE,
	ADMIN_KEYWORD
}

@Serializable
enum class SocialMedia {
	EMAIL,
	WEBSITE,
	STORE,
	PATREON,
	DISCORD,
	FACEBOOK,
	INSTAGRAM,
	TWITTER,
	TWITCH,
	YOUTUBE,
	TIKTOK,
	TEAMSPEAK,
	BLUESKY;

	companion object {
		fun fromString(value: String?): SocialMedia? = runCatching {
			value?.uppercase()?.let { valueOf(it) }
		}.getOrNull()
	}
}

@Serializable
enum class ServerHost {
	@SerialName("Apex Hosting") APEX_HOSTING,
	@SerialName("Aternos") ATERNOS,
	@SerialName("Bisect Hosting") BISECT_HOSTING,
	@SerialName("BloomHost") BLOOM_HOST,
	@SerialName("CreeperHost") CREEPER_HOST,
	@SerialName("DedicatedMC") DEDICATED_MC,
	@SerialName("Enxada") ENXADA,
	@SerialName("Ethera") ETHERA,
	@SerialName("GPORTAL") GPORTAL,
	@SerialName("Hetzner") HETZNER,
	@SerialName("MC Pro-Hosting") MC_PRO_HOSTING,
	@SerialName("Minehut") MINEHUT,
	@SerialName("Nexril") NEXRIL,
	@SerialName("Nitrado") NITRADO,
	@SerialName("Novonode") NOVONODE,
	@SerialName("Other") OTHER,
	@SerialName("OVH") OVH,
	@SerialName("Pebblehost") PEBBLEHOST,
	@SerialName("ScalaCube") SCALACUBE,
	@SerialName("SELF-HOSTED") SELF_HOSTED,
	@SerialName("Shockbyte") SHOCKBYTE,
	@SerialName("StickyPiston") STICKY_PISTON,
	@SerialName("UNKNOWN") UNKNOWN
}

@Serializable
enum class ServerStatus {
	ACTIVE_PUBLIC
}

@Serializable
enum class EventType {
	COMMUNITY,
	TOURNAMENT,
	FEATURE,
	STREAM,
	IN_PERSON,
	HOLIDAY
}

@Serializable
enum class EventJoinType {
	@SerialName("PUBLIC") PUBLIC,
	@SerialName("REGISTER") REGISTER,
	@SerialName("INVITE-ONLY") INVITE_ONLY;

	companion object {
		fun fromString(value: String?): EventJoinType? = when (value?.uppercase()) {
			"PUBLIC" -> PUBLIC
			"REGISTER" -> REGISTER
			"INVITE_ONLY" -> INVITE_ONLY
			else -> null
		}
	}
}

@Serializable
enum class GameSaferStatus {
	CREATED,
	VERIFIED_EMAIL,
	COMPLETED_WORKBOOK,
	IN_QUEUE,
	MOJANG_REVIEW,
	ACCEPTED,
	SPOT_CHECK,
	ON_HOLD
}

@Serializable
enum class MojangStatus {
	GS_REPORTED,
	QUEUE,
	REVIEWING,
	CONTACTED,
	HOLD,
	REVIEWED,
	GS_RE_REVIEW,
	DECLINED
}

@Serializable
enum class RolesType {
	SITE_ADMIN,
	SITE_MODERATOR,
	TEAM_GS_MEMBER,
	TEAM_MJ_MEMBER,
	SERVER_OPERATOR,
	SITE_USER
}

@Serializable
data class LoginSessionData(
	val email: String,
	val userId: String,
	val platformUserId: String?,
	val platformUserName: String?,
	val platformImg: String?,
	val rolesType: RolesType,
	val newPlayer: Boolean?,
	val userIdEnviado: String?
)

@Serializable
data class LoginSessionPayload(
	val payload: String
)

@Serializable
data class TrackingEventRequest(
	val event: TrackingEventType,
	val server: String?,
	val device: String,
	val browserId: String,
	val country: String,
	val day: Int,
	val hour: Int,
	val month: Int,
	val year: Int
)

@Serializable
data class Image(
	val id: String,
	val url: String,
	val altText: String?,
	val title: String?
)

@Serializable
data class ServerKeywordTag(
	val id: String,
	val name: String,
	val description: String,
	val type: TagType = TagType.KEYWORD,
	val is_highlighted: Boolean,
	val created_at: String
)

@Serializable
data class ServerVersionTag(
	val id: String,
	val name: String,
	val description: String,
	val type: TagType = TagType.VERSION,
	val is_highlighted: Boolean,
	val created_at: String
)

@Serializable
data class ServerLocationTag(
	val id: String,
	val name: String,
	val description: String?,
	val type: TagType = TagType.LOCATION,
	val is_highlighted: Boolean,
	val created_at: String
)

@Serializable
data class ServerLanguageTag(
	val id: String,
	val name: String,
	val description: String?,
	val type: TagType = TagType.LANGUAGE,
	val is_highlighted: Boolean,
	val created_at: String
)

@Serializable
data class ServerAdminKeywordTag(
	val id: String,
	val name: String,
	val description: String,
	val type: TagType = TagType.ADMIN_KEYWORD,
	val is_highlighted: Boolean,
	val created_at: String
)

@Serializable
data class ServerBadge(
	val id: String,
	val name: String,
	val description: String,
	val icon_url: String
)

@Serializable
data class BasicServerInfo(
	val id: String,
	val name: String,
	val slug: String,
	val isFeatured: Boolean,
	val experienceId: String?,
	val iconImage: Image,
	val backgroundImage: Image?,
	val featuredImage: Image?,
	val shortDescription: String?,
	val currentOnlinePlayers: Int,
	val currentMaxPlayers: Int,
	val isMainAddressVisible: Boolean,
	val javaAddress: String?,
	val javaPort: Int?,
	val bedrockAddress: String?,
	val bedrockPort: Int?,
	val votes: Int,
	val favoriteCount: Int,
	val mapLink: String?,
	val votesLast30Days: Int,
	val isOnline: Boolean,
	val serverTags: List<ServerKeywordTag>,
	val serverBadges: List<ServerBadge>?,
	val serversServices: Unknown?, // TODO - I scraped every server on the API and none have this field set
	val serverLanguage: List<ServerLanguageTag>,
	val serverLocation: List<ServerLocationTag>
)

@Serializable
data class ServerSearchResults(
	val data: List<BasicServerInfo>,
	val count: Int,
	val page: Int,
	val pageSize: Int,
	val sortBy: String
)

@Serializable
data class ServerDetailsBadge(
	val id: String,
	val name: String,
	val description: String,
	val icon_url: String,
	val iconUrl: String?
) {
	val effectiveIconUrl: String get() = iconUrl ?: icon_url
}

@Serializable
data class ServerSocialMedia(
	val url: String,
	val description: String,
	val social_media: SocialMedia
)

@Serializable
data class ServerSecondaryAddress(
	val platform: Platform,
	val address: String,
	val port: Int
)

@Serializable
data class ServerDetails(
	val id: String,
	val name: String,
	val slug: String,
	val iconImage: Image,
	val backgroundImage: Image?,
	val featuredImage: Image?,
	val shortDescription: String?,
	val isFeatured: Boolean,
	val experienceId: String?,
	val currentOnlinePlayers: Int,
	val currentMaxPlayers: Int,
	val isOnline: Boolean,
	val isHostVisible: Boolean,
	val isMainAddressVisible: Boolean,
	val allowSwearing: Boolean,
	val serverTags: List<ServerKeywordTag>,
	val serverBadges: List<ServerDetailsBadge>?,
	val serversServices: Unknown?, // TODO - I scraped every server on the API and none have this field set
	val serverLanguage: List<ServerLanguageTag>,
	val serverLocation: List<ServerLocationTag>,
	val serverMedias: List<ServerSocialMedia>,
	val longDescription: String,
	val currentMotd: String,
	val rawMotd: String,
	val presentationVideoUrl: String?,
	val mapLink: String?,
	val screenshotImages: List<Image>?,
	val javaAddress: String?,
	val javaPort: Int?,
	val bedrockAddress: String?,
	val bedrockPort: Int?,
	val secondaryAddresses: List<ServerSecondaryAddress>?,
	val modsLoaded: Unknown?, // TODO - I scraped every server on the API and none have this field set
	val claimedOn: String,
	val launchedOn: String,
	val privacyPolicyUrl: String?,
	val termsOfServiceUrl: String?,
	val codeOfConduct: String?,
	val codeOfConductUrl: String?,
	val hostName: ServerHost?,
	val statusName: ServerStatus,
	val version: List<ServerVersionTag>,
	val lookupVersion: String?,
	val votes: Int,
	val favoriteCount: Int,
	val votesLast30Days: Int
)

@Serializable
data class EventImage(
	val id: String,
	val url: String,
	val altText: String?,
	val title: String?,
	val imageType: String
)

@Serializable
data class ServerEvent(
	val id: String,
	val slug: String,
	val title: String,
	val longDescription: String,
	val eventType: EventType,
	val joinType: EventJoinType,
	val publishDate: String?,
	val startingDate: String,
	val endingDate: String,
	val highlightDate: String?,
	val serverId: String,
	val serverName: String,
	val serverSlug: String,
	val iconImage: EventImage,
	val backgroundImage: EventImage,
	val featuredImage: EventImage,
	val screenshotImages: List<EventImage>,
	val linkUrl: String?,
	val isHighlighted: Boolean,
	val eventJoinedCount: Int
)

@Serializable
data class ServerEventsList(
	val data: List<ServerEvent>,
	val count: Int,
	val page: Int,
	val pageSize: Int,
	val sortBy: String
)

@Serializable
data class EventDetails(
	val id: String,
	val slug: String,
	val title: String,
	val longDescription: String,
	val eventType: EventType,
	val joinType: EventJoinType,
	val publishDate: String?,
	val startingDate: String,
	val endingDate: String,
	val highlightDate: String?,
	val serverId: String,
	val serverName: String,
	val serverSlug: String,
	val iconImage: EventImage,
	val backgroundImage: EventImage,
	val featuredImage: EventImage,
	val screenshotImages: List<EventImage>,
	val presentationVideoUrl: String?,
	val codeOfConduct: String?,
	val codeOfConductUrl: String?,
	val prizes: String,
	val cost: Unknown?, // TODO - I can only find one event, mMHEqVsAOW, which doesn't have these populated
	val howToJoin: Unknown?, // TODO - I can only find one event, mMHEqVsAOW, which doesn't have these populated
	val linkUrl: String?,
	val isApproved: Boolean,
	val isHighlighted: Boolean,
	val isActive: Boolean,
	val createdAt: String,
	val eventJoinedCount: Int,
	val server: ServerDetails
)

// TODO - This never seems to have any data populated
@Serializable
data class EventDiscoveryResult(
	val highlighted: List<Unknown>,
	val live: List<Unknown>,
	val upcoming: List<Unknown>
)

@Serializable
data class BadgeListBadge(
	val id: String,
	val name: String,
	val description: String,
	val badgeType: String,
	val iconUrl: String,
	val createdAt: String,
	val isActive: Boolean
)

@Serializable
data class TagListTag(
	val id: String,
	val name: String,
	val description: String?,
	val serversCount: Int,
	val type: TagType,
	val createdAt: String,
	val isActive: Boolean
)

@Serializable
data class FeaturedTagCollectionItem(
	val type: String, // * Always "tags"?
	val label: String,
	val value: String // * Tag ID
)

@Serializable
data class FeaturedTagCollection(
	val name: String,
	val description: String,
	val searchItems: List<FeaturedTagCollectionItem>
)

@Serializable
data class WorkbookQuestion(
	val id: String,
	val question: String,
	val order: Int,
	val operatorDescription: String,
	val publicDescription: String? // TODO - No questions have this set, always null?
)

@Serializable
data class WorkbookAnswer(
	val id: String,
	val serverId: String,
	val questionId: String,
	val answer: String,
	val isPublic: Boolean,
	val isIncomplete: Boolean,
	val serverDontUnderstand: Boolean,
	val improvedAnswer: Boolean,
	val createdAt: String
)

@Serializable
data class IPInfo(
	val ip: String,
	val hostname: String?,
	val city: String?,
	val region: String?,
	val country: String?,
	val loc: String?,
	val org: String?,
	val postal: String?,
	val timezone: String?,
	val readme: String?
)

@Serializable
data class VoteRequest(
	val serverId: String,
	val playerUsername: String,
	val ipAddress: String
)

@Serializable
data class VoteResult(
	val success: Boolean
)