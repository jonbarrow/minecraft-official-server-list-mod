// TODO - All this UI code is very repetitive, can this be abstracted out? Maybe as a UI framework?

package dev.jonbarrow.officialserverlist

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class ServerSearchSortSelectionScreen(private val parent: Screen, private val filters: ServerSearchFilters, private val onChange: () -> Unit) : Screen(Component.translatable("officialserverlist.screen.sort_servers.title")) {
	override fun init() {
		super.init()

		val listTop = 30
		val listHeight = height - listTop - 40

		addRenderableWidget(SortListWidget(width, listHeight, listTop))
		addRenderableWidget(
			Button.builder(Component.translatable("gui.cancel")) {
				minecraft.setScreen(parent)
			}.bounds(width / 2 - 100, height - 28, 200, 20).build()
		)
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		super.extractRenderState(graphics, mouseX, mouseY, delta)
		graphics.text(font, title.string, width / 2 - font.width(title) / 2, 10, 0xFFFFFFFF.toInt(), true)
	}

	override fun onClose() {
		minecraft.setScreen(parent)
	}

	private inner class SortListWidget(width: Int, height: Int, y: Int) : ObjectSelectionList<SortListWidget.Entry>(minecraft, width, height, y, 20) {
		init {
			for (option in ServerSearchFilters.SortOption.entries) {
				addEntry(Entry(option))
			}
		}

		override fun getRowWidth(): Int = 260
		override fun scrollBarX(): Int = width / 2 + getRowWidth() / 2 + 6

		override fun setSelected(entry: Entry?) {
			super.setSelected(entry)
			if (entry != null) {
				filters.sortBy = entry.option
				onChange()
				minecraft.setScreen(parent)
			}
		}

		inner class Entry(val option: ServerSearchFilters.SortOption) : ObjectSelectionList.Entry<Entry>() {
			override fun extractContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float) {
				val font = Minecraft.getInstance().font
				val isCurrent = filters.sortBy == option
				val prefix = if (isCurrent) "▶ " else "    "
				val color = if (isCurrent) 0xFFFFD24A.toInt() else 0xFFFFFFFF.toInt()
				graphics.text(font, prefix + Component.translatable(option.displayNameKey).string, x + 8, y + 6, color, true)
			}

			override fun getNarration(): Component = Component.translatable(option.displayNameKey)
		}
	}
}