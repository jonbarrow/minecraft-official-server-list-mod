// TODO - All this UI code is very repetitive, can this be abstracted out? Maybe as a UI framework?
// TODO - I really hate the use of the term "pill" everywhere but I genuinely can't think of a better name. Rename those parts to something less stupid

package dev.jonbarrow.officialserverlist

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.LoadingDotsWidget
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import java.util.concurrent.CompletableFuture

class ServerDetailsScreen(private val parent: Screen, private val slug: String, initialData: ServerDetails? = null) : Screen(Component.translatable("officialserverlist.screen.server_details.title")) {
	companion object {
		private const val HEADER_TOP = 8
		private const val HEADER_HEIGHT = 70
		private const val TAB_BAR_HEIGHT = 20
		private const val TAB_BAR_GAP = 4
		private const val SIDE_PADDING = 12
		private const val DEFAULT_BANNER_URL = "https://findmcserver.com/assets/background_server_enderman.webp"

		private val LINK_SPRITE = Identifier.fromNamespaceAndPath("minecraft", "icon/link")
		private val LINK_HIGHLIGHTED_SPRITE = Identifier.fromNamespaceAndPath("minecraft", "icon/link_highlighted")
	}

	enum class Tab(val displayNameKey: String) {
		DETAILS("officialserverlist.tab.details"),
		RULES("officialserverlist.tab.rules"),
		GALLERY("officialserverlist.tab.gallery"),
		BADGES("officialserverlist.tab.badges"),
		EVENTS("officialserverlist.tab.events"),
		ESSENTIALS("officialserverlist.tab.essentials")
	}

	data class IconLink(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val url: String, val socialMedia: String? = null)
	data class EventCard(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val event: ServerEvent)

	private var details: ServerDetails? = initialData
	private var detailsFetched: Boolean = false
	private var loading: Boolean = false
	private var activeTab: Tab = Tab.DETAILS

	private var workbookQuestions: List<WorkbookQuestion>? = null
	private var workbookAnswers: List<WorkbookAnswer>? = null
	private var events: List<ServerEvent>? = null

	private val headerLinks = mutableListOf<IconLink>()
	private var detailsList: DetailsListWidget? = null

	override fun init() {
		super.init()

		if (!detailsFetched && !loading) {
			startFetch()
		}

		val tabBarY = HEADER_TOP + HEADER_HEIGHT + TAB_BAR_GAP
		val tabs = Tab.entries
		val tabBarTotalWidth = width - SIDE_PADDING * 2
		val tabSpacing = 4
		val tabWidth = (tabBarTotalWidth - tabSpacing * (tabs.size - 1)) / tabs.size

		for ((index, tab) in tabs.withIndex()) {
			val tabX = SIDE_PADDING + index * (tabWidth + tabSpacing)
			val button = Button.builder(Component.translatable(tab.displayNameKey)) {
				activeTab = tab
				if (tab == Tab.ESSENTIALS) ensureWorkbookLoaded()
				if (tab == Tab.EVENTS) ensureEventsLoaded()
				clearWidgets()
				init(width, height)
			}.bounds(tabX, tabBarY, tabWidth, TAB_BAR_HEIGHT).build()

			button.active = activeTab != tab
			addRenderableWidget(button)
		}

		val details = details
		if (details != null) {
			val listTop = tabBarY + TAB_BAR_HEIGHT + TAB_BAR_GAP
			val listHeight = height - listTop - 40
			detailsList = DetailsListWidget(width, listHeight, listTop, details, activeTab, workbookQuestions, workbookAnswers, events)
			addRenderableWidget(detailsList!!)
		} else if (loading) {
			val widget = LoadingDotsWidget(font, Component.translatable("officialserverlist.loading", Component.translatable("officialserverlist.loading_target.server_details")))
			widget.setPosition(width / 2 - widget.width / 2, height / 2 - 10)
			addRenderableWidget(widget)
		}

		addRenderableWidget(
			Button.builder(Component.translatable("gui.back")) {
				minecraft.setScreen(parent)
			}.bounds(width / 2 - 100, height - 28, 200, 20).build()
		)
	}

	private fun startFetch() {
		loading = true

		CompletableFuture.supplyAsync {
			ServerListApi.fetchServerDetails(slug)
		}.thenAccept { result ->
			minecraft.execute {
				loading = false
				detailsFetched = true
				result.onSuccess { entry ->
					details = entry
				}
				clearWidgets()
				init(width, height)
			}
		}
	}

