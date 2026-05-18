// TODO - All this UI code is very repetitive, can this be abstracted out? Maybe as a UI framework?

package dev.jonbarrow.officialserverlist

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.LoadingDotsWidget
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.ServerList
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture
import kotlin.math.ceil

class OfficialServerListScreen(private val parent: Screen) : Screen(Component.literal("Official Server List")) {

	companion object {
		private const val PAGE_SIZE = 15

		private const val PAGE_BUTTON_WIDTH = 20
		private const val PAGE_BUTTON_HEIGHT = 20
		private const val PAGE_RIGHT_MARGIN = 8
		private const val PAGE_TEXT_PADDING = 6
	}

	private val filters = ServerSearchFilters()

	private var currentPageData: List<BasicServerInfo>? = null
	private var totalServersCount: Int? = null

	private var errorMessage: String? = null
	private var loading: Boolean = false
	private var selectedServer: BasicServerInfo? = null
	private var addToListButton: Button? = null
	private var showDetailsButton: Button? = null
	private var prevPageButton: Button? = null
	private var nextPageButton: Button? = null

	private var pageTextX: Int = 0
	private var pageText: String = ""

	override fun init() {
		super.init()

		if (currentPageData == null && errorMessage == null && !loading) {
			startFetch()
		}

		populateWidgets()
	}

	private fun populateWidgets() {
		val list = ServerListWidget(minecraft, width, height - 120, 50, 41) { selected ->
			selectedServer = selected
			updateButtonStates()
		}

		currentPageData?.let { list.setServers(it) }
		addRenderableWidget(list)

		if (loading && currentPageData == null) {
			val widget = LoadingDotsWidget(font, Component.literal("Searching"))
			val listTop = 50
			val listBottom = height - 70
			val listMidY = (listTop + listBottom) / 2
			widget.setPosition(width / 2 - widget.width / 2, listMidY - 5)
			addRenderableWidget(widget)
		}

		addRenderableWidget(
			Button.builder(Component.literal("Filter")) {
				minecraft.setScreen(ServerSearchFilterScreen(this, filters) {
					currentPageData = null
					totalServersCount = null
					startFetch()
				})
			}.bounds(8, 1, 150, 20).build()
		)

		addRenderableWidget(
			Button.builder(Component.literal("Sort: ${filters.sortBy.displayName}")) {
				minecraft.setScreen(ServerSearchSortSelectionScreen(this, filters) {
					filters.pageNumber = 0
					currentPageData = null
					totalServersCount = null
					startFetch()
				})
			}.bounds(8, 22, 150, 20).build()
		)

		val serversCount = totalServersCount
		val totalPages = if (serversCount == null || serversCount == 0) 1
		else ceil(serversCount.toDouble() / PAGE_SIZE).toInt()

		pageText = if (serversCount == null) {
			"${filters.pageNumber + 1} of ?"
		} else {
			"${filters.pageNumber + 1} of $totalPages"
		}
		val pageTextWidth = font.width(pageText)

		val nextButtonX = width - PAGE_RIGHT_MARGIN - PAGE_BUTTON_WIDTH
		pageTextX = nextButtonX - PAGE_TEXT_PADDING - pageTextWidth
		val prevButtonX = pageTextX - PAGE_TEXT_PADDING - PAGE_BUTTON_WIDTH

		prevPageButton = Button.builder(Component.literal("<")) {
			if (filters.pageNumber > 0 && !loading) {
				filters.pageNumber--
				goToPage()
			}
		}.bounds(prevButtonX, 22, PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT).build().also {
			it.active = filters.pageNumber > 0 && !loading
			addRenderableWidget(it)
		}

		nextPageButton = Button.builder(Component.literal(">")) {
			if (filters.pageNumber < totalPages - 1 && !loading) {
				filters.pageNumber++
				goToPage()
			}
		}.bounds(nextButtonX, 22, PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT).build().also {
			it.active = filters.pageNumber < totalPages - 1 && !loading
			addRenderableWidget(it)
		}

		addRenderableWidget(
			Button.builder(Component.translatable("gui.back")) {
				returnToParent()
			}.bounds(width / 2 - 100, height - 28, 200, 20).build()
		)

		updateButtonStates()
	}

	private fun goToPage() {
		currentPageData = null
		selectedServer = null
		startFetch()
		clearWidgets()
		populateWidgets()
	}

	private fun returnToParent() {
		minecraft.setScreen(parent)
	}

