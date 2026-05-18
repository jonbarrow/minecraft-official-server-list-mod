// TODO - All this UI code is very repetitive, can this be abstracted out? Maybe as a UI framework?
// TODO - I really hate the use of the term "pill" everywhere but I genuinely can't think of a better name. Rename those parts to something less stupid

package dev.jonbarrow.officialserverlist

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.LoadingDotsWidget
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

class ServerSearchFilterScreen(private val parent: Screen, private val filters: ServerSearchFilters, private val onApply: () -> Unit) : Screen(Component.literal("Filter Servers")) {
	companion object {
		private const val DEFAULT_BADGE_COLOR = 0x68EDCC
		private const val EDITION_COLOR = 0x6CC349

		private val BADGE_COLORS = mapOf(
			"4965b8ed-e84a-4313-95cd-1d456389b839" to 0x68EDCC, // * Builder's Benchmark
			"f26a9cd9-18dd-435b-a8f4-6717bc174d76" to 0x6CC349, // * Community Basics
			"53099cb6-e5d0-4300-a9c5-956731ec182b" to 0xFBDD47, // * Alpha Supporter
			"7d95345a-13a9-4970-9578-ac3ef8071cc7" to 0x00D1E8, // * Beacon of Safety
			"gamesafer" to 0x4E61F5 // * GameSafer that's manually added
		)

		private var cachedBadges: List<BadgeListBadge>? = null
		private var cachedVersions: List<TagListTag>? = null
		private var cachedLanguages: List<TagListTag>? = null
		private var cachedLocations: List<TagListTag>? = null
		private var cachedKeywords: List<TagListTag>? = null

		// * The official website creates background colors using 0.2 opacity
		// * on the same color used for the border. Emulating that here with
		// * just a lightening effect since I think the opacity effect looks
		// * bad in game
		private fun lighten(rgb: Int, factor: Float): Int {
			val r = (rgb shr 16) and 0xFF
			val g = (rgb shr 8) and 0xFF
			val b = rgb and 0xFF

			val newR = (r + ((255 - r) * factor)).toInt().coerceIn(0, 255)
			val newG = (g + ((255 - g) * factor)).toInt().coerceIn(0, 255)
			val newB = (b + ((255 - b) * factor)).toInt().coerceIn(0, 255)

			return (newR shl 16) or (newG shl 8) or newB
		}

		private fun opaque(rgb: Int): Int = (0xFF shl 24) or rgb
	}

	private var loading: Boolean = false
	private lateinit var searchBox: EditBox
	private var filterList: FilterListWidget? = null

	data class ClickableOption(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val action: () -> Unit)

	override fun init() {
		super.init()

		if (cachedBadges == null || cachedVersions == null || cachedLanguages == null || cachedLocations == null || cachedKeywords == null) {
			loadFilterOptions()
		}

		if (!loading) {
			searchBox = EditBox(font, width / 2 - 150, 44, 300, 20, Component.literal("Search"))
			searchBox.setMaxLength(100)
			searchBox.value = filters.searchPhrase
			searchBox.setResponder { filters.searchPhrase = it }
			addRenderableWidget(searchBox)

			val listTop = 72
			val listHeight = height - listTop - 40
			filterList = FilterListWidget(width, listHeight, listTop)
			addRenderableWidget(filterList!!)
		} else if (loading) {
			val widget = LoadingDotsWidget(font, Component.literal("Loading filter options"))
			widget.setPosition(width / 2 - widget.width / 2, height / 2 - 10)
			addRenderableWidget(widget)
		}

		val buttonWidth = 100
		val spacing = 4
		val totalWidth = buttonWidth * 3 + spacing * 2
		val startX = width / 2 - totalWidth / 2

		addRenderableWidget(
			Button.builder(Component.literal("Reset")) {
				filters.reset()
				searchBox.value = ""
				clearWidgets()
				init(width, height)
			}.bounds(startX, height - 28, buttonWidth, 20).build()
		)

		addRenderableWidget(
			Button.builder(Component.literal("Apply")) {
				filters.pageNumber = 0
				onApply()
				minecraft.setScreen(parent)
			}.bounds(startX + buttonWidth + spacing, height - 28, buttonWidth, 20).build()
		)

		addRenderableWidget(
			Button.builder(Component.translatable("gui.cancel")) {
				minecraft.setScreen(parent)
			}.bounds(startX + (buttonWidth + spacing) * 2, height - 28, buttonWidth, 20).build()
		)
	}

