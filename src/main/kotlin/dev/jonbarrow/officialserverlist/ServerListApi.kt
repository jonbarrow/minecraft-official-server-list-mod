package dev.jonbarrow.officialserverlist

import net.minecraft.client.Minecraft
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

// * https://findmcserver.com/_next/static/EQXVm_bYrkKWKVrcOxdTp/_buildManifest.js
// * All API endpoints:
// *
// * - [ ] /api
// * - [ ] /api/auth/getSession
// * - [ ] /api/auth/logout
// * - [ ] /api/auth/minecraft-login/[code]
// * - [ ] /api/auth/[hash]
// * - [x] /api/badges
// * - [ ] /api/contact
// * - [x] /api/events
// * - [ ] /api/events/admin/[userId]/[serverId]
// * - [ ] /api/events/create
// * - [x] /api/events/discover
// * - [ ] /api/events/edit/[id]
// * - [ ] /api/events/edit-details/[userId]/[slug]
// * - [ ] /api/events/favorite/add
// * - [ ] /api/events/favorite/list
// * - [ ] /api/events/favorite/remove
// * - [ ] /api/events/highlights/[id]
// * - [x] /api/events/servers/[serverId]
// * - [x] /api/events/slug/[slug]
// * - [ ] /api/events/tokens/purchase/[userId]
// * - [ ] /api/events/tokens/[userId]
// * - [ ] /api/events/upcoming/[userId]/upcomingEvents
// * - [ ] /api/launcher/servers
// * - [ ] /api/launcher/servers/featured
// * - [x] /api/servers
// * - [ ] /api/servers/create
// * - [ ] /api/servers/deactivate/[serverId]
// * - [ ] /api/servers/delete/[serverId]
// * - [ ] /api/servers/details/[slug]/[userId]
// * - [x] /api/servers/discover
// * - [ ] /api/servers/download/downloadFavoriteServers
// * - [ ] /api/servers/edit
// * - [ ] /api/servers/favorite/add
// * - [ ] /api/servers/favorite/list
// * - [ ] /api/servers/favorite/remove
// * - [ ] /api/servers/hosts
// * - [ ] /api/servers/serverOperators/addServerOperator
// * - [ ] /api/servers/serverOperators/removeServerOperator
// * - [ ] /api/servers/status
// * - [ ] /api/servers/uploadImage
// * - [ ] /api/servers/validate-mail/resend
// * - [x] /api/servers/vote
// * - [ ] /api/servers/voteTest/[serverId]
// * - [x] /api/servers/workbook
// * - [ ] /api/servers/workbook/storeAnswers
// * - [x] /api/servers/workbook/workBookServerAnswers/[serverId]
// * - [x] /api/servers/[slug]
// * - [x] /api/tags
// * - [x] /api/tags/featured
// * - [x] /api/tracking
// * - [ ] /api/user/delete/[userId]
// * - [ ] /api/user/getServers
// * - [ ] /api/user/getUserPreferences/[userId]
// * - [ ] /api/user/linkGsProfile
// * - [ ] /api/user/linkGsProfile/activate2FA
// * - [ ] /api/user/linkGsProfile/isValid
// * - [ ] /api/user/updateUserPreferences
// * - [ ] /api/verify-email

object ServerListApi {
	private val client: HttpClient = HttpClient.newBuilder().build()
	private val json = Json {
		ignoreUnknownKeys = true
		coerceInputValues = true
		explicitNulls = false
	}
	private var cachedIPAddress: String? = null // * A few API endpoints consume this, so cache it to not spam the API

	private const val IP_INFO_API = "https://ipinfo.io/json" // * This is used for the voting endpoint. This is the API the official website uses, so use it here too for consistency
	private const val API_BASE = "https://findmcserver.com/api"
	private const val USER_AGENT = "OfficialServerListMod/1.0 (Fabric Minecraft Mod)" // * Let's be nice and tell them who we are. Don't resort to spoofing just yet

	// * Used by the official website for analytics tracking.
	// * Not used here, but implemented for documentation purposes
	fun trackEvent(eventType: TrackingEventType, serverID: String? = null): Result<Unit> {
		val now = LocalDateTime.now()
		val payload = TrackingEventRequest(
			event = eventType,
			server = serverID,
			device = "", // * The official client runs several checks on the device to see what kind of device it is, before finally using the result of "r.isMobile ? `${r.mobileVendor}-${r.mobileModel}: ${r.osName} ${r.osVersion}` : `${r.osName} ${r.osVersion}`"
			browserId = getIPAddress(),
			country = "", // * The official client gets this from a list of timezones, using "Intl.DateTimeFormat().resolvedOptions().timeZone" as the lookup
			day = now.dayOfMonth,
			hour = now.hour,
			month = now.monthValue,
			year = now.year
		)

		return requestPost<TrackingEventRequest, Unit>("$API_BASE/tracking", payload)
	}

	// * Used to display the servers on the main https://findmcserver.com home page, before you search
	fun discoverServers(): Result<List<BasicServerInfo>> {
		return request<List<BasicServerInfo>>("$API_BASE/servers/discover")
	}