	private fun updateButtonStates() {
		val enabled = selectedServer != null
		addToListButton?.active = enabled
		showDetailsButton?.active = enabled
	}

	private fun startFetch() {
		loading = true
		CompletableFuture.supplyAsync {
			ServerListApi.searchServers(filters, PAGE_SIZE)
		}.thenAccept { result ->
			minecraft.execute {
				loading = false
				result.onSuccess { response ->
					currentPageData = response.data
					totalServersCount = response.count
					errorMessage = null
				}.onFailure { err ->
					errorMessage = err.message ?: "Unknown error"
				}
				clearWidgets()
				populateWidgets()
			}
		}
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		super.extractRenderState(graphics, mouseX, mouseY, delta)

		val textWidth = font.width(title)
		graphics.text(font, title.string, width / 2 - textWidth / 2, 8, 0xFFFFFFFF.toInt(), true)

		if (totalServersCount != null || loading) {
			graphics.text(font, pageText, pageTextX, 28, 0xFFDDDDDD.toInt(), true)
		}

		totalServersCount?.let { count ->
			val numberText = count.toString()
			val labelText = " servers found"
			val numberWidth = font.width(numberText)
			val labelWidth = font.width(labelText)
			val totalWidth = numberWidth + labelWidth
			val startX = width / 2 - totalWidth / 2

			graphics.text(font, numberText, startX, 28, 0xFFFFD24A.toInt(), true)
			graphics.text(font, labelText, startX + numberWidth, 28, 0xFFAAAAAA.toInt(), false)
		}

		errorMessage?.let { err ->
			val msg = "Error: $err"
			val w = font.width(msg)
			graphics.text(font, msg, width / 2 - w / 2, height / 2 - 30, 0xFFFF5555.toInt(), true)
		}
	}

	override fun onClose() {
		returnToParent()
	}

	private inner class ServerListWidget(minecraft: Minecraft, width: Int, height: Int, y: Int, itemHeight: Int, private val onSelectionChanged: (BasicServerInfo?) -> Unit) : ObjectSelectionList<ServerListWidget.Entry>(minecraft, width, height, y, itemHeight) {
		fun setServers(servers: List<BasicServerInfo>) {
			clearEntries()
			servers.forEach { addEntry(Entry(it)) }
		}

		override fun getRowWidth(): Int = width - 20

		override fun scrollBarX(): Int = width - 6

		override fun setSelected(entry: Entry?) {
			super.setSelected(entry)
			onSelectionChanged(entry?.server)
		}

		inner class Entry(val server: BasicServerInfo) : ObjectSelectionList.Entry<Entry>() {
			private val displayName = TextUtils.sanitize(server.name)
			private val displayDescription = TextUtils.sanitize(server.shortDescription ?: "")
			private val displayAddress = TextUtils.sanitize(server.javaAddress ?: "Unknown")

			private val animationStart = System.currentTimeMillis()

			override fun extractContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float) {
				val font = Minecraft.getInstance().font
				val padding = 4
				val left = x + padding
				val top = y + padding
				val rowWidth = width - (padding * 2)

				val icon = ImageLoader.get(server.iconImage.url)
				if (icon != null) {
					graphics.blit(RenderPipelines.GUI_TEXTURED, icon.id, left, top, 0f, 0f, 32, 32, 32, 32)
				} else {
					graphics.fill(left, top, left + 32, top + 32, 0xFF333333.toInt())
				}

				val textX = left + 40
				val textColor = 0xFFFFFFFF.toInt()
				val mutedColor = 0xFFAAAAAA.toInt()
				val accentColor = 0xFF8FBCDB.toInt()
				val statusColor = if (server.isOnline) 0xFF55FF55.toInt() else 0xFFFF5555.toInt()

				graphics.text(font, displayName, textX, top + 2, textColor, true)

				val descMaxWidth = rowWidth - 50
				val descTop = top + 14
				val descWidth = font.width(displayDescription)

				if (descWidth <= descMaxWidth) {
					graphics.text(font, displayDescription, textX, descTop, mutedColor, false)
				} else {
					drawPingPongText(graphics, displayDescription, textX, descTop, descMaxWidth, mutedColor)
				}

				graphics.text(font, displayAddress, textX, top + 24, accentColor, true)

				val players = "${server.currentOnlinePlayers}/${server.currentMaxPlayers}"
				val playersWidth = font.width(players)
				graphics.text(font, players, left + rowWidth - playersWidth - 4, top + 24, statusColor, true)
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

			override fun getNarration(): Component = Component.literal(displayName)
		}
	}
}