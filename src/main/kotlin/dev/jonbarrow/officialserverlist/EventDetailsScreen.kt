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
import java.util.concurrent.CompletableFuture

class EventDetailsScreen(private val parent: Screen, private val slug: String, initialData: EventDetails? = null) : Screen(Component.literal("Event Details")) {
	companion object {
		private const val SIDE_PADDING = 12
		private const val CONTENT_TOP = 8
	}

	data class Link(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val url: String)

	private var event: EventDetails? = initialData
	private var fetched: Boolean = false
	private var loading: Boolean = false
	private var errorMessage: String? = null
	private var detailsList: EventListWidget? = null

	override fun init() {
		super.init()

		if (!fetched && !loading) {
			startFetch()
		}

		val event = event
		if (event != null) {
			val listTop = CONTENT_TOP
			val listHeight = height - listTop - 40
			detailsList = EventListWidget(width, listHeight, listTop, event)
			addRenderableWidget(detailsList!!)
		} else if (loading) {
			val widget = LoadingDotsWidget(font, Component.literal("Loading event"))
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
			ServerListApi.fetchEventDetails(slug)
		}.thenAccept { result ->
			minecraft.execute {
				loading = false
				fetched = true
				result.onSuccess { entry ->
					event = entry
				}.onFailure { err ->
					errorMessage = err.message ?: "Unknown error"
				}
				clearWidgets()
				init(width, height)
			}
		}
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		val mx = event.x().toInt()
		val my = event.y().toInt()

		detailsList?.let { list ->
			val listTop = list.y
			val listBottom = list.y + list.height
			if (my in listTop..listBottom) {
				for (link in list.visibleLinks) {
					if (mx in link.x1..link.x2 && my in link.y1..link.y2) {
						openURL(link.url)
						return true
					}
				}
			}
		}

		return super.mouseClicked(event, doubleClick)
	}

