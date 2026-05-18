// TODO - All this UI code is very repetitive, can this be abstracted out? Maybe as a UI framework?

package dev.jonbarrow.officialserverlist

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.network.chat.Component

object OfficialServerListClient : ClientModInitializer {
	override fun onInitializeClient() {
		ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
			if (screen is JoinMultiplayerScreen) {
				addCustomButton(screen)
			}
		}
	}

	private fun addCustomButton(screen: JoinMultiplayerScreen) {
		val widgets = Screens.getWidgets(screen)

		if (widgets.any { it is Button && it.message.string == "Official Server List" }) {
			return
		}

		val button = Button.builder(Component.literal("Official Server List")) { _ ->
			Minecraft.getInstance().setScreen(OfficialServerListScreen(screen))
		}.bounds(10, 6, 120, 20).build()

		widgets.add(button)
	}
}