	private fun ensureWorkbookLoaded() {
		val data = details ?: return
		if (workbookQuestions != null && workbookAnswers != null) {
			return
		}

		CompletableFuture.supplyAsync {
			val questions = ServerListApi.fetchWorkbookQuestions().getOrNull()
			val answers = ServerListApi.fetchWorkbookAnswers(data.id).getOrNull()
			questions to answers
		}.thenAccept { (questions, answers) ->
			minecraft.execute {
				workbookQuestions = questions
				workbookAnswers = answers
				clearWidgets()
				init(width, height)
			}
		}
	}

	private fun ensureEventsLoaded() {
		val data = details ?: return
		if (events != null) return

		CompletableFuture.supplyAsync {
			ServerListApi.fetchServerEvents(data.id)
		}.thenAccept { result ->
			minecraft.execute {
				result.onSuccess { response ->
					events = response.data
				}
				clearWidgets()
				init(width, height)
			}
		}
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		val mx = event.x().toInt()
		val my = event.y().toInt()

		val headerTop = HEADER_TOP
		val headerBottom = HEADER_TOP + HEADER_HEIGHT

		if (my in headerTop..headerBottom) {
			for (link in headerLinks) {
				if (mx in link.x1..link.x2 && my in link.y1..link.y2) {
					openURL(link.url, SocialMedia.fromString(link.socialMedia))
					return true
				}
			}
		}

		detailsList?.let { list ->
			val listTop = list.y
			val listBottom = list.y + list.height
			if (my in listTop..listBottom) {
				for (link in list.visibleLinks) {
					if (mx in link.x1..link.x2 && my in link.y1..link.y2) {
						openURL(link.url, SocialMedia.fromString(link.socialMedia))
						return true
					}
				}

				for (card in list.visibleEventCards) {
					if (mx in card.x1..card.x2 && my in card.y1..card.y2) {
						minecraft.setScreen(EventDetailsScreen(this, card.event.slug, null))
						return true
					}
				}
			}
		}

		return super.mouseClicked(event, doubleClick)
	}

	private fun openURL(url: String, socialMedia: SocialMedia? = null) {
		ConfirmLinkScreen.confirmLinkNow(this, normalizeURL(url, socialMedia), true)
	}

	private fun normalizeURL(rawURL: String, socialMedia: SocialMedia?): String {
		val url = rawURL.trim()
		if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:")) {
			return url
		}