	private fun openURL(url: String) {
		// * I don't think this should ever have socials/partial URLs?
		ConfirmLinkScreen.confirmLinkNow(this, url, true)
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		val bg = event?.backgroundImage?.url?.let { ImageLoader.get(it) }
		if (bg != null) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, bg.id, 0, 0, 0f, 0f, width, height, width, height)
			graphics.fill(0, 0, width, height, 0xCC000000.toInt())
		}

		super.extractRenderState(graphics, mouseX, mouseY, delta)

		errorMessage?.let { err ->
			val msg = "Error: $err"
			val w = font.width(msg)
			graphics.text(font, msg, width / 2 - w / 2, height / 2 - 30, 0xFFFF5555.toInt(), true)
		}
	}

	override fun onClose() {
		minecraft.setScreen(parent)
	}

	private inner class EventListWidget(width: Int, height: Int, y: Int, data: EventDetails) : ObjectSelectionList<EventListWidget.ContentEntry>(minecraft, width, height, y, computeEventHeight(width, data)) {
		val visibleLinks = mutableListOf<Link>()

		init {
			addEntry(ContentEntry(data))
		}

		override fun getRowWidth(): Int = width - 40
		override fun scrollBarX(): Int = width - 6
		override fun setSelected(entry: ContentEntry?) {
			// * No-op
		}

		inner class ContentEntry(val data: EventDetails) : ObjectSelectionList.Entry<ContentEntry>() {
			override fun extractContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float) {
				val font = Minecraft.getInstance().font
				val padding = 12
				val left = x + padding
				val top = y + padding
				val rowWidth = width - padding * 2

				visibleLinks.clear()
				var ly = top

				val iconSize = 32
				val titleX = left + iconSize + 8
				val titleWidth = rowWidth - iconSize - 8

				val icon = ImageLoader.get(data.iconImage.url)
				if (icon != null) {
					graphics.blit(RenderPipelines.GUI_TEXTURED, icon.id, left, ly, 0f, 0f, iconSize, iconSize, iconSize, iconSize)
				} else {
					graphics.fill(left, ly, left + iconSize, ly + iconSize, 0xFF333333.toInt())
				}

				val titleText = TextUtils.sanitize(data.title)
				val titleLines = wrapText(font, titleText, titleWidth)
				var titleLineY = ly
				for (line in titleLines) {
					graphics.text(font, line, titleX, titleLineY, 0xFFFFFFFF.toInt(), true)
					titleLineY += font.lineHeight + 2
				}
				ly = maxOf(ly + iconSize, titleLineY) + 4

				var pillX = left
				val typeLabel = TextUtils.eventTypeLabel(data.eventType.name)
				val typeWidth = font.width(typeLabel) + 10
				val pillHeight = font.lineHeight + 4

				graphics.fill(pillX, ly, pillX + typeWidth, ly + pillHeight, 0xFF55AA55.toInt())
				graphics.text(font, typeLabel, pillX + 5, ly + 3, 0xFFFFFFFF.toInt(), false)
				pillX += typeWidth + 4

				val joinLabel = TextUtils.joinTypeLabel(data.joinType.name)
				val joinWidth = font.width(joinLabel) + 10
				val joinBg = when (data.joinType) {
					EventJoinType.PUBLIC -> 0xFF3A6FA0.toInt()
					EventJoinType.REGISTER -> 0xFFB08030.toInt()
					EventJoinType.INVITE_ONLY -> 0xFFA03030.toInt()
				}

				graphics.fill(pillX, ly, pillX + joinWidth, ly + pillHeight, joinBg)
				graphics.text(font, joinLabel, pillX + 5, ly + 3, 0xFFFFFFFF.toInt(), false)
				ly += font.lineHeight + 12

				val featured = ImageLoader.get(data.featuredImage.url)
				val featuredHeight = (rowWidth * 9 / 16)
				if (featured != null) {
					graphics.blit(RenderPipelines.GUI_TEXTURED, featured.id, left, ly, 0f, 0f, rowWidth, featuredHeight, rowWidth, featuredHeight)
				} else {
					graphics.fill(left, ly, left + rowWidth, ly + featuredHeight, 0xFF333333.toInt())
					val placeholder = "Loading..."
					val pw = font.width(placeholder)
					graphics.text(font, placeholder, left + rowWidth / 2 - pw / 2, ly + featuredHeight / 2 - font.lineHeight / 2, 0xFFAAAAAA.toInt(), false)
				}

				ly += featuredHeight + 12

				ly = drawSectionHeader(graphics, font, "When", left, ly, rowWidth)
				graphics.text(font, "Starts: ${TextUtils.formatDateTime(data.startingDate)}", left, ly, 0xFFFFFFFF.toInt(), false)
				ly += font.lineHeight + 2
				graphics.text(font, "Ends: ${TextUtils.formatDateTime(data.endingDate)}", left, ly, 0xFFFFFFFF.toInt(), false)
				ly += font.lineHeight + 12

				ly = drawSectionHeader(graphics, font, "Participation", left, ly, rowWidth)
				graphics.text(font, "${data.eventJoinedCount} player${if (data.eventJoinedCount == 1) "" else "s"} joined", left, ly, 0xFFFFFFFF.toInt(), false)
				ly += font.lineHeight + 12

				if (data.longDescription.isNotBlank()) {
					ly = drawSectionHeader(graphics, font, "About This Event", left, ly, rowWidth)
					ly = drawMarkdown(graphics, font, data.longDescription, left, ly, rowWidth)
					ly += 12
				}

				if (data.prizes.isNotBlank()) {
					ly = drawSectionHeader(graphics, font, "Prizes", left, ly, rowWidth)
					ly = drawMarkdown(graphics, font, data.prizes, left, ly, rowWidth)
					ly += 12
				}

				if (!data.codeOfConduct.isNullOrBlank() || !data.codeOfConductUrl.isNullOrBlank()) {
					ly = drawSectionHeader(graphics, font, "Code of Conduct", left, ly, rowWidth)

					if (!data.codeOfConductUrl.isNullOrBlank()) {
						val url = data.codeOfConductUrl
						graphics.text(font, "Full text: ", left, ly, 0xFFAAAAAA.toInt(), false)
						val labelWidth = font.width("Full text: ")
						val urlWidth = font.width(url)
						val hover = mouseX in (left + labelWidth)..(left + labelWidth + urlWidth) && mouseY in ly..(ly + font.lineHeight)
						val color = if (hover) 0xFF55FFFF.toInt() else 0xFF8FBCDB.toInt()
						graphics.text(font, url, left + labelWidth, ly, color, false)
						visibleLinks.add(Link(left + labelWidth, ly, left + labelWidth + urlWidth, ly + font.lineHeight, url))
						ly += font.lineHeight + 8
					}

					if (!data.codeOfConduct.isNullOrBlank()) {
						ly = drawMarkdown(graphics, font, data.codeOfConduct, left, ly, rowWidth)
					}

					ly += 12
				}

				if (!data.presentationVideoUrl.isNullOrBlank()) {
					ly = drawSectionHeader(graphics, font, "Presentation Video", left, ly, rowWidth)
					val url = data.presentationVideoUrl
					val urlWidth = font.width(url)
					val hover = mouseX in left..(left + urlWidth) && mouseY in ly..(ly + font.lineHeight)
					val color = if (hover) 0xFF55FFFF.toInt() else 0xFF8FBCDB.toInt()
					graphics.text(font, url, left, ly, color, false)
					visibleLinks.add(Link(left, ly, left + urlWidth, ly + font.lineHeight, url))
					ly += font.lineHeight + 12
				}

				if (!data.linkUrl.isNullOrBlank()) {
					ly = drawSectionHeader(graphics, font, "More Info", left, ly, rowWidth)
					val url = data.linkUrl
					val urlWidth = font.width(url)
					val hover = mouseX in left..(left + urlWidth) && mouseY in ly..(ly + font.lineHeight)
					val color = if (hover) 0xFF55FFFF.toInt() else 0xFF8FBCDB.toInt()
					graphics.text(font, url, left, ly, color, false)
					visibleLinks.add(Link(left, ly, left + urlWidth, ly + font.lineHeight, url))
					ly += font.lineHeight + 12
				}

				if (data.screenshotImages.isNotEmpty()) {
					ly = drawSectionHeader(graphics, font, "Gallery", left, ly, rowWidth)
					val columns = 3
					val gap = 6
					val cellWidth = (rowWidth - gap * (columns - 1)) / columns
					val cellHeight = (cellWidth * 9 / 16)

					for ((index, screenshot) in data.screenshotImages.withIndex()) {
						val col = index % columns
						val row = index / columns
						val cellX = left + col * (cellWidth + gap)
						val cellY = ly + row * (cellHeight + gap)

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
			}

			override fun getNarration(): Component = Component.literal(TextUtils.sanitize(data.title))

			private fun drawSectionHeader(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, title: String, x: Int, y: Int, width: Int): Int {
				graphics.text(font, title, x, y, 0xFFFFD24A.toInt(), true)
				graphics.fill(x, y + font.lineHeight + 1, x + width, y + font.lineHeight + 2, 0xFF555555.toInt())

				return y + font.lineHeight + 6
			}

			private fun drawMarkdown(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, text: String, x: Int, y: Int, maxWidth: Int): Int {
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
									graphics.text(font, line, x + bulletWidth, currentY, 0xFFFFFFFF.toInt(), false)
								} else {
									graphics.text(font, line, x + bulletWidth, currentY, 0xFFFFFFFF.toInt(), false)
								}
								currentY += font.lineHeight + 1
							}
						}
						Markdown.BlockType.PARAGRAPH -> {
							for (line in wrapText(font, block.text, maxWidth)) {
								graphics.text(font, line, x, currentY, 0xFFFFFFFF.toInt(), false)
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
		}
	}
}

