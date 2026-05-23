// TODO - All this UI code is very repetitive, can this be abstracted out? Maybe as a UI framework?

package dev.jonbarrow.officialserverlist

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component

class FMCSAccountScreen(private val parent: Screen) : Screen(Component.translatable("officialserverlist.screen.server_list.title")) {
	companion object {
		private const val DEFAULT_USER_IMAGE = "https://findmcserver.com/assets/user_default_img.webp"

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

	private enum class Tab(val labelKey: String) {
		FAVORITE_SERVERS("officialserverlist.tab.favorite_servers"),
		YOUR_SERVERS("officialserverlist.tab.your_servers"),
		FAVORITE_EVENTS("officialserverlist.tab.favorite_events"),
		UPCOMING_EVENTS("officialserverlist.tab.upcoming_events")
	}

	private var activeTab: Tab = Tab.FAVORITE_SERVERS
	private var sectionList: SectionListWidget? = null

	override fun init() {
		super.init()

		populateWidgets()
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
					minecraft.setScreen(MicrosoftAuthScreen(this) { hash ->
						minecraft.execute {
							if (hash != null) {
								ServerListApi.loginWithHash(hash)
								clearWidgets()
								populateWidgets()
							}
						}
					})
				}.bounds(width / 2 - 45, height / 2 - 10, 90, 20).build()
			)

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
			Button.builder(Component.translatable("officialserverlist.button.preferences")) {
				// TODO - stub
			}.bounds(preferencesX, bottomRowY, HEADER_BUTTON_WIDTH, HEADER_BUTTON_HEIGHT).build()
		)

		addRenderableWidget(
			Button.builder(Component.translatable("officialserverlist.button.logout")) {
				// TODO - stub
			}.bounds(logoutX, bottomRowY, HEADER_BUTTON_WIDTH, HEADER_BUTTON_HEIGHT).build()
		)

		addRenderableWidget(
			Button.builder(Component.translatable("officialserverlist.button.delete_account")) {
				// TODO - stub
			}.bounds(deleteX, bottomRowY, HEADER_BUTTON_WIDTH, HEADER_BUTTON_HEIGHT).build()
		)

		if (loginSession.platformUserId == null || loginSession.platformUserName == null) {
			addRenderableWidget(
				Button.builder(Component.translatable("officialserverlist.button.link_minecraft_account")) {
					// TODO - stub
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
		ServerListApi.loginSession?.let { drawHeader(graphics, it) }
		super.extractRenderState(graphics, mouseX, mouseY, delta)
	}

	private fun drawHeader(graphics: GuiGraphicsExtractor, loginSession: LoginSessionData) {
		val font = Minecraft.getInstance().font
		val left = SIDE_PADDING
		val y = HEADER_TOP

		val iconSize = HEADER_HEIGHT - 8
		val iconX = left
		val iconY = y + 4

		val userImage = ImageLoader.get(DEFAULT_USER_IMAGE)
		if (userImage != null) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, userImage.id, iconX, iconY, 0f, 0f, iconSize, iconSize, iconSize, iconSize)
		} else {
			graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF333333.toInt())
		}

		val textX = iconX + iconSize + 8
		val bottomRowY = HEADER_TOP + HEADER_HEIGHT - HEADER_BUTTON_HEIGHT - 4
		val topRowY = bottomRowY - HEADER_BUTTON_HEIGHT - HEADER_BUTTON_GAP
		val textY = topRowY + (HEADER_BUTTON_HEIGHT - font.lineHeight) / 2

		graphics.text(font, Component.translatable("officialserverlist.label.logged_in_as", loginSession.email).string, textX, textY, 0xFFFFFFFF.toInt(), true)
	}

	override fun onClose() {
		returnToParent()
	}

	private inner class SectionListWidget(width: Int, height: Int, y: Int, private val section: Tab) : ObjectSelectionList<SectionListWidget.SectionEntry>(minecraft, width, height, y, 18) {
		override fun getRowWidth(): Int = width - 20
		override fun scrollBarX(): Int = width - 6
		override fun setSelected(entry: SectionEntry?) {
			// * No-op
		}

		inner class SectionEntry(private val text: String) : ObjectSelectionList.Entry<SectionEntry>() {
			override fun extractContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float) {}

			override fun getNarration(): Component = Component.literal(text)
		}
	}
}