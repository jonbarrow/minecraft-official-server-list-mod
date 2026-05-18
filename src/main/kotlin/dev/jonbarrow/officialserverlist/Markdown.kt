package dev.jonbarrow.officialserverlist

// * Super minimal markdown parser, only handling common syntax that I saw used by the API
// TODO - Replace this with a real/robust parser?
object Markdown {
	enum class BlockType {
		HEADING1,
		HEADING2,
		HEADING3,
		PARAGRAPH,
		BULLET,
		BLANK
	}

	data class Block(val type: BlockType, val text: String)

	fun parse(text: String): List<Block> {
		val blocks = mutableListOf<Block>()

		for (rawLine in text.split("\n")) {
			val line = rawLine.trimEnd()
			when {
				line.isBlank() -> blocks.add(Block(BlockType.BLANK, ""))
				line.startsWith("### ") -> blocks.add(Block(BlockType.HEADING3, convertInline(line.removePrefix("### "))))
				line.startsWith("## ") -> blocks.add(Block(BlockType.HEADING2, convertInline(line.removePrefix("## "))))
				line.startsWith("# ") -> blocks.add(Block(BlockType.HEADING1, convertInline(line.removePrefix("# "))))
				line.startsWith("- ") || line.startsWith("* ") -> blocks.add(Block(BlockType.BULLET, convertInline(line.substring(2))))
				else -> blocks.add(Block(BlockType.PARAGRAPH, convertInline(line)))
			}
		}

		return blocks
	}

	// * Convert inline markdown to Minecraft formatting codes
	private fun convertInline(line: String): String {
		var result = line

		// * Inline links [text](url) -> "text (url)"
		result = Regex("\\[([^\\]]+)]\\(([^)]+)\\)").replace(result) { "${it.groupValues[1]} (${it.groupValues[2]})" }

		// * Bold before italic, otherwise the italic regex eats one of the asterisks
		// * out of **x** and breaks the pairing
		result = Regex("\\*\\*([^*]+)\\*\\*").replace(result) { "§l${it.groupValues[1]}§r" }
		result = Regex("__([^_]+)__").replace(result) { "§l${it.groupValues[1]}§r" }

		// * Italic
		result = Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)").replace(result) { "§o${it.groupValues[1]}§r" }
		result = Regex("(?<!_)_([^_]+)_(?!_)").replace(result) { "§o${it.groupValues[1]}§r" }

		// * Inline code. There is no code block we can use so just make it gray to look different
		result = Regex("`([^`]+)`").replace(result) { "§7${it.groupValues[1]}§r" }

		return TextUtils.sanitize(result)
	}
}
