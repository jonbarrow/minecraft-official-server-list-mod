package dev.jonbarrow.officialserverlist

class ServerSearchFilters {
	enum class Edition(val displayName: String, val queryValue: String, val iconUrl: String) {
		JAVA("Java", "Java", "https://findmcserver.com/assets/java.webp"),
		CROSS_PLAY("Cross Play", "Cross Play", "https://findmcserver.com/assets/crossPlay.webp")
	}

	enum class SortOption(val displayName: String, val queryValue: String) {
		DEFAULT("Default Sorting", "default"),
		RANDOM("Random Sorting", "random"),
		BADGES_VOTES("Badges + Votes", "badges"),
		VOTES_DESC("Votes High to Low", "votes_desc"),
		VOTES_ASC("Votes Low to High", "votes_asc"),
		PLAYERS_DESC("Players High to Low", "players_desc"),
		PLAYERS_ASC("Players Low to High", "players_asc"),
		LAUNCHED_DESC("Launched New to Old", "launched_desc"),
		LAUNCHED_ASC("Launched Old to New", "launched_asc"),
		CREATED_DESC("Claimed New to Old", "created_desc"),
		CREATED_ASC("Claimed Old to New", "created_asc"),
		NAME_ASC("Name A to Z", "name_asc"),
		NAME_DESC("Name Z to A", "name_desc"),
		FAVORITES_DESC("Favorites High to Low", "favorites_desc"),
		FAVORITES_ASC("Favorites Low to High", "favorites_asc")
	}

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