package com.genzopia.Instagame.utils

import com.genzopia.Instagame.features.home.ui.HomeGameItem
import kotlin.math.ln

/**
 * Smart search engine for games using:
 * - TF-IDF scoring (term frequency × inverse document frequency)
 * - Fuzzy matching via Levenshtein distance for typo tolerance
 * - Prefix boosting (exact prefix matches rank higher)
 * - Field weighting (name > developer > description)
 *
 * All computation is pure Kotlin — no ML libraries needed.
 */
object GameSearchEngine {

    private data class GameIndex(
        val game: HomeGameItem,
        val nameTokens: List<String>,
        val descTokens: List<String>,
        val devTokens: List<String>,
        val allText: String // lowercased concat for fast substring check
    )

    private var index: List<GameIndex> = emptyList()
    private var idfCache: Map<String, Double> = emptyMap()

    /** Call this once when the full game list is loaded / updated. */
    fun buildIndex(games: List<HomeGameItem>) {
        index = games.map { game ->
            GameIndex(
                game = game,
                nameTokens = tokenize(game.gameName),
                descTokens = tokenize(game.description),
                devTokens = tokenize(game.developerName),
                allText = "${game.gameName} ${game.description} ${game.developerName}".lowercase()
            )
        }
        idfCache = buildIdf(index)
    }

    /** Returns ranked results for the given query. Empty query returns all games. */
    fun search(query: String, allGames: List<HomeGameItem>): List<HomeGameItem> {
        if (query.isBlank()) return allGames

        // Rebuild index lazily if games changed
        if (index.size != allGames.size) buildIndex(allGames)

        val q = query.trim().lowercase()
        val queryTokens = tokenize(q)

        data class Scored(val game: HomeGameItem, val score: Double)

        val results = index.mapNotNull { entry ->
            val score = scoreEntry(entry, q, queryTokens)
            if (score > 0.0) Scored(entry.game, score) else null
        }

        return results.sortedByDescending { it.score }.map { it.game }
    }

    // ── Scoring ──────────────────────────────────────────────────────────────

    private fun scoreEntry(entry: GameIndex, rawQuery: String, queryTokens: List<String>): Double {
        var score = 0.0

        // 1. Exact substring match (highest priority)
        if (entry.allText.contains(rawQuery)) {
            score += 10.0
            // Extra boost if name starts with query
            if (entry.game.gameName.lowercase().startsWith(rawQuery)) score += 5.0
        }

        // 2. TF-IDF for each query token
        for (token in queryTokens) {
            // Name field — weight 4
            score += tfidf(token, entry.nameTokens, entry.game.gameId) * 4.0
            // Developer field — weight 2
            score += tfidf(token, entry.devTokens, entry.game.gameId) * 2.0
            // Description field — weight 1
            score += tfidf(token, entry.descTokens, entry.game.gameId) * 1.0
        }

        // 3. Fuzzy matching — catches typos (only if TF-IDF gave nothing)
        if (score < 0.5 && queryTokens.isNotEmpty()) {
            val allTokens = entry.nameTokens + entry.devTokens + entry.descTokens
            for (qToken in queryTokens) {
                val bestFuzzy = allTokens.maxOfOrNull { fuzzyScore(qToken, it) } ?: 0.0
                score += bestFuzzy * 2.0 // fuzzy is lower confidence
            }
        }

        return score
    }

    private fun tfidf(token: String, fieldTokens: List<String>, docId: String): Double {
        if (fieldTokens.isEmpty()) return 0.0
        val tf = fieldTokens.count { it == token }.toDouble() / fieldTokens.size
        val idf = idfCache[token] ?: 0.0
        return tf * idf
    }

    // ── Fuzzy (Levenshtein-based) ─────────────────────────────────────────────

    /**
     * Returns a 0..1 similarity score between two tokens.
     * 1.0 = identical, 0.0 = completely different.
     * Skips computation for very short tokens to avoid false positives.
     */
    private fun fuzzyScore(a: String, b: String): Double {
        if (a.length < 3 || b.length < 3) return if (a == b) 1.0 else 0.0
        val dist = levenshtein(a, b)
        val maxLen = maxOf(a.length, b.length).toDouble()
        val similarity = 1.0 - dist / maxLen
        // Only count as a match if similarity is above threshold
        return if (similarity >= 0.6) similarity else 0.0
    }

    private fun levenshtein(a: String, b: String): Double {
        val m = a.length; val n = b.length
        if (m == 0) return n.toDouble()
        if (n == 0) return m.toDouble()
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) for (j in 1..n) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
            else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
        }
        return dp[m][n].toDouble()
    }

    // ── IDF ──────────────────────────────────────────────────────────────────

    private fun buildIdf(docs: List<GameIndex>): Map<String, Double> {
        val n = docs.size.toDouble()
        val docFreq = mutableMapOf<String, Int>()
        for (doc in docs) {
            val allTokens = (doc.nameTokens + doc.descTokens + doc.devTokens).toSet()
            for (t in allTokens) docFreq[t] = (docFreq[t] ?: 0) + 1
        }
        return docFreq.mapValues { (_, df) -> ln(n / (1.0 + df)) + 1.0 }
    }

    // ── Tokenizer ─────────────────────────────────────────────────────────────

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }
    }
}
