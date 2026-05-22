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

class FMCSAccountScreen(private val parent: Screen) : Screen(Component.translatable("officialserverlist.screen.server_list.title")) {
	override fun init() {
		super.init()

		populateWidgets()
	}

	private fun populateWidgets() {
		if (ServerListApi.loginSession == null) {
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
				}.bounds(0, 0, 90, 20).build()
			)

			return
		}
	}

	private fun returnToParent() {
		minecraft.setScreen(parent)
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		super.extractRenderState(graphics, mouseX, mouseY, delta)

		val loginSession = ServerListApi.loginSession

		if (loginSession != null) {
			val font = Minecraft.getInstance().font

			graphics.text(font, "Logged in as ${loginSession.userId}", 0, 0, 0xFFAAAAAA.toInt(), false)
		}
	}

	override fun onClose() {
		returnToParent()
	}
}