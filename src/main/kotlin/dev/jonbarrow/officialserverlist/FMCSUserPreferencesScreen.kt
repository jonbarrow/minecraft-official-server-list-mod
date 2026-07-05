// TODO - All this UI code is very repetitive, can this be abstracted out? Maybe as a UI framework?

package dev.jonbarrow.officialserverlist

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.LoadingDotsWidget
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

class FMCSUserPreferencesScreen(private val parent: Screen, private val currentPreferences: UserPreferences, private val onSave: (UpdateUserPreferencesPayload) -> Unit) : Screen(Component.translatable("officialserverlist.screen.user_preferences.title")) {
	companion object {
		private const val OPTION_COLOR = 0x6CC349

		private var cachedLanguages: List<TagListTag>? = null

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

	enum class PlatformPreference(val displayNameKey: String, val apiValue: String) {
		JAVA("officialserverlist.platform.java", "JAVA"),
		BEDROCK("officialserverlist.platform.bedrock", "BEDROCK"),
		BOTH("officialserverlist.platform.both", "BOTH");

		companion object {
			fun fromApiValue(value: String?): PlatformPreference = entries.firstOrNull { it.apiValue == value } ?: BOTH
		}
	}

	private var loading: Boolean = false
	private var preferenceList: PreferenceListWidget? = null
	private var platformPreference: PlatformPreference = PlatformPreference.fromApiValue(currentPreferences.platform)
	private var sortPreference: ServerSearchFilters.SortOption = ServerSearchFilters.SortOption.entries.firstOrNull { it.queryValue == currentPreferences.sortPreference } ?: ServerSearchFilters.SortOption.DEFAULT
	private val selectedLanguageIDs: MutableSet<String> = (currentPreferences.languagesPreferences ?: emptyList()).map { it.id }.toMutableSet()

	data class ClickableOption(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val action: () -> Unit)

	override fun init() {
		super.init()

		if (cachedLanguages == null) {
			loadLanguages()
		}

		val controlWidth = 300
		val controlX = width / 2 - controlWidth / 2

		if (!loading) {
			addRenderableWidget(
				CycleButton.builder<PlatformPreference>({ value -> Component.translatable(value.displayNameKey) }, platformPreference)
					.withValues(PlatformPreference.entries)
					.create(controlX, 44, controlWidth, 20, Component.translatable("officialserverlist.label.platform_preference")) { _, value ->
						platformPreference = value
					}
			)

			addRenderableWidget(
				CycleButton.builder<ServerSearchFilters.SortOption>({ value -> Component.translatable(value.displayNameKey) }, sortPreference)
					.withValues(ServerSearchFilters.SortOption.entries)
					.create(controlX, 68, controlWidth, 20, Component.translatable("officialserverlist.label.sort_preference")) { _, value ->
						sortPreference = value
					}
			)

			val listTop = 96
			val listHeight = height - listTop - 40
			preferenceList = PreferenceListWidget(width, listHeight, listTop)
			addRenderableWidget(preferenceList!!)
		} else if (loading) {
			val widget = LoadingDotsWidget(font, Component.translatable("officialserverlist.loading", Component.translatable("officialserverlist.loading_target.filter_options")))
			widget.setPosition(width / 2 - widget.width / 2, height / 2 - 10)
			addRenderableWidget(widget)
		}

		val buttonWidth = 100
		val spacing = 4
		val totalWidth = buttonWidth * 2 + spacing
		val startX = width / 2 - totalWidth / 2

		addRenderableWidget(
			Button.builder(Component.translatable("officialserverlist.button.user_preferences_save")) { _ ->
				minecraft.execute {
					onSave(UpdateUserPreferencesPayload(
						allowSwearing = null,
						platform = platformPreference.apiValue,
						playerNewsletter = null,
						serverNewsletter = null,
						sortPreference = sortPreference.queryValue,
						keywords = selectedLanguageIDs.toList()
					))
					minecraft.setScreen(parent)
				}
			}.bounds(startX, height - 28, buttonWidth, 20).build()
		)

		addRenderableWidget(
			Button.builder(Component.translatable("gui.cancel")) {
				minecraft.setScreen(parent)
			}.bounds(startX + buttonWidth + spacing, height - 28, buttonWidth, 20).build()
		)
	}

	private fun loadLanguages() {
		loading = true

		CompletableFuture.supplyAsync {
			ServerListApi.fetchTags("LANGUAGE").getOrNull()
		}.thenAccept { result ->
			minecraft.execute {
				@Suppress("UNCHECKED_CAST")
				cachedLanguages = result as List<TagListTag>
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

			preferenceList?.let { list ->
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

	private inner class PreferenceListWidget(width: Int, height: Int, y: Int) : ObjectSelectionList<PreferenceListWidget.ContentEntry>(minecraft, width, height, y, computeContentHeight(width)) {
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

				cachedLanguages?.let { languages ->
					y = drawSectionHeader(graphics, font, Component.translatable("officialserverlist.section.primary_languages").string, left, y, rowWidth)
					drawTextOptions(graphics, font, languages.map { it.id to it.name }, selectedLanguageIDs, left, y, rowWidth)
				}
			}

			override fun getNarration(): Component = Component.translatable("officialserverlist.screen.user_preferences.title")

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

					drawClickableOptionBackground(graphics, currentX, currentY, currentX + pillWidth, currentY + pillHeight, isSelected, OPTION_COLOR)

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

		cachedLanguages?.let {
			height += font.lineHeight + 6
			height += estimatePillsHeight(font, it.map { v -> v.name }, rowWidth)
		}

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
}