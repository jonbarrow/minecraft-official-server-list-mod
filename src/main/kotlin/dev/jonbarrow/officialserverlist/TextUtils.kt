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

	fun eventTypeLabel(type: String?): String {
		val eventType = type?.let { runCatching { EventType.valueOf(it.uppercase()) }.getOrNull() }

		return when (eventType) {
			EventType.COMMUNITY -> "Community"
			EventType.TOURNAMENT -> "Tournament"
			EventType.FEATURE -> "Feature"
			EventType.STREAM -> "Stream"
			EventType.IN_PERSON -> "In Person"
			EventType.HOLIDAY -> "Holiday"
			null -> type?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Event"
		}
	}

	fun joinTypeLabel(type: String?): String = when (EventJoinType.fromString(type)) {
		EventJoinType.PUBLIC -> "Public"
		EventJoinType.REGISTER -> "Register"
		EventJoinType.INVITE_ONLY -> "Invite-Only"
		null -> type?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Unknown"
	}

	fun formatDate(iso: String?): String {
		if (iso.isNullOrBlank()) return "Unknown"
		return try {
			val instant = Instant.parse(iso)
			DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
				.withLocale(Locale.getDefault())
				.withZone(ZoneId.systemDefault())
				.format(instant)
		} catch (e: Exception) {
			iso
		}
	}

	fun formatDateTime(iso: String?): String {
		if (iso.isNullOrBlank()) return "Unknown"
		return try {
			val instant = Instant.parse(iso)
			DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
				.withLocale(Locale.getDefault())
				.withZone(ZoneId.systemDefault())
				.format(instant)
		} catch (e: Exception) {
			iso
		}
	}
}