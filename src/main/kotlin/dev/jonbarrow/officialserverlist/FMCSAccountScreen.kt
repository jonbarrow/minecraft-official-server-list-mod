// TODO - All this UI code is very repetitive, can this be abstracted out? Maybe as a UI framework?

package dev.jonbarrow.officialserverlist

import java.util.Base64
import java.util.concurrent.CompletableFuture
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.components.LoadingDotsWidget
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component

class FMCSAccountScreen(private val parent: Screen) : Screen(Component.translatable("officialserverlist.screen.server_list.title")) {
	companion object {
		private const val DEFAULT_USER_IMAGE = "https://findmcserver.com/assets/user_default_img.webp"
		private const val UPCOMING_EVENT_ICON = "https://findmcserver.com/assets/calendar-icon.webp"

		private const val SIDE_PADDING = 12
		private const val HEADER_TOP = 8
		private const val HEADER_HEIGHT = 56
		private const val HEADER_BUTTON_WIDTH = 110
		private const val HEADER_BUTTON_HEIGHT = 20
		private const val HEADER_BUTTON_GAP = 4

		private const val TAB_BAR_HEIGHT = 20
		private const val TAB_BAR_GAP = 4
		private const val BOTTOM_BAR_HEIGHT = 40
	}

	enum class Tab(val labelKey: String) {
		// TODO - Should "Your Servers" be before "Favorite Servers"?
		FAVORITE_SERVERS("officialserverlist.tab.favorite_servers"),
		YOUR_SERVERS("officialserverlist.tab.your_servers"),
		FAVORITE_EVENTS("officialserverlist.tab.favorite_events"),
		UPCOMING_EVENTS("officialserverlist.tab.upcoming_events")
	}

	private val managedServersFilters = ServerSearchFilters()
	private var managedServers: ServerSearchResults? = null
	private var favoriteServers: List<FavoritedServer>? = null
	private var upcomingEvents: List<BasicEventInfo>? = null
	private var favoriteEvents: List<ServerEvent>? = null
	private var userPreferences: UserPreferences? = null
	private var loading: Boolean = false
	private var activeTab: Tab = Tab.FAVORITE_SERVERS
	private var sectionList: SectionListWidget? = null

	override fun init() {
		super.init()

		// * This is what the official client uses
		managedServersFilters.sortBy = ServerSearchFilters.SortOption.BADGES_VOTES

		if (ServerListApi.loginSession != null && (managedServers == null || favoriteServers == null || upcomingEvents == null || favoriteEvents == null || userPreferences == null)) {
			startFetch()
		}

		populateWidgets()
	}

	private fun startFetch() {
		loading = true

		CompletableFuture.supplyAsync {
			val loginSession = ServerListApi.loginSession!!
			val managedServers = ServerListApi.fetchManagedServers(loginSession.userId, managedServersFilters).getOrNull()
			val favoriteServers = ServerListApi.fetchFavoritedServers(loginSession.userId).getOrNull()
			val upcomingEvents = ServerListApi.fetchUpcomingEvents(loginSession.userId).getOrNull()
			val favoriteEvents = ServerListApi.fetchFavoritedEvents(loginSession.userId).getOrNull()
			val userPreferences = ServerListApi.fetchUserPreferences(loginSession.userId).getOrNull()

			listOf(managedServers, favoriteServers, upcomingEvents, favoriteEvents, userPreferences)
		}.thenAccept { results ->
			minecraft.execute {
				@Suppress("UNCHECKED_CAST")
				managedServers = results[0] as ServerSearchResults
				@Suppress("UNCHECKED_CAST")
				favoriteServers = results[1] as List<FavoritedServer>
				@Suppress("UNCHECKED_CAST")
				upcomingEvents = results[2] as List<BasicEventInfo>
				@Suppress("UNCHECKED_CAST")
				favoriteEvents = results[3] as List<ServerEvent>
				@Suppress("UNCHECKED_CAST")
				userPreferences = results[4] as UserPreferences
				loading = false
				clearWidgets()
				init(width, height)
			}
		}
	}

