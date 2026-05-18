package dev.jonbarrow.officialserverlist

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object TextUtils {
	// * Strips away characters Minecraft can't handle, like emoji modifier sequences
	fun sanitize(input: String): String {
		if (input.isEmpty()) return input
		val builder = StringBuilder(input.length)
		var i = 0

		while (i < input.length) {
			val codepoint = input.codePointAt(i)
			val charCount = Character.charCount(codepoint)

			val keep = when {
				codepoint > 0xFFFF -> false
				codepoint in 0xFE00..0xFE0F -> false
				Character.getType(codepoint) == Character.FORMAT.toInt() -> false
				Character.isISOControl(codepoint) -> false
				else -> true
			}

			if (keep) {
				builder.appendCodePoint(codepoint)
			}

			i += charCount
		}

		return builder.toString()
	}
}