		// * Some servers don't put full links for their socials, so try and clean it up as best
		// * as possible and assume the input is a username. If not, oh well
		val cleanedUsername = url.removePrefix("@").trim()
		return when (socialMedia) {
			SocialMedia.EMAIL -> "mailto:$url"
			SocialMedia.WEBSITE, SocialMedia.STORE -> if (url.contains(".")) "https://$url" else url
			SocialMedia.PATREON -> "https://patreon.com/$cleanedUsername"
			SocialMedia.TWITTER -> "https://twitter.com/$cleanedUsername"
			SocialMedia.INSTAGRAM -> "https://instagram.com/$cleanedUsername"
			SocialMedia.FACEBOOK -> "https://facebook.com/$cleanedUsername"
			SocialMedia.TIKTOK -> "https://tiktok.com/@$cleanedUsername"
			SocialMedia.TWITCH -> "https://twitch.tv/$cleanedUsername"
			SocialMedia.YOUTUBE -> "https://youtube.com/@$cleanedUsername"
			SocialMedia.BLUESKY -> "https://bsky.app/profile/$cleanedUsername"
			SocialMedia.DISCORD -> if (cleanedUsername.matches(Regex("[a-zA-Z0-9-]+"))) {
				"https://discord.gg/$cleanedUsername"
			} else "https://$cleanedUsername"
			SocialMedia.TEAMSPEAK -> "ts3server://$cleanedUsername"
			null -> if (url.contains(".")) "https://$url" else url
		}
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		details?.let { drawHeader(graphics, it, mouseX, mouseY) }
		super.extractRenderState(graphics, mouseX, mouseY, delta)
	}

	private fun drawHeader(graphics: GuiGraphicsExtractor, data: ServerDetails, mouseX: Int, mouseY: Int) {
		headerLinks.clear()
		val left = SIDE_PADDING
		val rowWidth = width - SIDE_PADDING * 2
		val y = HEADER_TOP

		val banner = ImageLoader.get(data.backgroundImage?.url ?: DEFAULT_BANNER_URL)
		if (banner != null) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, banner.id, left, y, 0f, 0f, rowWidth, HEADER_HEIGHT, rowWidth, HEADER_HEIGHT)
			graphics.fill(left, y, left + rowWidth, y + HEADER_HEIGHT, 0xAA000000.toInt())
		} else {
			graphics.fill(left, y, left + rowWidth, y + HEADER_HEIGHT, 0xFF1A1A1A.toInt())
		}

		val iconSize = 40
		val iconX = left + 6
		val iconY = y + 6
		val icon = ImageLoader.get(data.iconImage.url)
		if (icon != null) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, icon.id, iconX, iconY, 0f, 0f, iconSize, iconSize, iconSize, iconSize)
		}

		val textX = iconX + iconSize + 6
		val textRight = left + rowWidth - 6
		var lineY = iconY

		graphics.text(font, TextUtils.sanitize(data.name), textX, lineY, 0xFFFFFFFF.toInt(), true)
		lineY += font.lineHeight + 1

		val address = data.javaAddress?.let {
			if (data.javaPort != null && data.javaPort != 25565) "$it:${data.javaPort}" else it
		} ?: Component.translatable("officialserverlist.label.no_address").string

		graphics.text(font, TextUtils.sanitize(address), textX, lineY, 0xFF8FBCDB.toInt(), true)
		lineY += font.lineHeight + 1

		val statusText = if (data.isOnline) Component.translatable("officialserverlist.label.online").string else Component.translatable("officialserverlist.label.offline").string
		val statusColor = if (data.isOnline) 0xFF55FF55.toInt() else 0xFFFF5555.toInt()

		graphics.text(font, "$statusText  ${data.currentOnlinePlayers}/${data.currentMaxPlayers}", textX, lineY, statusColor, false)
		lineY += font.lineHeight + 1

		graphics.text(font, Component.translatable("officialserverlist.label.votes_favorites", data.votes, data.favoriteCount).string, textX, lineY, 0xFFAAAAAA.toInt(), false)

		if (!data.mapLink.isNullOrBlank()) {
			val buttonSize = 20
			val buttonX = left + rowWidth - buttonSize - 4
			val buttonY = y + 4
			val hover = mouseX in buttonX..(buttonX + buttonSize) && mouseY in buttonY..(buttonY + buttonSize)
			val sprite = if (hover) LINK_HIGHLIGHTED_SPRITE else LINK_SPRITE
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, buttonX, buttonY, buttonSize, buttonSize)
			headerLinks.add(IconLink(buttonX, buttonY, buttonX + buttonSize, buttonY + buttonSize, data.mapLink))
		}

		if (data.serverMedias.isNotEmpty()) {
			val socialIconWidth = 15
			val socialIconHeight = 16
			val iconGap = 4
			var socialX = left + 6
			val socialY = iconY + iconSize + 4

			for ((index, media) in data.serverMedias.withIndex()) {
				val texture = SocialIcons.textureFor(media.social_media.name) ?: continue
				if (socialX + socialIconWidth > textRight) break

				val hover = mouseX in socialX..(socialX + socialIconWidth) && mouseY in socialY..(socialY + socialIconHeight)

				graphics.blit(RenderPipelines.GUI_TEXTURED, texture, socialX, socialY, 0f, 0f, socialIconWidth, socialIconHeight, socialIconWidth, socialIconHeight)
				if (hover) {
					graphics.fill(socialX, socialY, socialX + socialIconWidth, socialY + socialIconHeight, 0x4055FFFF)
				}

				headerLinks.add(IconLink(
					socialX, socialY,
					socialX + socialIconWidth, socialY + socialIconHeight,
					media.url, media.social_media.name
				))

				socialX += socialIconWidth + iconGap
			}
		}
	}

	override fun onClose() {
		minecraft.setScreen(parent)
	}

	private inner class DetailsListWidget(width: Int, height: Int, y: Int, private val data: ServerDetails, private val tab: Tab, private val questions: List<WorkbookQuestion>?, private val answers: List<WorkbookAnswer>?, private val events: List<ServerEvent>?) : ObjectSelectionList<DetailsListWidget.ContentEntry>(minecraft, width, height, y, computeHeight(width, data, tab, questions, answers, events)) {
		val visibleLinks = mutableListOf<IconLink>()
		val visibleEventCards = mutableListOf<EventCard>()

		init {
			addEntry(ContentEntry(data))
		}

		override fun getRowWidth(): Int = width - 20
		override fun scrollBarX(): Int = width - 6
		override fun setSelected(entry: ContentEntry?) {
			// * No-op
		}

		inner class ContentEntry(val data: ServerDetails) : ObjectSelectionList.Entry<ContentEntry>() {
			override fun extractContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float) {
				val font = Minecraft.getInstance().font
				val padding = 8
				val left = x + padding
				val top = y + padding
				val rowWidth = width - padding * 2

				visibleLinks.clear()
				visibleEventCards.clear()

				when (tab) {
					Tab.DETAILS -> renderDetailsTab(graphics, font, left, top, rowWidth, mouseX, mouseY)
					Tab.RULES -> renderRulesTab(graphics, font, left, top, rowWidth, mouseX, mouseY)
					Tab.GALLERY -> renderGalleryTab(graphics, font, left, top, rowWidth)
					Tab.BADGES -> renderBadgesTab(graphics, font, left, top, rowWidth)
					Tab.EVENTS -> renderEventsTab(graphics, font, left, top, rowWidth)
					Tab.ESSENTIALS -> renderEssentialsTab(graphics, font, left, top, rowWidth)
				}
			}

			override fun getNarration(): Component = Component.literal(TextUtils.sanitize(data.name))

			private fun renderDetailsTab(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, left: Int, top: Int, rowWidth: Int, mouseX: Int, mouseY: Int) {
				var y = top

				if (!data.shortDescription.isNullOrBlank()) {
					y = drawWrappedText(graphics, font, TextUtils.sanitize(data.shortDescription), left, y, rowWidth, 0xFFCCCCCC.toInt())
					y += 12
				}

				if (data.rawMotd.isNotBlank()) {
					y = drawSectionHeader(graphics, font, Component.translatable("officialserverlist.section.motd").string, left, y, rowWidth)

					for (rawLine in data.rawMotd.split("\n")) {
						graphics.text(font, Component.literal(rawLine), left, y, 0xFFFFFFFF.toInt(), false)
						y += font.lineHeight + 1
					}

					y += 12
				}

				if (data.longDescription.isNotBlank()) {
					y = drawSectionHeader(graphics, font, Component.translatable("officialserverlist.section.about").string, left, y, rowWidth)
					y = drawMarkdown(graphics, font, data.longDescription, left, y, rowWidth, mouseX, mouseY)
					y += 12
				}

				if (data.version.isNotEmpty()) {
					y = drawSectionHeader(graphics, font, Component.translatable("officialserverlist.section.supported_versions").string, left, y, rowWidth)
					y = drawTagPillsInner(graphics, font, data.version.map { it.name }, left, y, rowWidth)
					y += 12
				}

				if (data.serverTags.isNotEmpty()) {
					y = drawSectionHeader(graphics, font, Component.translatable("officialserverlist.section.tags").string, left, y, rowWidth)
					y = drawTagPillsInner(graphics, font, data.serverTags.map { it.name }, left, y, rowWidth)
					y += 12
				}

				val meta = mutableListOf<String>()

				if (data.serverLanguage.isNotEmpty()) {
					meta.add(Component.translatable("officialserverlist.label.languages_list", data.serverLanguage.joinToString(", ") { it.name }).string)
				}

				if (data.serverLocation.isNotEmpty()) {
					meta.add(Component.translatable("officialserverlist.label.location_list", data.serverLocation.joinToString(", ") { it.name }).string)
				}

				if (data.lookupVersion != null) {
					meta.add(Component.translatable("officialserverlist.label.server_software", data.lookupVersion).string)
				}

				meta.add(Component.translatable("officialserverlist.label.launched", TextUtils.formatDate(data.launchedOn)).string)
				meta.add(Component.translatable("officialserverlist.label.claimed", TextUtils.formatDate(data.claimedOn)).string)

				if (meta.isNotEmpty()) {
					y = drawSectionHeader(graphics, font, Component.translatable("officialserverlist.section.info").string, left, y, rowWidth)

					for (line in meta) {
						graphics.text(font, line, left, y, 0xFFAAAAAA.toInt(), false)
						y += font.lineHeight + 2
					}

					y += 8
				}

				if (!data.presentationVideoUrl.isNullOrBlank()) {
					y = drawSectionHeader(graphics, font, Component.translatable("officialserverlist.section.presentation_video").string, left, y, rowWidth)
					val url = data.presentationVideoUrl
					val urlWidth = font.width(url)
					val hover = mouseX in left..(left + urlWidth) && mouseY in y..(y + font.lineHeight)
					val color = if (hover) 0xFF55FFFF.toInt() else 0xFF8FBCDB.toInt()
					graphics.text(font, url, left, y, color, false)
					visibleLinks.add(IconLink(left, y, left + urlWidth, y + font.lineHeight, url))
					y += font.lineHeight + 8
				}
			}

			private fun renderRulesTab(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, left: Int, top: Int, rowWidth: Int, mouseX: Int, mouseY: Int) {
				var y = top
				val links = mutableListOf<Pair<String, String>>()

				if (!data.codeOfConductUrl.isNullOrBlank()) links.add(Component.translatable("officialserverlist.section.code_of_conduct").string to data.codeOfConductUrl)

				data.privacyPolicyUrl?.let { links.add(Component.translatable("officialserverlist.section.privacy_policy").string to it) }
				data.termsOfServiceUrl?.let { links.add(Component.translatable("officialserverlist.section.terms_of_service").string to it) }

				if (links.isNotEmpty()) {
					y = drawSectionHeader(graphics, font, Component.translatable("officialserverlist.section.links").string, left, y, rowWidth)

					for ((label, url) in links) {
						graphics.text(font, "• $label: ", left, y, 0xFFAAAAAA.toInt(), false)
						val labelWidth = font.width("• $label: ")
						val urlWidth = font.width(url)
						val hover = mouseX in (left + labelWidth)..(left + labelWidth + urlWidth) && mouseY in y..(y + font.lineHeight)
						val color = if (hover) 0xFF55FFFF.toInt() else 0xFF8FBCDB.toInt()

						graphics.text(font, url, left + labelWidth, y, color, false)

						visibleLinks.add(IconLink(
							left + labelWidth, y,
							left + labelWidth + urlWidth, y + font.lineHeight,
							url
						))

						y += font.lineHeight + 2
					}

					y += 12
				}

				if (!data.codeOfConduct.isNullOrBlank()) {
					y = drawSectionHeader(graphics, font, Component.translatable("officialserverlist.section.code_of_conduct").string, left, y, rowWidth)
					drawMarkdown(graphics, font, data.codeOfConduct, left, y, rowWidth, mouseX, mouseY)
				} else if (links.isEmpty()) {
					graphics.text(font, Component.translatable("officialserverlist.empty.no_rules").string, left, y, 0xFFAAAAAA.toInt(), false)
				}
			}

			private fun renderGalleryTab(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, left: Int, top: Int, rowWidth: Int) {
				var y = top
				val screenshots = data.screenshotImages

				if (screenshots.isNullOrEmpty()) {
					graphics.text(font, Component.translatable("officialserverlist.empty.no_screenshots").string, left, y, 0xFFAAAAAA.toInt(), false)
					return
				}

				val columns = 3
				val gap = 6
				val cellWidth = (rowWidth - gap * (columns - 1)) / columns
				val cellHeight = (cellWidth * 9 / 16)

				for ((index, screenshot) in screenshots.withIndex()) {
					val col = index % columns
					val row = index / columns
					val cellX = left + col * (cellWidth + gap)
					val cellY = y + row * (cellHeight + gap)

					val image = ImageLoader.get(screenshot.url)
					if (image != null) {
						graphics.blit(RenderPipelines.GUI_TEXTURED, image.id, cellX, cellY, 0f, 0f, cellWidth, cellHeight, cellWidth, cellHeight)
					} else {
						graphics.fill(cellX, cellY, cellX + cellWidth, cellY + cellHeight, 0xFF333333.toInt())
						val placeholder = "..."
						val pw = font.width(placeholder)
						graphics.text(font, placeholder, cellX + cellWidth / 2 - pw / 2, cellY + cellHeight / 2 - font.lineHeight / 2, 0xFFAAAAAA.toInt(), false)
					}
				}
			}

			private fun renderBadgesTab(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, left: Int, top: Int, rowWidth: Int) {
				var y = top
				val badges = data.serverBadges

				if (badges.isNullOrEmpty()) {
					graphics.text(font, Component.translatable("officialserverlist.empty.no_badges").string, left, y, 0xFFAAAAAA.toInt(), false)
					return
				}

				for (badge in badges) {
					val badgeIcon = ImageLoader.get(badge.effectiveIconUrl)
					val iconSize = 32

					if (badgeIcon != null) {
						graphics.blit(RenderPipelines.GUI_TEXTURED, badgeIcon.id, left, y, 0f, 0f, iconSize, iconSize, iconSize, iconSize)
					} else {
						graphics.fill(left, y, left + iconSize, y + iconSize, 0xFF555555.toInt())
					}

					val textX = left + iconSize + 8
					val textWidth = rowWidth - iconSize - 8
					graphics.text(font, badge.name, textX, y, 0xFFFFAA00.toInt(), true)

					var ly = y + font.lineHeight + 2
					val descLines = wrapText(font, TextUtils.sanitize(badge.description), textWidth)

					for (line in descLines) {
						graphics.text(font, line, textX, ly, 0xFFCCCCCC.toInt(), false)
						ly += font.lineHeight + 1
					}

					y = maxOf(y + iconSize, ly) + 8
				}
			}

			private fun renderEventsTab(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, left: Int, top: Int, rowWidth: Int) {
				var y = top

				if (events == null) {
					graphics.text(font, Component.translatable("officialserverlist.empty.loading").string, left, y, 0xFFAAAAAA.toInt(), false)
					return
				}

				if (events.isEmpty()) {
					graphics.text(font, Component.translatable("officialserverlist.empty.no_events").string, left, y, 0xFFAAAAAA.toInt(), false)
					return
				}

				val cardHeight = 80
				val cardGap = 8

				for (event in events) {
					drawEventCard(graphics, font, event, left, y, rowWidth, cardHeight)
					visibleEventCards.add(EventCard(left, y, left + rowWidth, y + cardHeight, event))
					y += cardHeight + cardGap
				}
			}

			private fun drawEventCard(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, event: ServerEvent, left: Int, top: Int, rowWidth: Int, cardHeight: Int) {
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

			private fun renderEssentialsTab(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, left: Int, top: Int, rowWidth: Int) {
				var y = top
				if (questions == null || answers == null) {
					graphics.text(font, Component.translatable("officialserverlist.empty.loading").string, left, y, 0xFFAAAAAA.toInt(), false)
					return
				}

				val publicAnswers = answers.filter { it.isPublic && !it.isIncomplete }
				if (publicAnswers.isEmpty()) {
					graphics.text(font, Component.translatable("officialserverlist.empty.no_essentials").string, left, y, 0xFFAAAAAA.toInt(), false)
					return
				}

				for (question in questions.sortedBy { it.order }) {
					val answer = publicAnswers.find { it.questionId == question.id } ?: continue
					val questionLines = wrapText(font, question.question, rowWidth)

					for (line in questionLines) {
						graphics.text(font, line, left, y, 0xFFFFFFFF.toInt(), true)
						y += font.lineHeight + 1
					}

					y += 2
					y = drawWrappedText(graphics, font, answer.answer, left, y, rowWidth, 0xFFDDDDDD.toInt())
					y += 12
				}
			}

			private fun drawSectionHeader(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, title: String, x: Int, y: Int, width: Int): Int {
				graphics.text(font, title, x, y, 0xFFFFFFFF.toInt(), true)
				graphics.fill(x, y + font.lineHeight + 1, x + width, y + font.lineHeight + 2, 0xFF555555.toInt())

				return y + font.lineHeight + 6
			}

			private fun drawWrappedText(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, text: String, x: Int, y: Int, maxWidth: Int, color: Int): Int {
				var currentY = y

				for (paragraph in text.split("\n")) {
					if (paragraph.isBlank()) {
						currentY += font.lineHeight
						continue
					}

					for (line in wrapText(font, paragraph, maxWidth)) {
						graphics.text(font, line, x, currentY, color, false)
						currentY += font.lineHeight + 1
					}
				}

				return currentY
			}

			private fun drawMarkdown(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, text: String, x: Int, y: Int, maxWidth: Int, mouseX: Int, mouseY: Int): Int {
				var currentY = y

				for (block in Markdown.parse(text)) {
					when (block.type) {
						Markdown.BlockType.BLANK -> currentY += font.lineHeight / 2
						Markdown.BlockType.HEADING1 -> {
							currentY += 4
							for (line in wrapText(font, block.text, maxWidth)) {
								graphics.text(font, line, x, currentY, 0xFFFFFFFF.toInt(), true)
								currentY += font.lineHeight + 2
							}
							graphics.fill(x, currentY, x + maxWidth, currentY + 1, 0xFF666666.toInt())
							currentY += 6
						}
						Markdown.BlockType.HEADING2 -> {
							currentY += 2
							for (line in wrapText(font, block.text, maxWidth)) {
								graphics.text(font, line, x, currentY, 0xFFFFCC00.toInt(), true)
								currentY += font.lineHeight + 2
							}
							currentY += 4
						}
						Markdown.BlockType.HEADING3 -> {
							for (line in wrapText(font, block.text, maxWidth)) {
								graphics.text(font, line, x, currentY, 0xFFDDDDDD.toInt(), true)
								currentY += font.lineHeight + 1
							}
							currentY += 3
						}
						Markdown.BlockType.BULLET -> {
							val bullet = "• "
							val bulletWidth = font.width(bullet)
							val lines = wrapText(font, block.text, maxWidth - bulletWidth)
							for ((i, line) in lines.withIndex()) {
								if (i == 0) {
									graphics.text(font, bullet, x, currentY, 0xFFAAAAAA.toInt(), false)
									graphics.text(font, line, x + bulletWidth, currentY, 0xFFDDDDDD.toInt(), false)
								} else {
									graphics.text(font, line, x + bulletWidth, currentY, 0xFFDDDDDD.toInt(), false)
								}
								currentY += font.lineHeight + 1
							}
						}
						Markdown.BlockType.PARAGRAPH -> {
							for (line in wrapText(font, block.text, maxWidth)) {
								graphics.text(font, line, x, currentY, 0xFFDDDDDD.toInt(), false)
								currentY += font.lineHeight + 1
							}
							currentY += 2
						}
					}
				}

				return currentY
			}

			private fun wrapText(font: net.minecraft.client.gui.Font, text: String, maxWidth: Int): List<String> {
				val words = text.split(" ")
				val lines = mutableListOf<String>()
				var current = StringBuilder()

				for (word in words) {
					val candidate = if (current.isEmpty()) word else "$current $word"
					if (font.width(candidate) <= maxWidth) {
						if (current.isNotEmpty()) current.append(' ')
						current.append(word)
					} else {
						if (current.isNotEmpty()) lines.add(current.toString())
						current = StringBuilder(word)
					}
				}

				if (current.isNotEmpty()) lines.add(current.toString())

				return if (lines.isEmpty()) listOf("") else lines
			}

			private fun drawTagPillsInner(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, tags: List<String>, x: Int, y: Int, maxWidth: Int): Int {
				var currentX = x
				var currentY = y
				val pillHeight = font.lineHeight + 4
				val pillPadding = 4

				for (tag in tags) {
					val pillWidth = font.width(tag) + pillPadding * 2
					if (currentX + pillWidth > x + maxWidth) {
						currentX = x
						currentY += pillHeight + 2
					}

					graphics.fill(currentX, currentY, currentX + pillWidth, currentY + pillHeight, 0xFF3A3A3A.toInt())
					graphics.text(font, tag, currentX + pillPadding, currentY + 2, 0xFFDDDDDD.toInt(), false)
					currentX += pillWidth + 4
				}

				return currentY + pillHeight
			}
		}
	}
}

