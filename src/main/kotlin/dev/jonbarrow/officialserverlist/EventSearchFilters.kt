package dev.jonbarrow.officialserverlist

class EventSearchFilters {
	var selectedEventType: EventType? = null
	var selectedJoinType: EventJoinType? = null

	fun reset() {
		selectedEventType = null
		selectedJoinType = null
	}

	fun eventTypeQueryValue(): String {
		val eventType = selectedEventType ?: return ""
		return eventTypeToQueryString(eventType)
	}

	fun joinTypeQueryValue(): String {
		val joinType = selectedJoinType ?: return ""
		return joinTypeToQueryString(joinType)
	}

	private fun eventTypeToQueryString(eventType: EventType): String {
		return eventType.name
	}

	private fun joinTypeToQueryString(joinType: EventJoinType): String {
		return when (joinType) {
			EventJoinType.PUBLIC -> "PUBLIC"
			EventJoinType.REGISTER -> "REGISTER"
			EventJoinType.INVITE_ONLY -> "INVITE-ONLY"
		}
	}
}