	private fun loadFilterOptions() {
		loading = true

		CompletableFuture.supplyAsync {
			val badges = ServerListApi.fetchBadges().getOrNull()
			val versions = ServerListApi.fetchTags("VERSION").getOrNull()
			val languages = ServerListApi.fetchTags("LANGUAGE").getOrNull()
			val locations = ServerListApi.fetchTags("LOCATION").getOrNull()
			val keywords = ServerListApi.fetchTags("KEYWORD").getOrNull()

			listOf(badges, versions, languages, locations, keywords)
		}.thenAccept { results ->
			minecraft.execute {
				@Suppress("UNCHECKED_CAST")
				cachedBadges = results[0] as List<BadgeListBadge>
				@Suppress("UNCHECKED_CAST")
				cachedVersions = results[1] as List<TagListTag>
				@Suppress("UNCHECKED_CAST")
				cachedLanguages = results[2] as List<TagListTag>
				@Suppress("UNCHECKED_CAST")
				cachedLocations = results[3] as List<TagListTag>
				@Suppress("UNCHECKED_CAST")
				cachedKeywords = results[4] as List<TagListTag>
				loading = false
				clearWidgets()
				init(width, height)
			}
		}
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		if (event.button() == 0) {
			val mx = event.x().toInt()
			val my = event.y().toInt()

			filterList?.let { list ->
				val listTop = list.y
				val listBottom = list.y + list.height

				if (my in listTop..listBottom) {
					for (pill in list.visiblePills) {
						if (mx in pill.x1..pill.x2 && my in pill.y1..pill.y2) {
							pill.action()
							return true
						}
					}
				}
			}
		}

		return super.mouseClicked(event, doubleClick)
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		super.extractRenderState(graphics, mouseX, mouseY, delta)

		val titleWidth = font.width(title)
		graphics.text(font, title.string, width / 2 - titleWidth / 2, 16, 0xFFFFFFFF.toInt(), true)
	}

	override fun onClose() {
		minecraft.setScreen(parent)
	}