private fun computeHeight(listWidth: Int, data: ServerDetails, tab: ServerDetailsScreen.Tab, questions: List<WorkbookQuestion>?, answers: List<WorkbookAnswer>?, events: List<ServerEvent>?): Int {
	val font = Minecraft.getInstance().font
	val padding = 8
	val rowWidth = listWidth - 36
	var height = padding * 2

	when (tab) {
		ServerDetailsScreen.Tab.DETAILS -> {
			if (!data.shortDescription.isNullOrBlank()) {
				height += estimateWrappedHeight(font, data.shortDescription, rowWidth) + 12
			}
			if (data.rawMotd.isNotBlank()) {
				height += font.lineHeight + 6
				height += data.rawMotd.split("\n").size * (font.lineHeight + 1)
				height += 12
			}
			if (data.longDescription.isNotBlank()) {
				height += font.lineHeight + 6
				height += estimateMarkdownHeight(font, data.longDescription, rowWidth)
				height += 12
			}
			if (data.version.isNotEmpty()) {
				height += font.lineHeight + 6
				height += estimatePillHeight(font, data.version.map { it.name }, rowWidth)
				height += 12
			}
			if (data.serverTags.isNotEmpty()) {
				height += font.lineHeight + 6
				height += estimatePillHeight(font, data.serverTags.map { it.name }, rowWidth)
				height += 12
			}
			var metaLines = 0
			if (data.serverLanguage.isNotEmpty()) metaLines++
			if (data.serverLocation.isNotEmpty()) metaLines++
			if (data.lookupVersion != null) metaLines++
			metaLines += 2  // Launched + Claimed always present
			height += font.lineHeight + 6
			height += metaLines * (font.lineHeight + 2) + 8
			if (!data.presentationVideoUrl.isNullOrBlank()) {
				height += font.lineHeight + 6
				height += font.lineHeight + 8
			}
		}
		ServerDetailsScreen.Tab.RULES -> {
			var linkCount = 0
			if (!data.codeOfConductUrl.isNullOrBlank()) linkCount++
			if (data.privacyPolicyUrl != null) linkCount++
			if (data.termsOfServiceUrl != null) linkCount++
			if (linkCount > 0) {
				height += font.lineHeight + 6
				height += linkCount * (font.lineHeight + 2)
				height += 12
			}
			if (!data.codeOfConduct.isNullOrBlank()) {
				height += font.lineHeight + 6
				height += estimateMarkdownHeight(font, data.codeOfConduct, rowWidth)
			} else if (linkCount == 0) {
				height += font.lineHeight
			}
		}
		ServerDetailsScreen.Tab.GALLERY -> {
			val screenshots = data.screenshotImages
			if (screenshots.isNullOrEmpty()) {
				height += font.lineHeight
			} else {
				val columns = 3
				val gap = 6
				val cellWidth = (rowWidth - gap * (columns - 1)) / columns
				val cellHeight = cellWidth * 9 / 16
				val rows = (screenshots.size + columns - 1) / columns
				height += rows * cellHeight + (rows - 1) * gap
			}
		}
		ServerDetailsScreen.Tab.BADGES -> {
			val badges = data.serverBadges
			if (badges.isNullOrEmpty()) {
				height += font.lineHeight
			} else {
				for (badge in badges) {
					val descHeight = estimateWrappedHeight(font, badge.description, rowWidth - 40)
					height += maxOf(32, font.lineHeight + 2 + descHeight) + 8
				}
			}
		}
		ServerDetailsScreen.Tab.EVENTS -> {
			if (events == null || events.isEmpty()) {
				height += font.lineHeight
			} else {
				val cardHeight = 80
				val cardGap = 8
				height += events.size * cardHeight + (events.size - 1) * cardGap
			}
		}
		ServerDetailsScreen.Tab.ESSENTIALS -> {
			if (questions == null || answers == null) {
				height += font.lineHeight
			} else {
				val publicAnswers = answers.filter { it.isPublic && !it.isIncomplete }
				for (question in questions.sortedBy { it.order }) {
					val answer = publicAnswers.find { it.questionId == question.id } ?: continue
					height += estimateWrappedHeight(font, question.question, rowWidth) + 2
					height += estimateWrappedHeight(font, answer.answer, rowWidth) + 12
				}
			}
		}
	}

	return maxOf(height, 100)
}

