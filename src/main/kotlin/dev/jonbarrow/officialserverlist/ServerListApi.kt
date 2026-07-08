package dev.jonbarrow.officialserverlist

import net.minecraft.client.Minecraft
import net.fabricmc.loader.api.FabricLoader
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDateTime
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Duration

// * https://findmcserver.com/_next/static/LXx_EeiRp4m3mLesjU8mq/_buildManifest.js
// * All API endpoints:
// *
// * - [x] /api
// * - [x] /api/auth/getSession
// * - [x] /api/auth/logout
// * - [x] /api/auth/minecraft-login/[code]
// * - [x] /api/auth/[hash]
// * - [x] /api/badges
// * - [ ] /api/contact
// * - [x] /api/events
// * - [ ] /api/events/admin/[userId]/[serverId]
// * - [ ] /api/events/create
// * - [x] /api/events/discover
// * - [ ] /api/events/edit/[id]
// * - [ ] /api/events/edit-details/[userId]/[slug]
// * - [ ] /api/events/favorite/add
// * - [x] /api/events/favorite/list
// * - [ ] /api/events/favorite/remove
// * - [ ] /api/events/highlights/[id]
// * - [x] /api/events/servers/[serverId]
// * - [x] /api/events/slug/[slug]
// * - [ ] /api/events/tokens/purchase/[userId]
// * - [ ] /api/events/tokens/[userId]
// * - [x] /api/events/upcoming/[userId]/upcomingEvents
// * - [ ] /api/launcher/servers
// * - [ ] /api/launcher/servers/complete
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
// * - [x] /api/servers/favorite/list
// * - [ ] /api/servers/favorite/remove
// * - [x] /api/servers/hosts
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
// * - [x] /api/user/delete/[userId]
// * - [x] /api/user/getServers
// * - [x] /api/user/getUserPreferences/[userId]
// * - [ ] /api/user/linkGsProfile
// * - [ ] /api/user/linkGsProfile/activate2FA
// * - [ ] /api/user/linkGsProfile/isValid
// * - [x] /api/user/updateUserPreferences
// * - [ ] /api/verify-email

object ServerListApi {
	private val json = Json {
		ignoreUnknownKeys = true
		coerceInputValues = true
		explicitNulls = false
	}
	private var cachedIPAddress: String? = null // * A few API endpoints consume this, so cache it to not spam the API
	private val cookieManager = CookieManager().apply {
		setCookiePolicy(CookiePolicy.ACCEPT_ALL)
	}
	private val client: HttpClient = HttpClient.newBuilder().cookieHandler(cookieManager).build()

	private const val IP_INFO_API = "https://ipinfo.io/json" // * This is used for the voting endpoint. This is the API the official website uses, so use it here too for consistency
	private const val API_BASE = "https://findmcserver.com/api"
	private const val USER_AGENT = "OfficialServerListMod/1.1.0 (Fabric Minecraft Mod)" // * Let's be nice and tell them who we are. Don't resort to spoofing just yet
	private const val SECURITY_KEY = "Mbh6Ku8kVfrvv1DVWekX" // * This is a static key the official client uses to both AES encrypt certain responses and sign client-created JWTs
	private val SESSION_FILE = FabricLoader.getInstance().configDir.resolve("officialserverlist").resolve("session.dat")

	var persistSessionCookie = false
	var loginSession: LoginSessionData? = null

	init {
		loadPersistedCookies()
	}

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

	fun loginWithHash(hash: String) {
		val response = request<LoginSessionPayload>("$API_BASE/auth/$hash").getOrNull() ?: return
		val decrypted = CryptoUtil.decryptCryptoJS(response.payload, SECURITY_KEY)

		loginSession = json.decodeFromString<LoginSessionData>(decrypted)
		persistCookies()
	}

	fun linkMinecraftAccount(code: String) {
		loginSession = request<LoginSessionData>("$API_BASE/auth/minecraft-login/$code").getOrNull() ?: return
		persistCookies()
	}

	fun getSession(): Result<LoginSessionData> {
		val response = request<LoginSessionPayload>("$API_BASE/auth/getSession").getOrNull() ?: return Result.failure(Exception("getSession request failed"))
		val decrypted = CryptoUtil.decryptCryptoJS(response.payload, SECURITY_KEY)

		loginSession = json.decodeFromString<LoginSessionData>(decrypted)

		return Result.success(loginSession!!)
	}

	fun fetchUserPreferences(userID: String): Result<UserPreferences> {
		return request<UserPreferences>("$API_BASE/user/getUserPreferences/$userID")
	}

	fun updateUserPreferences(userID: String, payload: UpdateUserPreferencesPayload): Result<UserPreferences> {
		val options = buildJsonObject {
			put("expiresIn", "60") // * This functionally does nothing, but the real client sends it, so we do too
		}
		val token = CryptoUtil.buildJWT(payload, SECURITY_KEY, options)
		val requestPayload = UpdateUserPreferencesRequest(
			payload = token,
			userId = userID
		)

		return requestPatch<UpdateUserPreferencesRequest, UserPreferences>("$API_BASE/user/updateUserPreferences", requestPayload)
	}

