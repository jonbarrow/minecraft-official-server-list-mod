// TODO - All this UI code is very repetitive, can this be abstracted out? Maybe as a UI framework?

package dev.jonbarrow.officialserverlist

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.LoadingDotsWidget
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.ServerList
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
		if (loading && currentPageData == null) {
			val widget = LoadingDotsWidget(font, Component.literal("Searching"))
			addRenderableWidget(widget)
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
}