private fun computeEventHeight(listWidth: Int, data: EventDetails): Int {
	val font = Minecraft.getInstance().font
	val padding = 12
	val rowWidth = listWidth - 64
	var height = padding * 2

	val iconSize = 32
	val titleWidth = rowWidth - iconSize - 8
	val titleHeight = estimateWrapped(font, TextUtils.sanitize(data.title), titleWidth)
	height += maxOf(iconSize, titleHeight) + 4

	height += font.lineHeight + 16
	height += (rowWidth * 9 / 16) + 12

	height += font.lineHeight + 6 + (font.lineHeight + 2) * 2 + 12
	height += font.lineHeight + 6 + font.lineHeight + 12

	if (data.longDescription.isNotBlank()) {
		height += font.lineHeight + 6
		height += estimateMarkdown(font, data.longDescription, rowWidth) + 12
	}

	if (data.prizes.isNotBlank()) {
		height += font.lineHeight + 6
		height += estimateMarkdown(font, data.prizes, rowWidth) + 12
	}

	if (!data.codeOfConduct.isNullOrBlank() || !data.codeOfConductUrl.isNullOrBlank()) {
		height += font.lineHeight + 6
		if (!data.codeOfConductUrl.isNullOrBlank()) height += font.lineHeight + 8
		if (!data.codeOfConduct.isNullOrBlank()) height += estimateMarkdown(font, data.codeOfConduct, rowWidth)
		height += 12
	}

	if (!data.presentationVideoUrl.isNullOrBlank()) {
		height += font.lineHeight + 6 + font.lineHeight + 12
	}

	if (!data.linkUrl.isNullOrBlank()) {
		height += font.lineHeight + 6 + font.lineHeight + 12
	}

	if (data.screenshotImages.isNotEmpty()) {
		height += font.lineHeight + 6
		val columns = 3
		val gap = 6
		val cellWidth = (rowWidth - gap * (columns - 1)) / columns
		val cellHeight = cellWidth * 9 / 16
		val rows = (data.screenshotImages.size + columns - 1) / columns
		height += rows * cellHeight + (rows - 1) * gap
	}

	return maxOf(height, 100)
}

private fun estimateWrapped(font: net.minecraft.client.gui.Font, text: String, maxWidth: Int): Int {
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

private fun estimateMarkdown(font: net.minecraft.client.gui.Font, text: String, maxWidth: Int): Int {
	var height = 0
	for (block in Markdown.parse(text)) {
		height += when (block.type) {
			Markdown.BlockType.BLANK -> font.lineHeight / 2
			Markdown.BlockType.HEADING1 -> 4 + estimateWrapped(font, block.text, maxWidth) + 6 + 1
			Markdown.BlockType.HEADING2 -> 2 + estimateWrapped(font, block.text, maxWidth) + 4
			Markdown.BlockType.HEADING3 -> estimateWrapped(font, block.text, maxWidth) + 3
			Markdown.BlockType.BULLET -> {
				val bulletWidth = font.width("• ")
				estimateWrapped(font, block.text, maxWidth - bulletWidth)
			}
			Markdown.BlockType.PARAGRAPH -> estimateWrapped(font, block.text, maxWidth) + 2
		}
	}

	return height
}