	fun logout() {
		requestPost<Unit, Unit>("$API_BASE/auth/logout", null)
		cookieManager.cookieStore.removeAll() // * Endpoint returns a Set-Cookie header to set the cookie to an empty string with maxAge=0, but clear it just in case
		clearPersistedCookies()
		loginSession = null
	}

	fun deleteAccount(userID: String, payload: DeleteAccountPayload): Result<DeleteAccountResponse> {
		val options = buildJsonObject {
			put("expiresIn", "60") // * This functionally does nothing, but the real client sends it, so we do too
		}
		val token = CryptoUtil.buildJWT(payload, SECURITY_KEY, options)
		val requestPayload = DeleteAccountRequest(
			payload = token
		)

		return requestDelete<DeleteAccountRequest, DeleteAccountResponse>("$API_BASE/user/delete/$userID", requestPayload)
	}

	// * Returns a combination of /api/servers/discover and /api/tags/featured?
	// * Unsure what the usecase here is
	fun discoverServersAndFetchFeaturedTags(): Result<DiscoverServersAndFetchFeaturedTagsResponse> {
		return request<DiscoverServersAndFetchFeaturedTagsResponse>("$API_BASE")
	}

	// * Used to display the servers on the main https://findmcserver.com home page, before you search
	fun discoverServers(): Result<List<BasicServerInfo>> {
		return request<List<BasicServerInfo>>("$API_BASE/servers/discover")
	}

