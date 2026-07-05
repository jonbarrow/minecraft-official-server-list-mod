// TODO - Tried to model this screen more-or-less like the vanilla ConfirmLinkScreen. Should this be redone to match the usage style of the other pages?

package dev.jonbarrow.officialserverlist

import it.unimi.dsi.fastutil.booleans.BooleanConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class ConfirmDeleteFMCSAccountScreen(callback: BooleanConsumer, private val userID: String, private val onDeleted: () -> Unit) : ConfirmScreen(callback, Component.translatable("officialserverlist.screen.delete_account.title"), Component.translatable("officialserverlist.screen.delete_account.message") .withStyle { style ->
	style
		.withColor(0xFF5555)
		.withBold(true)
		.withItalic(true)
		.withUnderlined(true)
}) {
	init {
		yesButtonComponent = Component.translatable("officialserverlist.button.delete_account_confirm")
		noButtonComponent = CommonComponents.GUI_CANCEL
	}

	companion object {
		fun open(parent: Screen?, userID: String, onDeleted: () -> Unit) {
			val minecraft = Minecraft.getInstance()

			minecraft.setScreen(
				ConfirmDeleteFMCSAccountScreen(
					BooleanConsumer { confirmed ->
						if (confirmed) {
							ServerListApi.deleteAccount(userID, DeleteAccountPayload(
								requestedByUserId = userID
							))
							ServerListApi.logout() // * The website logs you out after deleting the account, so we do too

							onDeleted()
						} else {
							minecraft.setScreen(parent)
						}
					}, userID, onDeleted
				)
			)
		}
	}
}