	private inner class FilterListWidget(width: Int, height: Int, y: Int) : ObjectSelectionList<FilterListWidget.ContentEntry>(minecraft, width, height, y, computeContentHeight(width)) {
		val visiblePills = mutableListOf<ClickableOption>()

		init {
			addEntry(ContentEntry())
		}

		override fun getRowWidth(): Int = width - 20
		override fun scrollBarX(): Int = width - 6
		override fun setSelected(entry: ContentEntry?) {
			// * No-op
		}

		inner class ContentEntry : ObjectSelectionList.Entry<ContentEntry>() {
			override fun extractContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float) {
				visiblePills.clear()

				val font = Minecraft.getInstance().font
				val padding = 8
				val left = x + padding
				val top = y + padding
				val rowWidth = width - padding * 2
				var y = top

				y = drawSectionHeader(graphics, font, "Edition", left, y, rowWidth)
				y = drawEditionToggle(graphics, font, left, y, rowWidth)
				y += 12

				y = drawSectionHeader(graphics, font, "Official Experiences", left, y, rowWidth)
				y = drawExperienceToggle(graphics, font, left, y, rowWidth)
				y += 12

				cachedBadges?.let { allBadges ->
					val visible = allBadges.filter { it.badgeType != "GAMERSAFER" } + filters.gameSaferBadge
					y = drawSectionHeader(graphics, font, "Badges", left, y, rowWidth)
					y = drawBadgeFilters(graphics, font, visible, filters.selectedBadgeIDs, left, y, rowWidth)
					y += 12
				}

				cachedVersions?.let { versions ->
					y = drawSectionHeader(graphics, font, "Versions", left, y, rowWidth)
					y = drawTextOptions(graphics, font, versions.map { it.id to it.name }, filters.selectedVersionIDs, left, y, rowWidth)
					y += 12
				}

				cachedLanguages?.let { languages ->
					y = drawSectionHeader(graphics, font, "Languages", left, y, rowWidth)
					y = drawTextOptions(graphics, font, languages.map { it.id to it.name }, filters.selectedLanguageIDs, left, y, rowWidth)
					y += 12
				}

				cachedLocations?.let { locations ->
					y = drawSectionHeader(graphics, font, "Locations", left, y, rowWidth)
					y = drawTextOptions(graphics, font, locations.map { it.id to it.name }, filters.selectedLocationIDs, left, y, rowWidth)
					y += 12
				}

				cachedKeywords?.let { keywords ->
					y = drawSectionHeader(graphics, font, "Keywords", left, y, rowWidth)
					y = drawTextOptions(graphics, font, keywords.map { it.id to it.name }, filters.selectedKeywordIDs, left, y, rowWidth)
					y += 12
				}

				y = drawSectionHeader(graphics, font, "Server Size", left, y, rowWidth)
				drawTextOptions(graphics, font, ServerSearchFilters.PlayerCountOption.ALL.map { it.queryValue to it.displayName }, filters.selectedPlayerCounts, left, y, rowWidth)
			}

			override fun getNarration(): Component = Component.literal("Filter options")

			private fun drawSectionHeader(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, title: String, x: Int, y: Int, width: Int): Int {
				graphics.text(font, title, x, y, 0xFFFFFFFF.toInt(), true)
				graphics.fill(x, y + font.lineHeight + 1, x + width, y + font.lineHeight + 2, 0xFF555555.toInt())

				return y + font.lineHeight + 6
			}

			private fun drawClickableOptionBackground(graphics: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, isSelected: Boolean, color: Int) {
				val bg = if (isSelected) {
					opaque(lighten(color, 0.35f))
				} else {
					0xFF3A3A3A.toInt()
				}

				graphics.fill(x1, y1, x2, y2, bg)

				if (isSelected) {
					val border = opaque(color)

					graphics.fill(x1, y1, x2, y1 + 1, border)
					graphics.fill(x1, y2 - 1, x2, y2, border)
					graphics.fill(x1, y1, x1 + 1, y2, border)
					graphics.fill(x2 - 1, y1, x2, y2, border)
				}
			}

			private fun drawEditionToggle(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, x: Int, y: Int, width: Int): Int {
				val height = 28
				val spacing = 8
				val itemWidth = (width - spacing) / 2
				val iconSize = 18
				val iconLabelGap = 6

				for ((index, edition) in ServerSearchFilters.Edition.entries.withIndex()) {
					val itemX = x + index * (itemWidth + spacing)
					val isSelected = filters.edition == edition

					drawClickableOptionBackground(graphics, itemX, y, itemX + itemWidth, y + height, isSelected, EDITION_COLOR)

					val labelWidth = font.width(edition.displayName)
					val contentWidth = iconSize + iconLabelGap + labelWidth
					val contentStart = itemX + (itemWidth - contentWidth) / 2

					val iconY = y + (height - iconSize) / 2
					val iconTexture = ImageLoader.get(edition.iconUrl)
					if (iconTexture != null) {
						graphics.blit(RenderPipelines.GUI_TEXTURED, iconTexture.id, contentStart, iconY, 0f, 0f, iconSize, iconSize, iconSize, iconSize)
					} else {
						graphics.fill(contentStart, iconY, contentStart + iconSize, iconY + iconSize, 0xFF555555.toInt())
					}

					graphics.text(font, edition.displayName, contentStart + iconSize + iconLabelGap, y + (height - font.lineHeight) / 2, 0xFFFFFFFF.toInt(), true)

					visiblePills.add(ClickableOption(itemX, y, itemX + itemWidth, y + height) {
						filters.edition = edition
					})
				}

				return y + height
			}

			private fun drawExperienceToggle(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, x: Int, y: Int, width: Int): Int {
				val checkboxSize = 12
				val bg = if (filters.hasExperienceID) 0xFF55AA55.toInt() else 0xFF3A3A3A.toInt()

				graphics.fill(x, y, x + checkboxSize, y + checkboxSize, bg)

				if (filters.hasExperienceID) {
					graphics.fill(x + 3, y + 3, x + checkboxSize - 3, y + checkboxSize - 3, 0xFFFFFFFF.toInt())
				}

				graphics.text(font, "Show Only Official Minecraft Experiences", x + checkboxSize + 6, y + (checkboxSize - font.lineHeight) / 2 + 1, 0xFFDDDDDD.toInt(), false)

				visiblePills.add(ClickableOption(x, y, x + width, y + checkboxSize) {
					filters.hasExperienceID = !filters.hasExperienceID
				})

				return y + checkboxSize + 4
			}

			private fun drawBadgeFilters(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, badges: List<BadgeListBadge>, selected: MutableSet<String>, x: Int, y: Int, maxWidth: Int): Int {
				var currentX = x
				var currentY = y
				val iconSize = 16
				val pillHeight = maxOf(iconSize, font.lineHeight) + 8
				val pillPadding = 6
				val iconLabelGap = 5

				for (badge in badges) {
					val labelWidth = font.width(badge.name)
					val pillWidth = pillPadding + iconSize + iconLabelGap + labelWidth + pillPadding

					if (currentX + pillWidth > x + maxWidth) {
						currentX = x
						currentY += pillHeight + 3
					}

					val isSelected = selected.contains(badge.id)
					val baseColor = BADGE_COLORS[badge.id] ?: DEFAULT_BADGE_COLOR

					drawClickableOptionBackground(graphics, currentX, currentY, currentX + pillWidth, currentY + pillHeight, isSelected, baseColor)

					val iconX = currentX + pillPadding
					val iconY = currentY + (pillHeight - iconSize) / 2
					val iconTexture = ImageLoader.get(badge.iconUrl)
					if (iconTexture != null) {
						graphics.blit(RenderPipelines.GUI_TEXTURED, iconTexture.id, iconX, iconY, 0f, 0f, iconSize, iconSize, iconSize, iconSize)
					} else {
						graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF555555.toInt())
					}

					val labelX = iconX + iconSize + iconLabelGap
					val labelY = currentY + (pillHeight - font.lineHeight) / 2
					graphics.text(font, badge.name, labelX, labelY, 0xFFFFFFFF.toInt(), isSelected)

					visiblePills.add(ClickableOption(currentX, currentY, currentX + pillWidth, currentY + pillHeight) {
						if (isSelected) selected.remove(badge.id) else selected.add(badge.id)
					})

					currentX += pillWidth + 4
				}

				return currentY + pillHeight
			}