private fun estimateWrappedHeight(font: net.minecraft.client.gui.Font, text: String, maxWidth: Int): Int {
	val lineHeight = font.lineHeight + 1
	var lines = 0
	for (paragraph in text.split("\n")) {
		if (paragraph.isBlank()) {
			lines++
			continue
		}

		val words = paragraph.split(" ")
		var current = StringBuilder()
		for (word in words) {
			val candidate = if (current.isEmpty()) word else "$current $word"

			if (font.width(candidate) <= maxWidth) {
				if (current.isNotEmpty()) current.append(' ')
				current.append(word)
			} else {
				if (current.isNotEmpty()) lines++
				current = StringBuilder(word)
			}
		}
		if (current.isNotEmpty()) lines++
	}

	return lines * lineHeight
}

private fun estimateMarkdownHeight(font: net.minecraft.client.gui.Font, text: String, maxWidth: Int): Int {
	var height = 0
	for (block in Markdown.parse(text)) {
		height += when (block.type) {
			Markdown.BlockType.BLANK -> font.lineHeight / 2
			Markdown.BlockType.HEADING1 -> 4 + estimateWrappedHeight(font, block.text, maxWidth) + 6 + 1
			Markdown.BlockType.HEADING2 -> 2 + estimateWrappedHeight(font, block.text, maxWidth) + 4
			Markdown.BlockType.HEADING3 -> estimateWrappedHeight(font, block.text, maxWidth) + 3
			Markdown.BlockType.BULLET -> {
				val bulletWidth = font.width("• ")
				estimateWrappedHeight(font, block.text, maxWidth - bulletWidth)
			}
			Markdown.BlockType.PARAGRAPH -> estimateWrappedHeight(font, block.text, maxWidth) + 2
		}
	}

	return height
}

private fun estimatePillHeight(font: net.minecraft.client.gui.Font, tags: List<String>, maxWidth: Int): Int {
	val pillHeight = font.lineHeight + 4
	val pillPadding = 4
	var rows = 1
	var currentX = 0

	for (tag in tags) {
		val pillWidth = font.width(tag) + pillPadding * 2
		if (currentX + pillWidth > maxWidth) {
			rows++
			currentX = 0
		}

		currentX += pillWidth + 4
	}

	return rows * (pillHeight + 2)
}