	private fun populateWidgets() {
		addRenderableWidget(
			Button.builder(Component.translatable("gui.back")) {
				returnToParent()
			}.bounds(width / 2 - 100, height - 28, 200, 20).build()
		)

		val loginSession = ServerListApi.loginSession

		if (loginSession == null) {
			addRenderableWidget(
				Button.builder(Component.translatable("officialserverlist.button.login")) {
					minecraft.setScreen(MicrosoftAuthScreen(this, MicrosoftAuthScreen.AuthType.LOGIN) { hash ->
						minecraft.execute {
							if (hash != null) {
								ServerListApi.loginWithHash(hash)
								clearWidgets()
								init(width, height)
							}
						}
					})
				}.bounds(width / 2 - 45, height / 2 - 10, 90, 20).build()
			)

			addRenderableWidget(
				Checkbox.builder(Component.translatable("officialserverlist.checkbox.remember_me"), font)
					.selected(ServerListApi.persistSessionCookie)
					.onValueChange { _, value ->
						ServerListApi.persistSessionCookie = value
						clearWidgets()
						init(width, height)
					}.pos(width / 2 - 45, height / 2 + 16).build()
			)

			return
		}

		if (loading) {
			val widget = LoadingDotsWidget(font, Component.translatable("officialserverlist.loading", Component.translatable("officialserverlist.loading_target.account_data")))
			widget.setPosition(width / 2 - widget.width / 2, height / 2 - 10)
			addRenderableWidget(widget)

			return
		}

		val iconSize = HEADER_HEIGHT - 8
		val textX = SIDE_PADDING + iconSize + 8

		val bottomRowY = HEADER_TOP + HEADER_HEIGHT - HEADER_BUTTON_HEIGHT - 4
		val topRowY = bottomRowY - HEADER_BUTTON_HEIGHT - HEADER_BUTTON_GAP

		val preferencesX = textX
		val logoutX = preferencesX + HEADER_BUTTON_WIDTH + HEADER_BUTTON_GAP
		val deleteX = logoutX + HEADER_BUTTON_WIDTH + HEADER_BUTTON_GAP

		addRenderableWidget(
			Button.builder(Component.translatable("officialserverlist.button.preferences")) { _ ->
				minecraft.execute {
					minecraft.setScreen(FMCSUserPreferencesScreen(this, userPreferences!!) { newPreferences ->
						minecraft.execute {
							userPreferences = ServerListApi.updateUserPreferences(loginSession.userId, newPreferences).getOrNull()
							clearWidgets()
							init(width, height)
						}
					})
				}
			}.bounds(preferencesX, bottomRowY, HEADER_BUTTON_WIDTH, HEADER_BUTTON_HEIGHT).build()
		)

		addRenderableWidget(
			Button.builder(Component.translatable("officialserverlist.button.logout")) {
				ServerListApi.logout()
				returnToParent()
			}.bounds(logoutX, bottomRowY, HEADER_BUTTON_WIDTH, HEADER_BUTTON_HEIGHT).build()
		)

		addRenderableWidget(
			Button.builder(Component.translatable("officialserverlist.button.delete_account")) {
				ConfirmDeleteFMCSAccountScreen.open(this, loginSession.userId) {
					returnToParent() // * Only runs if the user did delete their account, to pop them back up out of the account settings now that the account is gone
				}
			}.bounds(deleteX, bottomRowY, HEADER_BUTTON_WIDTH, HEADER_BUTTON_HEIGHT).build()
		)

		if (loginSession.platformUserId == null || loginSession.platformUserName == null) {
			addRenderableWidget(
				Button.builder(Component.translatable("officialserverlist.button.link_minecraft_account")) {
					minecraft.setScreen(MicrosoftAuthScreen(this, MicrosoftAuthScreen.AuthType.LINK_MINECRAFT) { code ->
						minecraft.execute {
							if (code != null) {
								ServerListApi.linkMinecraftAccount(code)
								clearWidgets()
								init(width, height)
							}
						}
					})
				}.bounds(deleteX, topRowY, HEADER_BUTTON_WIDTH, HEADER_BUTTON_HEIGHT).build()
			)
		}

		val tabBarY = HEADER_TOP + HEADER_HEIGHT + TAB_BAR_GAP
		val sections = Tab.entries
		val tabBarTotalWidth = width - SIDE_PADDING * 2
		val tabSpacing = 4
		val tabWidth = (tabBarTotalWidth - tabSpacing * (sections.size - 1)) / sections.size

		for ((index, section) in sections.withIndex()) {
			val tabX = SIDE_PADDING + index * (tabWidth + tabSpacing)
			val button = Button.builder(Component.translatable(section.labelKey)) {
				activeTab = section
				clearWidgets()
				init(width, height)
			}.bounds(tabX, tabBarY, tabWidth, TAB_BAR_HEIGHT).build()

			button.active = activeTab != section
			addRenderableWidget(button)
		}

		val listTop = HEADER_TOP + HEADER_HEIGHT + TAB_BAR_GAP + TAB_BAR_HEIGHT + TAB_BAR_GAP
		val listHeight = height - listTop - BOTTOM_BAR_HEIGHT

		sectionList = SectionListWidget(width, listHeight, listTop, activeTab)
		addRenderableWidget(sectionList!!)
	}

