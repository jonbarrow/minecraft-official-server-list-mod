package dev.jonbarrow.officialserverlist

class ServerSearchFilters {
	enum class Edition(val displayNameKey: String, val queryValue: String, val iconUrl: String) {
		JAVA("officialserverlist.edition.java", "Java", "https://findmcserver.com/assets/java.webp"),
		CROSS_PLAY("officialserverlist.edition.cross_play", "Cross Play", "https://findmcserver.com/assets/crossPlay.webp")
	}

	enum class SortOption(val displayNameKey: String, val queryValue: String) {
		DEFAULT("officialserverlist.sort.default", "default"),
		RANDOM("officialserverlist.sort.random", "random"),
		BADGES_VOTES("officialserverlist.sort.badges_votes", "badges"),
		VOTES_DESC("officialserverlist.sort.votes_desc", "votes_desc"),
		VOTES_ASC("officialserverlist.sort.votes_asc", "votes_asc"),
		PLAYERS_DESC("officialserverlist.sort.players_desc", "players_desc"),
		PLAYERS_ASC("officialserverlist.sort.players_asc", "players_asc"),
		LAUNCHED_DESC("officialserverlist.sort.launched_desc", "launched_desc"),
		LAUNCHED_ASC("officialserverlist.sort.launched_asc", "launched_asc"),
		CREATED_DESC("officialserverlist.sort.created_desc", "created_desc"),
		CREATED_ASC("officialserverlist.sort.created_asc", "created_asc"),
		NAME_ASC("officialserverlist.sort.name_asc", "name_asc"),
		NAME_DESC("officialserverlist.sort.name_desc", "name_desc"),
		FAVORITES_DESC("officialserverlist.sort.favorites_desc", "favorites_desc"),
		FAVORITES_ASC("officialserverlist.sort.favorites_asc", "favorites_asc")
	}

	// * Player count labels are numeric ranges, so they don't need translation.
	// * Leaving them as plain display strings.
	data class PlayerCountOption(val displayName: String, val queryValue: String) {
		companion object {
			val ALL = listOf(
				PlayerCountOption("0-25", "0-25"),
				PlayerCountOption("26-99", "26-99"),
				PlayerCountOption("100-499", "100-499"),
				PlayerCountOption("500-999", "500-999"),
				PlayerCountOption("1000+", "1000-plus")
			)
		}
	}

	// * The official client hides all the "GameSafer" badges it gets from the API,
	// * and instead adds this custom one instead?? I have no idea why, but doing
	// * the same thing here
	val gameSaferBadge = BadgeListBadge(
		id = "gamesafer",
		name = "GameSafer",
		description = "GameSafer-verified servers",
		badgeType = "GAMERSAFER",
		iconUrl = "https://findmcserver.com/assets/diamond3.png",
		createdAt = "",
		isActive = true
	)

	var edition: Edition = Edition.JAVA
	var sortBy: SortOption = SortOption.DEFAULT
	var hasExperienceID: Boolean = false
	val selectedBadgeIDs: MutableSet<String> = mutableSetOf()
	val selectedVersionIDs: MutableSet<String> = mutableSetOf()
	val selectedLanguageIDs: MutableSet<String> = mutableSetOf()
	val selectedLocationIDs: MutableSet<String> = mutableSetOf()
	val selectedKeywordIDs: MutableSet<String> = mutableSetOf()
	val selectedPlayerCounts: MutableSet<String> = mutableSetOf()
	var searchPhrase: String = ""
	var pageNumber: Int = 0

	fun reset() {
		edition = Edition.JAVA
		sortBy = SortOption.DEFAULT
		hasExperienceID = false
		selectedBadgeIDs.clear()
		selectedVersionIDs.clear()
		selectedLanguageIDs.clear()
		selectedLocationIDs.clear()
		selectedKeywordIDs.clear()
		selectedPlayerCounts.clear()
		searchPhrase = ""
		pageNumber = 0
	}

	fun allTagIds(): List<String> = buildList {
		addAll(selectedVersionIDs)
		addAll(selectedLanguageIDs)
		addAll(selectedLocationIDs)
		addAll(selectedKeywordIDs)
	}

	fun playerCountQueryValue(): String {
		// * The official client has an "Any" size option whose value
		// * is an empty string, which seems to always be included in
		// * the list as the first option?
		if (selectedPlayerCounts.isEmpty()) {
			return ""
		}

		return "," + selectedPlayerCounts.joinToString(",")
	}
}