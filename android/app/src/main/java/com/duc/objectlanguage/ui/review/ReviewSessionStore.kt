package com.duc.objectlanguage.ui.review

import com.duc.objectlanguage.data.model.ReviewCardResponse

data class ReviewSessionState(
    val collectionId: Int,
    val practice: Boolean,
    val collectionName: String?,
    val cards: List<ReviewCardResponse>,
    val currentIndex: Int,
    val answerRevealed: Boolean,
    val finished: Boolean,
    val finishedMessage: String?,
    val summary: ReviewSessionSummary
)

object ReviewSessionStore {
    private val sessions = mutableMapOf<Pair<Int, Boolean>, ReviewSessionState>()

    fun get(collectionId: Int, practice: Boolean): ReviewSessionState? {
        return sessions[collectionId to practice]
    }

    fun hasActiveSession(collectionId: Int, practice: Boolean): Boolean {
        val state = sessions[collectionId to practice] ?: return false
        return state.cards.isNotEmpty() && !state.finished
    }

    fun save(state: ReviewSessionState) {
        if (state.collectionId <= 0) return
        sessions[state.collectionId to state.practice] = state
    }

    fun clear(collectionId: Int, practice: Boolean) {
        sessions.remove(collectionId to practice)
    }

    fun clearAll() {
        sessions.clear()
    }
}