			private fun drawTextOptions(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, items: List<Pair<String, String>>, selected: MutableSet<String>, x: Int, y: Int, maxWidth: Int): Int {
				var currentX = x
				var currentY = y
				val pillHeight = font.lineHeight + 6
				val pillPadding = 6

				for ((id, label) in items) {
					val pillWidth = font.width(label) + pillPadding * 2
					if (currentX + pillWidth > x + maxWidth) {
						currentX = x
						currentY += pillHeight + 3
					}

					val isSelected = selected.contains(id)

					drawClickableOptionBackground(graphics, currentX, currentY, currentX + pillWidth, currentY + pillHeight, isSelected, EDITION_COLOR)

					graphics.text(font, label, currentX + pillPadding, currentY + 3, 0xFFFFFFFF.toInt(), isSelected)

					visiblePills.add(ClickableOption(currentX, currentY, currentX + pillWidth, currentY + pillHeight) {
						if (isSelected) selected.remove(id) else selected.add(id)
					})

					currentX += pillWidth + 4
				}

				return currentY + pillHeight
			}
		}
	}

	private fun computeContentHeight(listWidth: Int): Int {
		val font = Minecraft.getInstance().font
		val padding = 8
		val rowWidth = listWidth - 36
		var height = padding * 2

		height += font.lineHeight + 6 + 28 + 12
		height += font.lineHeight + 6 + 16 + 12

		cachedBadges?.let { all ->
			val visible = all.filter { it.badgeType != "GAMERSAFER" } + filters.gameSaferBadge
			height += font.lineHeight + 6
			height += estimateBadgePillsHeight(font, visible, rowWidth) + 12
		}
		cachedVersions?.let {
			height += font.lineHeight + 6
			height += estimatePillsHeight(font, it.map { v -> v.name }, rowWidth) + 12
		}
		cachedLanguages?.let {
			height += font.lineHeight + 6
			height += estimatePillsHeight(font, it.map { v -> v.name }, rowWidth) + 12
		}
		cachedLocations?.let {
			height += font.lineHeight + 6
			height += estimatePillsHeight(font, it.map { v -> v.name }, rowWidth) + 12
		}
		cachedKeywords?.let {
			height += font.lineHeight + 6
			height += estimatePillsHeight(font, it.map { v -> v.name }, rowWidth) + 12
		}

		height += font.lineHeight + 6
		height += estimatePillsHeight(font, ServerSearchFilters.PlayerCountOption.ALL.map { it.displayName }, rowWidth)

		return maxOf(height, 100)
	}

	private fun estimatePillsHeight(font: net.minecraft.client.gui.Font, labels: List<String>, maxWidth: Int): Int {
		val pillHeight = font.lineHeight + 6
		val pillPadding = 6
		var rows = 1
		var currentX = 0

		for (label in labels) {
			val pillWidth = font.width(label) + pillPadding * 2

			if (currentX + pillWidth > maxWidth) {
				rows++
				currentX = 0
			}

			currentX += pillWidth + 4
		}

		return rows * (pillHeight + 3)
	}

	private fun estimateBadgePillsHeight(font: net.minecraft.client.gui.Font, badges: List<BadgeListBadge>, maxWidth: Int): Int {
		val iconSize = 16
		val pillHeight = maxOf(iconSize, font.lineHeight) + 8
		val pillPadding = 6
		val iconLabelGap = 5
		var rows = 1
		var currentX = 0

		for (badge in badges) {
			val pillWidth = pillPadding + iconSize + iconLabelGap + font.width(badge.name) + pillPadding

			if (currentX + pillWidth > maxWidth) {
				rows++
				currentX = 0
			}

			currentX += pillWidth + 4
		}

		return rows * (pillHeight + 3)
	}
}