	// * Searches for servers based on certain criteria
	fun searchServers(filters: ServerSearchFilters, pageSize: Int = 15): Result<ServerSearchResults> {
		val params = mutableListOf<Pair<String, String>>().apply {
			add("pageNumber" to filters.pageNumber.toString())
			add("pageSize" to pageSize.toString())
			add("sortBy" to filters.sortBy.queryValue)
			add("gamerSaferStatus" to "undefined") // * This seems to be a GameSaferStatus, but it seems to do nothing?
			add("mojangStatus" to "undefined") // * This seems to be a MojangStatus, but it seems to do nothing?
			add("edition" to filters.edition.queryValue)
			add("size" to filters.playerCountQueryValue())

			if (filters.hasExperienceID) {
				add("hasExperienceId" to "true")
			}

			if (filters.selectedBadgeIDs.isNotEmpty()) {
				add("badges" to filters.selectedBadgeIDs.joinToString(","))
			}

			val tags = filters.allTagIds()
			if (tags.isNotEmpty()) {
				add("tags" to tags.joinToString(","))
			}

			if (filters.searchPhrase.isNotBlank()) {
				add("searchTerms" to filters.searchPhrase)
			}
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

	// * Gets a list of the users favorited servers
	fun fetchFavoritedServers(userID: String): Result<List<FavoritedServer>> {
		val params = mutableListOf<Pair<String, String>>().apply {
			add("userId" to userID)
		}

		return request<List<FavoritedServer>>("$API_BASE/servers/favorite/list?" + buildQueryString(params))
	}

	// * Gets a list of the users owned/managed servers
	// TODO - I *think* these query paramaters and response type are correct, however I don't own any servers so I don't actually know. Seems right though
	fun fetchManagedServers(userID: String, filters: ServerSearchFilters, pageSize: Int = 15): Result<ServerSearchResults> {
		val params = mutableListOf<Pair<String, String>>().apply {
			add("userId" to userID)
			add("pageNumber" to filters.pageNumber.toString())
			add("pageSize" to pageSize.toString())
			add("sortBy" to filters.sortBy.queryValue)
			add("edition" to filters.edition.queryValue)
			add("size" to filters.playerCountQueryValue())

			if (filters.hasExperienceID) {
				add("hasExperienceId" to "true")
			}

			if (filters.selectedBadgeIDs.isNotEmpty()) {
				add("badges" to filters.selectedBadgeIDs.joinToString(","))
			}

			val tags = filters.allTagIds()
			if (tags.isNotEmpty()) {
				add("tags" to tags.joinToString(","))
			}

			if (filters.searchPhrase.isNotBlank()) {
				add("searchTerms" to filters.searchPhrase)
			}
		}

		return request<ServerSearchResults>("$API_BASE/user/getServers?" + buildQueryString(params))
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
	fun searchEvents(filters: EventSearchFilters): Result<ServerEventsList> {
		val params = mutableListOf<Pair<String, String>>().apply {
			add("eventType" to filters.eventTypeQueryValue())
			add("joinType" to filters.joinTypeQueryValue())
		}

		return request<ServerEventsList>("$API_BASE/events?" + buildQueryString(params))
	}

	// * Gets a list of upcoming events registered to users the target user has
	// * has listed in their favorite servers
	fun fetchUpcomingEvents(userID: String): Result<List<BasicEventInfo>> {
		return request<List<BasicEventInfo>>("$API_BASE/events/upcoming/$userID/upcomingEvents")
	}

	// * Gets a list of the users favorited events
	fun fetchFavoritedEvents(userID: String): Result<List<ServerEvent>> {
		val params = mutableListOf<Pair<String, String>>().apply {
			add("userId" to userID)
		}

		return request<List<ServerEvent>>("$API_BASE/events/favorite/list?" + buildQueryString(params))
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
		val loginSession = loginSession
		val username = if (loginSession?.platformUserName != null) loginSession.platformUserName else Minecraft.getInstance().user.name
		val ipAddress = getIPAddress()
		return requestPost<VoteRequest, VoteResult>("$API_BASE/servers/vote", VoteRequest(serverID, username, ipAddress))
	}

	// * Gets a list of Minecraft server hosts FMCS officially recognizes
	// * Requires a login session, otherwise this endpoint throws 401, presumably
	// * because it's only used during the "add a server" flow?
	fun fetchServerHosts(): Result<ServerHostListResponse> {
		return request<ServerHostListResponse>("$API_BASE/servers/hosts")
	}

	// * Helpers

	private fun persistCookies() {
		if (!persistSessionCookie) {
			return
		}

		try {
			val cookies = cookieManager.cookieStore.cookies.map { cookie ->
				val maxAge = cookie.maxAge
				val expiresAt = if (maxAge < 0) {
					-1L
				} else {
					System.currentTimeMillis() + maxAge * 1000L
				}

				PersistedCookie(
					name = cookie.name,
					value = cookie.value,
					domain = cookie.domain,
					path = cookie.path,
					expiresAt = expiresAt,
					secure = cookie.secure,
					httpOnly = cookie.isHttpOnly,
					version = cookie.version
				)
			}

			if (cookies.isEmpty()) {
				return
			}

			val plaintext = json.encodeToString(cookies)
			val obfuscated = CryptoUtil.obfuscateCookie(plaintext)

			Files.createDirectories(SESSION_FILE.parent)
			Files.writeString(SESSION_FILE, obfuscated)
		} catch (e: Exception) {
			// TODO - Handle this
		}
	}

	private fun loadPersistedCookies() {
		try {
			if (!Files.exists(SESSION_FILE)) {
				return
			}

			val obfuscated = Files.readString(SESSION_FILE)
			val plaintext = CryptoUtil.deobfuscateCookie(obfuscated) ?: run {
				// * Yeet the bitch if something goes wrong, just make the user login again
				clearPersistedCookies()
				return
			}

			val cookies = json.decodeFromString<List<PersistedCookie>>(plaintext)
			val now = System.currentTimeMillis()

			for (persisted in cookies) {
				val maxAge: Long = if (persisted.expiresAt < 0) {
					-1L
				} else {
					val remaining = persisted.expiresAt - now
					if (remaining <= 0) {
						continue
					}

					remaining / 1000L
				}

				val cookie = HttpCookie(persisted.name, persisted.value).apply {
					domain = persisted.domain
					path = persisted.path
					this.maxAge = maxAge
					secure = persisted.secure
					isHttpOnly = persisted.httpOnly
					version = persisted.version
				}

				cookieManager.cookieStore.add(URI.create("https://findmcserver.com"), cookie)
			}

			getSession()
		} catch (e: Exception) {
			clearPersistedCookies()
		}
	}

	private fun clearPersistedCookies() {
		try {
			Files.deleteIfExists(SESSION_FILE)
		} catch (e: Exception) {
			// * Nothing to do if we can't delete it
		}
	}

	private fun getIPAddress(): String {
		cachedIPAddress?.let { return it }

		val fetched = request<IPInfo>(IP_INFO_API).getOrNull()?.ip ?: ""
		if (fetched.isNotBlank()) {
			cachedIPAddress = fetched
		}

		return fetched
	}

	// TODO - Merge all these request functions into one

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

	private inline fun <reified TBody, reified TResponse> requestPost(url: String, body: TBody? = null): Result<TResponse> {
		return try {
			val builder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Accept", "application/json")
				.header("User-Agent", USER_AGENT)

			if (body != null) {
				val bodyJson = json.encodeToString(body)
				builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(bodyJson))
			} else {
				builder.POST(HttpRequest.BodyPublishers.noBody())
			}

			val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())

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

	private inline fun <reified TBody, reified TResponse> requestPatch(url: String, body: TBody): Result<TResponse> {
		return try {
			val bodyJson = json.encodeToString(body)

			val request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Accept", "application/json")
				.header("Content-Type", "application/json")
				.header("User-Agent", USER_AGENT)
				.method("PATCH", HttpRequest.BodyPublishers.ofString(bodyJson))
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

	private inline fun <reified TBody, reified TResponse> requestDelete(url: String, body: TBody): Result<TResponse> {
		return try {
			val bodyJson = json.encodeToString(body)

			val request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Accept", "application/json")
				.header("Content-Type", "application/json")
				.header("User-Agent", USER_AGENT)
				.method("DELETE", HttpRequest.BodyPublishers.ofString(bodyJson))
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