	private fun returnToParent() {
		minecraft.setScreen(parent)
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		if (ServerListApi.loginSession == null) {
			if (ServerListApi.persistSessionCookie) {
				val disclaimer = Component.translatable("officialserverlist.label.remember_me_disclaimer")
				val maxWidth = 300
				var dy = height / 2 + 42

				for (line in font.split(disclaimer, maxWidth)) {
					val lineWidth = font.width(line)
					graphics.text(font, line, width / 2 - lineWidth / 2, dy, 0xFFFFAA55.toInt(), false)
					dy += font.lineHeight + 1
				}
			}
		} else {
			drawHeader(graphics, ServerListApi.loginSession!!)
		}

		super.extractRenderState(graphics, mouseX, mouseY, delta)
	}

	private fun drawHeader(graphics: GuiGraphicsExtractor, loginSession: LoginSessionData) {
		val font = Minecraft.getInstance().font
		val left = SIDE_PADDING
		val y = HEADER_TOP

		val iconSize = HEADER_HEIGHT - 8
		val iconX = left
		val iconY = y + 4

		val userImage = if (loginSession.platformImg != null) {
			ImageLoader.fromBytes(loginSession.platformImg, Base64.getDecoder().decode(loginSession.platformImg))
		} else {
			ImageLoader.get(DEFAULT_USER_IMAGE)
		}

		if (userImage != null) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, userImage.id, iconX, iconY, 0f, 0f, iconSize, iconSize, iconSize, iconSize)
		} else {
			graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF333333.toInt())
		}

		val textX = iconX + iconSize + 8
		val bottomRowY = HEADER_TOP + HEADER_HEIGHT - HEADER_BUTTON_HEIGHT - 4
		val topRowY = bottomRowY - HEADER_BUTTON_HEIGHT - HEADER_BUTTON_GAP
		val textY = topRowY + (HEADER_BUTTON_HEIGHT - font.lineHeight) / 2
		val displayName = if (loginSession.platformUserName != null) loginSession.platformUserName else loginSession.email

		graphics.text(font, Component.translatable("officialserverlist.label.logged_in_as", displayName).string, textX, textY, 0xFFFFFFFF.toInt(), true)
	}

	override fun onClose() {
		returnToParent()
	}

	private inner class SectionListWidget(width: Int, height: Int, y: Int, private val tab: Tab) : ObjectSelectionList<SectionListWidget.SectionEntry>(minecraft, width, height, y, computeHeight(tab, upcomingEvents)) {
		init {
			addEntry(SectionEntry())
		}

		override fun getRowWidth(): Int = width - 20
		override fun scrollBarX(): Int = width - 6
		override fun setSelected(entry: SectionEntry?) {
			// * No-op
		}

		inner class SectionEntry() : ObjectSelectionList.Entry<SectionEntry>() {
			private val animationStart = System.currentTimeMillis()

			override fun extractContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float) {
				val font = Minecraft.getInstance().font
				val padding = 8
				val left = x + padding
				val top = y + padding
				val rowWidth = width - padding * 2

				when (tab) {
					Tab.FAVORITE_SERVERS -> renderFavoriteServersTab(graphics, font, left, top, rowWidth)
					Tab.YOUR_SERVERS -> rendeManagedServersTab(graphics, font, left, top, rowWidth)
					Tab.FAVORITE_EVENTS -> renderFavoriteEventsTab(graphics, font, left, top, rowWidth)
					Tab.UPCOMING_EVENTS -> renderUpcomingEventsTab(graphics, font, left, top, rowWidth)
				}
			}

			override fun getNarration(): Component = Component.literal("")

			private fun renderFavoriteServersTab(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, left: Int, top: Int, rowWidth: Int) {
				var y = top

				if (favoriteServers!!.isEmpty()) {
					graphics.text(font, Component.translatable("officialserverlist.empty.no_favorite_servers").string, left, y, 0xFFAAAAAA.toInt(), false)
					return
				}

				val cardHeight = 60
				val cardGap = 8

				for (server in favoriteServers!!) {
					drawFavoritedServerCard(graphics, font, server, left, y, rowWidth, cardHeight)
					y += cardHeight + cardGap
				}
			}

			private fun rendeManagedServersTab(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, left: Int, top: Int, rowWidth: Int) {
				var y = top

				if (managedServers!!.data.isEmpty()) {
					graphics.text(font, Component.translatable("officialserverlist.empty.no_managed_servers").string, left, y, 0xFFAAAAAA.toInt(), false)
					return
				}

				val cardHeight = 60
				val cardGap = 8

				for (server in managedServers!!.data) {
					drawManagedServerCard(graphics, font, server, left, y, rowWidth, cardHeight)
					y += cardHeight + cardGap
				}
			}

			private fun renderFavoriteEventsTab(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, left: Int, top: Int, rowWidth: Int) {
				var y = top

				if (favoriteEvents!!.isEmpty()) {
					graphics.text(font, Component.translatable("officialserverlist.empty.no_favorite_events").string, left, y, 0xFFAAAAAA.toInt(), false)
					return
				}

				val cardHeight = 60
				val cardGap = 8

				for (event in favoriteEvents!!) {
					drawFavoritedEventCard(graphics, font, event, left, y, rowWidth, cardHeight)
					y += cardHeight + cardGap
				}
			}

			private fun renderUpcomingEventsTab(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, left: Int, top: Int, rowWidth: Int) {
				var y = top

				if (upcomingEvents!!.isEmpty()) {
					graphics.text(font, Component.translatable("officialserverlist.empty.no_upcoming_events").string, left, y, 0xFFAAAAAA.toInt(), false)
					return
				}

				val cardHeight = 60
				val cardGap = 8

				for (event in upcomingEvents!!) {
					drawUpcomingEventCard(graphics, font, event, left, y, rowWidth, cardHeight)
					y += cardHeight + cardGap
				}
			}

			// TODO - These card drawing functions are almost identcal to each other, merge them?

			private fun drawFavoritedServerCard(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, server: FavoritedServer, left: Int, top: Int, rowWidth: Int, cardHeight: Int) {
				// TODO - Make this be more like the server list cards?

				val bg = server.backgroundImage?.url?.let { ImageLoader.get(it) }
				if (bg != null) {
					graphics.blit(RenderPipelines.GUI_TEXTURED, bg.id, left, top, 0f, 0f, rowWidth, cardHeight, rowWidth, cardHeight)
					graphics.fill(left, top, left + rowWidth, top + cardHeight, 0xAA000000.toInt())
				} else {
					graphics.fill(left, top, left + rowWidth, top + cardHeight, 0xFF1A1A1A.toInt())
				}

				val iconSize = 48
				val iconX = left + 8
				val iconY = top + (cardHeight - iconSize) / 2
				val icon = ImageLoader.get(server.iconImage.url)
				if (icon != null) {
					graphics.blit(RenderPipelines.GUI_TEXTURED, icon.id, iconX, iconY, 0f, 0f, iconSize, iconSize, iconSize, iconSize)
				} else {
					graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF333333.toInt())
				}

				val textX = iconX + iconSize + 8
				val textRight = left + rowWidth - 8
				val textWidth = textRight - textX
				var ly = iconY

				val title = truncateToWidth(font, TextUtils.sanitize(server.name), textWidth)
				graphics.text(font, title, textX, ly, 0xFFFFFFFF.toInt(), true)
				ly += font.lineHeight + 2

				val description = TextUtils.sanitize(server.shortDescription ?: "")
				if (description.isNotEmpty()) {
					val descTruncated = truncateToWidth(font, description, textWidth)
					graphics.text(font, descTruncated, textX, ly, 0xFFAAAAAA.toInt(), false)
					ly += font.lineHeight + 6
				}

				val statusText = if (server.isOnline) Component.translatable("officialserverlist.label.online").string else Component.translatable("officialserverlist.label.offline").string
				val statusColor = if (server.isOnline) 0xFF55FF55.toInt() else 0xFFFF5555.toInt()
				graphics.text(font, "$statusText  ${server.currentOnlinePlayers}/${server.currentMaxPlayers}", textX, ly, statusColor, false)
			}

			private fun drawManagedServerCard(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, server: BasicServerInfo, left: Int, top: Int, rowWidth: Int, cardHeight: Int) {
				// TODO - Make this be more like the server list cards?

				val bg = server.backgroundImage?.url?.let { ImageLoader.get(it) }
				if (bg != null) {
					graphics.blit(RenderPipelines.GUI_TEXTURED, bg.id, left, top, 0f, 0f, rowWidth, cardHeight, rowWidth, cardHeight)
					graphics.fill(left, top, left + rowWidth, top + cardHeight, 0xAA000000.toInt())
				} else {
					graphics.fill(left, top, left + rowWidth, top + cardHeight, 0xFF1A1A1A.toInt())
				}

				val iconSize = 48
				val iconX = left + 8
				val iconY = top + (cardHeight - iconSize) / 2
				val icon = ImageLoader.get(server.iconImage.url)
				if (icon != null) {
					graphics.blit(RenderPipelines.GUI_TEXTURED, icon.id, iconX, iconY, 0f, 0f, iconSize, iconSize, iconSize, iconSize)
				} else {
					graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF333333.toInt())
				}

				val textX = iconX + iconSize + 8
				val textRight = left + rowWidth - 8
				val textWidth = textRight - textX
				var ly = iconY

				val title = truncateToWidth(font, TextUtils.sanitize(server.name), textWidth)
				graphics.text(font, title, textX, ly, 0xFFFFFFFF.toInt(), true)
				ly += font.lineHeight + 2

				val description = TextUtils.sanitize(server.shortDescription ?: "")
				if (description.isNotEmpty()) {
					val descTruncated = truncateToWidth(font, description, textWidth)
					graphics.text(font, descTruncated, textX, ly, 0xFFAAAAAA.toInt(), false)
					ly += font.lineHeight + 6
				}

				val statusText = if (server.isOnline) Component.translatable("officialserverlist.label.online").string else Component.translatable("officialserverlist.label.offline").string
				val statusColor = if (server.isOnline) 0xFF55FF55.toInt() else 0xFFFF5555.toInt()
				graphics.text(font, "$statusText  ${server.currentOnlinePlayers}/${server.currentMaxPlayers}", textX, ly, statusColor, false)
			}

			private fun drawFavoritedEventCard(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, event: ServerEvent, left: Int, top: Int, rowWidth: Int, cardHeight: Int) {
				val bg = ImageLoader.get(event.backgroundImage.url)
				if (bg != null) {
					graphics.blit(RenderPipelines.GUI_TEXTURED, bg.id, left, top, 0f, 0f, rowWidth, cardHeight, rowWidth, cardHeight)
					graphics.fill(left, top, left + rowWidth, top + cardHeight, 0xAA000000.toInt())
				} else {
					graphics.fill(left, top, left + rowWidth, top + cardHeight, 0xFF1A1A1A.toInt())
				}

				val iconSize = 48
				val iconX = left + 8
				val iconY = top + (cardHeight - iconSize) / 2
				val icon = ImageLoader.get(event.iconImage.url)
				if (icon != null) {
					graphics.blit(RenderPipelines.GUI_TEXTURED, icon.id, iconX, iconY, 0f, 0f, iconSize, iconSize, iconSize, iconSize)
				} else {
					graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF333333.toInt())
				}

				val textX = iconX + iconSize + 8
				val textRight = left + rowWidth - 8
				val textWidth = textRight - textX
				var ly = iconY

				val title = truncateToWidth(font, TextUtils.sanitize(event.title), textWidth)
				graphics.text(font, title, textX, ly, 0xFFFFFFFF.toInt(), true)
				ly += font.lineHeight + 2

				var pillX = textX
				val typeLabel = TextUtils.eventTypeLabel(event.eventType.name)
				val typeWidth = font.width(typeLabel) + 8
				graphics.fill(pillX, ly, pillX + typeWidth, ly + font.lineHeight + 2, 0xFF55AA55.toInt())
				graphics.text(font, typeLabel, pillX + 4, ly + 2, 0xFFFFFFFF.toInt(), false)
				pillX += typeWidth + 4

				val joinLabel = TextUtils.joinTypeLabel(event.joinType.name)
				val joinWidth = font.width(joinLabel) + 8
				val joinBg = when (event.joinType) {
					EventJoinType.PUBLIC -> 0xFF3A6FA0.toInt()
					EventJoinType.REGISTER -> 0xFFB08030.toInt()
					EventJoinType.INVITE_ONLY -> 0xFFA03030.toInt()
				}
				graphics.fill(pillX, ly, pillX + joinWidth, ly + font.lineHeight + 2, joinBg)
				graphics.text(font, joinLabel, pillX + 4, ly + 2, 0xFFFFFFFF.toInt(), false)
				ly += font.lineHeight + 6

				val startStr = TextUtils.formatDateTime(event.startingDate)
				val endStr = TextUtils.formatDateTime(event.endingDate)
				val dateLine = "$startStr → $endStr"
				val dateTruncated = truncateToWidth(font, dateLine, textWidth)
				graphics.text(font, dateTruncated, textX, ly, 0xFF8FBCDB.toInt(), false)
				ly += font.lineHeight + 2

				graphics.text(font, Component.translatable("officialserverlist.label.joined_count", event.eventJoinedCount).string, textX, ly, 0xFFAAAAAA.toInt(), false)
			}

			private fun drawUpcomingEventCard(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, event: BasicEventInfo, left: Int, top: Int, rowWidth: Int, cardHeight: Int) {
				graphics.fill(left, top, left + rowWidth, top + cardHeight, 0xFF1A1A1A.toInt())

				val iconSize = 48
				val iconX = left + 8
				val iconY = top + (cardHeight - iconSize) / 2
				val icon = ImageLoader.get(UPCOMING_EVENT_ICON)
				if (icon != null) {
					graphics.blit(RenderPipelines.GUI_TEXTURED, icon.id, iconX, iconY, 0f, 0f, iconSize, iconSize, iconSize, iconSize)
				} else {
					graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF333333.toInt())
				}

				val textX = iconX + iconSize + 8
				val textRight = left + rowWidth - 8
				val textWidth = textRight - textX
				var ly = iconY

				val title = truncateToWidth(font, "${TextUtils.sanitize(event.serverName)} - ${TextUtils.sanitize(event.title)}", textWidth)
				graphics.text(font, title, textX, ly, 0xFFFFFFFF.toInt(), true)
				ly += font.lineHeight + 2

				var pillX = textX
				val typeLabel = TextUtils.eventTypeLabel(event.eventType.name)
				val typeWidth = font.width(typeLabel) + 8
				graphics.fill(pillX, ly, pillX + typeWidth, ly + font.lineHeight + 2, 0xFF55AA55.toInt())
				graphics.text(font, typeLabel, pillX + 4, ly + 2, 0xFFFFFFFF.toInt(), false)
				pillX += typeWidth + 4

				val joinLabel = TextUtils.joinTypeLabel(event.joinType.name)
				val joinWidth = font.width(joinLabel) + 8
				val joinBg = when (event.joinType) {
					EventJoinType.PUBLIC -> 0xFF3A6FA0.toInt()
					EventJoinType.REGISTER -> 0xFFB08030.toInt()
					EventJoinType.INVITE_ONLY -> 0xFFA03030.toInt()
				}
				graphics.fill(pillX, ly, pillX + joinWidth, ly + font.lineHeight + 2, joinBg)
				graphics.text(font, joinLabel, pillX + 4, ly + 2, 0xFFFFFFFF.toInt(), false)
				ly += font.lineHeight + 6

				val startStr = TextUtils.formatDateTime(event.startingDate)
				val dateTruncated = truncateToWidth(font, startStr, textWidth)
				graphics.text(font, dateTruncated, textX, ly, 0xFF8FBCDB.toInt(), false)
				ly += font.lineHeight + 2

				graphics.text(font, Component.translatable("officialserverlist.label.joined_count", event.eventJoinedCount).string, textX, ly, 0xFFAAAAAA.toInt(), false)
			}

			private fun truncateToWidth(font: net.minecraft.client.gui.Font, text: String, maxWidth: Int): String {
				if (font.width(text) <= maxWidth) {
					return text
				}

				var result = text

				while (result.isNotEmpty() && font.width("$result...") > maxWidth) {
					result = result.dropLast(1)
				}

				return "$result..."
			}

			private fun drawPingPongText(graphics: GuiGraphicsExtractor, text: String, x: Int, y: Int, maxWidth: Int, color: Int) {
				val font = Minecraft.getInstance().font
				val textWidth = font.width(text)
				val scrollDistance = textWidth - maxWidth

				val pauseDuration = 1.0
				val pixelsPerSecond = 30.0
				val scrollDuration = scrollDistance / pixelsPerSecond
				val cycleDuration = (pauseDuration + scrollDuration) * 2

				val elapsed = (System.currentTimeMillis() - animationStart) / 1000.0
				val cyclePos = elapsed % cycleDuration

				val offset: Int = when {
					cyclePos < pauseDuration -> 0
					cyclePos < pauseDuration + scrollDuration -> {
						val progress = (cyclePos - pauseDuration) / scrollDuration
						(progress * scrollDistance).toInt()
					}
					cyclePos < pauseDuration * 2 + scrollDuration -> scrollDistance
					else -> {
						val progress = (cyclePos - pauseDuration * 2 - scrollDuration) / scrollDuration
						(scrollDistance * (1.0 - progress)).toInt()
					}
				}

				graphics.enableScissor(x, y, x + maxWidth, y + font.lineHeight)
				graphics.text(font, text, x - offset, y, color, false)
				graphics.disableScissor()
			}
		}
	}
}

private fun computeHeight(tab: FMCSAccountScreen.Tab, cardDatas: List<Any>?): Int {
	val font = Minecraft.getInstance().font
	val padding = 8
	var height = padding * 2

	if (cardDatas.isNullOrEmpty()) {
		height += font.lineHeight
	} else {
		val cardHeight = 60
		val cardGap = 8
		height += cardDatas.size * cardHeight + (cardDatas.size - 1) * cardGap
	}

	return maxOf(height, 100)
}