	// * Searches for servers based on certain criteria
	fun searchServers(): Result<ServerSearchResults> {
		val params = mutableListOf<Pair<String, String>>().apply {
			add("pageNumber" to "0")
			add("pageSize" to "15")
			add("sortBy" to "default")
			add("gamerSaferStatus" to "undefined")
			add("mojangStatus" to "undefined")
			add("edition" to "Java")
			add("size" to "")
		}

		return request<ServerSearchResults>("$API_BASE/servers?" + buildQueryString(params))
	}

	// * Gets the details of a specific server
	fun fetchServerDetails(slug: String): Result<ServerDetails> {
		return request<ServerDetails>("$API_BASE/servers/$slug")
	}

	// * Gets the events of a specific server
	fun fetchServerEvents(serverID: String): Result<ServerEventsList> {
		return request<ServerEventsList>("$API_BASE/events/servers/$serverID")
	}

	// * Gets the details of a specific event
	fun fetchEventDetails(slug: String): Result<EventDetails> {
		return request<EventDetails>("$API_BASE/events/slug/$slug")
	}

	// * Find featured events? Unknown usage, the API never seems to populate this data
	fun discoverEvents(): Result<EventDiscoveryResult> {
		return request<EventDiscoveryResult>("$API_BASE/events/discover")
	}

	// * Searches for all events based on certain criteria
	fun searchEvents(): Result<ServerEventsList> {
		return request<ServerEventsList>("$API_BASE/events")
	}

	// * Gets a list of the badges a server can have.
	// * Not all of these are shown in the official UI for some reason
	fun fetchBadges(): Result<List<BadgeListBadge>> {
		return request<List<BadgeListBadge>>("$API_BASE/badges")
	}

	// * Gets all the tags of a specific type
	fun fetchTags(type: String): Result<List<TagListTag>> {
		return request<List<TagListTag>>("$API_BASE/tags?type=$type")
	}

	// * Gets the "Quick Searches" tag collections on the main https://findmcserver.com home page
	fun fetchFeaturedTags(type: String): Result<List<FeaturedTagCollection>> {
		return request<List<FeaturedTagCollection>>("$API_BASE/tags/featured")
	}

	// * Gets a list of the workbook questions server owners are asked about their server
	fun fetchWorkbookQuestions(): Result<List<WorkbookQuestion>> {
		return request<List<WorkbookQuestion>>("$API_BASE/servers/workbook")
	}

	// * Gets the responses of a specific server to the workbook questions.
	// * Even if a server has made responses private, they may still show up in the API response.
	// * The filtering is done client-side through the "isPublic" field
	fun fetchWorkbookAnswers(serverID: String): Result<List<WorkbookAnswer>> {
		return request<List<WorkbookAnswer>>("$API_BASE/servers/workbook/workBookServerAnswers/$serverID")
	}

	// * Votes for a specific server
	fun voteForServer(serverID: String): Result<VoteResult> {
		val username = Minecraft.getInstance().user.name
		val ipAddress = getIPAddress()
		return requestPost<VoteRequest, VoteResult>("$API_BASE/servers/vote", VoteRequest(serverID, username, ipAddress))
	}

	private fun getIPAddress(): String {
		cachedIPAddress?.let { return it }

		val fetched = request<IPInfo>(IP_INFO_API).getOrNull()?.ip ?: ""
		if (fetched.isNotBlank()) {
			cachedIPAddress = fetched
		}

		return fetched
	}

	private inline fun <reified TResponse> request(url: String): Result<TResponse> {
		return try {
			val request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Accept", "application/json")
				.header("User-Agent", USER_AGENT)
				.GET()
				.build()

			val response = client.send(request, HttpResponse.BodyHandlers.ofString())

			if (TResponse::class == Unit::class) {
				@Suppress("UNCHECKED_CAST")
				return Result.success(Unit as TResponse)
			}

			val parsed = json.decodeFromString<TResponse>(response.body())

			Result.success(parsed)
		} catch (e: Exception) {
			Result.failure(e)
		}
	}

	private inline fun <reified TBody, reified TResponse> requestPost(url: String, body: TBody): Result<TResponse> {
		return try {
			val bodyJson = json.encodeToString(body)

			val request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Accept", "application/json")
				.header("Content-Type", "application/json")
				.header("User-Agent", USER_AGENT)
				.POST(HttpRequest.BodyPublishers.ofString(bodyJson))
				.build()

			val response = client.send(request, HttpResponse.BodyHandlers.ofString())

			if (TResponse::class == Unit::class) {
				@Suppress("UNCHECKED_CAST")
				return Result.success(Unit as TResponse)
			}

			val parsed = json.decodeFromString<TResponse>(response.body())

			Result.success(parsed)
		} catch (e: Exception) {
			Result.failure(e)
		}
	}

	private fun buildQueryString(params: List<Pair<String, String>>): String =
		params.joinToString("&") { (k, v) ->
			"$k=" + URLEncoder.encode(v, StandardCharsets.UTF_8)
		}
}