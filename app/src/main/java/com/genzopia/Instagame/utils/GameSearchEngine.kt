package com.genzopia.Instagame.utils

import com.genzopia.Instagame.features.home.ui.HomeGameItem
import kotlin.math.ln
import kotlin.math.min

object GameSearchEngine {

    private const val BM25_K1 = 1.2
    private const val BM25_B = 0.75
    private const val NGRAM_SIZE = 3
    private const val FUZZY_THRESHOLD = 0.6
    private const val MAX_SUGGESTIONS = 8

    data class SearchResult(
        val game: HomeGameItem,
        val score: Double,
        val matchedField: String,
        val matchedText: String
    )

    private data class GameIndex(
        val game: HomeGameItem,
        val nameTokens: List<String>,
        val descTokens: List<String>,
        val devTokens: List<String>,
        val allText: String,
        val nameTrigrams: Set<String>,
        val phoneticName: String
    )

    private var index: List<GameIndex> = emptyList()
    private var avgDocLen = 0.0
    private var totalFieldLen = 0
    private var docCount = 0
    private var docFreq: Map<String, Int> = emptyMap()

    fun buildIndex(games: List<HomeGameItem>) {
        index = games.map { game ->
            val name = game.gameName
            GameIndex(
                game = game,
                nameTokens = tokenize(name),
                descTokens = tokenize(game.description),
                devTokens = tokenize(game.developerName),
                allText = buildString {
                    append(name.lowercase()); append(' ')
                    append(game.description.lowercase()); append(' ')
                    append(game.developerName.lowercase())
                },
                nameTrigrams = extractTrigrams(name),
                phoneticName = soundexEncode(name)
            )
        }
        docCount = index.size
        docFreq = buildDocFreq(index)
        val totalLen = index.sumOf { it.nameTokens.size + it.descTokens.size + it.devTokens.size }
        avgDocLen = if (docCount > 0) totalLen.toDouble() / docCount else 0.0
        totalFieldLen = totalLen
    }

    fun search(query: String, allGames: List<HomeGameItem>): List<HomeGameItem> {
        if (query.isBlank()) return allGames
        if (index.size != allGames.size) buildIndex(allGames)

        val q = query.trim().lowercase()
        val queryTokens = tokenize(q)
        val queryTrigrams = extractTrigrams(q)

        return index.mapNotNull { entry ->
            val score = scoreEntry(entry, q, queryTokens, queryTrigrams)
            if (score > 0.0) entry.game to score else null
        }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    fun searchWithDetails(query: String, allGames: List<HomeGameItem>): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        if (index.size != allGames.size) buildIndex(allGames)

        val q = query.trim().lowercase()
        val queryTokens = tokenize(q)
        val queryTrigrams = extractTrigrams(q)

        return index.mapNotNull { entry ->
            val score = scoreEntry(entry, q, queryTokens, queryTrigrams)
            if (score > 0.0) {
                val matched = findBestMatch(entry, q, queryTokens)
                SearchResult(entry.game, score, matched.first, matched.second)
            } else null
        }
            .sortedByDescending { it.score }
    }

    fun suggest(query: String, allGames: List<HomeGameItem>): List<String> {
        if (query.isBlank() || query.length < 2) return emptyList()
        if (index.size != allGames.size) buildIndex(allGames)

        val q = query.trim().lowercase()
        val qTrigrams = extractTrigrams(q)

        data class ScoredName(val name: String, val score: Double)

        val scored = index.mapNotNull { entry ->
            val name = entry.game.gameName
            val nameLower = name.lowercase()
            if (nameLower == q) return@mapNotNull null

            var score = 0.0
            if (nameLower.startsWith(q)) score += 20.0
            else if (nameLower.contains(q)) score += 10.0

            val nameToks = entry.nameTokens
            for (qt in tokenize(q)) {
                if (nameToks.any { it.startsWith(qt) }) score += 8.0
                if (nameToks.any { it.contains(qt) }) score += 4.0
            }

            val common = qTrigrams.intersect(entry.nameTrigrams)
            score += common.size * 2.0

            if (score > 0.0) ScoredName(name, score) else null
        }

        return scored
            .sortedByDescending { it.score }
            .take(MAX_SUGGESTIONS)
            .map { it.name }
    }

    private fun scoreEntry(
        entry: GameIndex, rawQuery: String,
        queryTokens: List<String>, queryTrigrams: Set<String>
    ): Double {
        var score = 0.0

        if (entry.allText.contains(rawQuery)) {
            score += 10.0
            if (entry.game.gameName.lowercase().startsWith(rawQuery)) score += 8.0
        }

        val commonTrigrams = queryTrigrams.intersect(entry.nameTrigrams)
        if (queryTrigrams.isNotEmpty()) {
            val ratio = commonTrigrams.size.toDouble() / queryTrigrams.size
            score += ratio * 6.0
        }

        for (token in queryTokens) {
            score += bm25(token, entry.nameTokens) * 4.0
            score += bm25(token, entry.devTokens) * 2.0
            score += bm25(token, entry.descTokens) * 1.0
        }

        val qPhonetic = soundexEncode(rawQuery)
        if (qPhonetic.isNotEmpty() && entry.phoneticName.isNotEmpty() && qPhonetic == entry.phoneticName) {
            score += 7.0
        }

        if (score < 0.5 && queryTokens.isNotEmpty()) {
            val allTokens = entry.nameTokens + entry.devTokens + entry.descTokens
            for (qToken in queryTokens) {
                val bestFuzzy = allTokens.maxOfOrNull { fuzzyScore(qToken, it) } ?: 0.0
                score += bestFuzzy * 1.5
            }
        }

        return score
    }

    private fun bm25(token: String, fieldTokens: List<String>): Double {
        if (fieldTokens.isEmpty() || docCount == 0) return 0.0
        val tf = fieldTokens.count { it == token }
        if (tf == 0) return 0.0
        val df = docFreq[token] ?: return 0.0
        val idf = ln(((docCount - df + 0.5) / (df + 0.5)).coerceAtLeast(1.0))
        val fieldLen = fieldTokens.size
        val numerator = tf * (BM25_K1 + 1)
        val denominator = tf + BM25_K1 * (1 - BM25_B + BM25_B * (fieldLen / avgDocLen.coerceAtLeast(1.0)))
        return idf * numerator / denominator
    }

    private fun fuzzyScore(a: String, b: String): Double {
        if (a.length < 3 || b.length < 3) return if (a == b) 1.0 else 0.0
        val dist = levenshtein(a, b)
        val maxLen = maxOf(a.length, b.length).toDouble()
        val similarity = 1.0 - dist / maxLen
        return if (similarity >= FUZZY_THRESHOLD) similarity else 0.0
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

    private fun findBestMatch(entry: GameIndex, rawQuery: String, queryTokens: List<String>): Pair<String, String> {
        val nameLower = entry.game.gameName.lowercase()
        if (nameLower.contains(rawQuery)) return "name" to entry.game.gameName

        val devLower = entry.game.developerName.lowercase()
        if (devLower.contains(rawQuery)) return "developer" to entry.game.developerName

        val descLower = entry.game.description.lowercase()
        if (descLower.contains(rawQuery)) {
            val idx = descLower.indexOf(rawQuery)
            val start = maxOf(0, idx - 30)
            val end = min(descLower.length, idx + rawQuery.length + 30)
            return "description" to "...${entry.game.description.substring(start, end)}..."
        }

        for (qt in queryTokens) {
            for (nt in entry.nameTokens) {
                if (nt.startsWith(qt) || fuzzyScore(qt, nt) >= FUZZY_THRESHOLD) {
                    return "name" to entry.game.gameName
                }
            }
        }

        return "name" to entry.game.gameName
    }

    private fun buildDocFreq(docs: List<GameIndex>): Map<String, Int> {
        val df = mutableMapOf<String, Int>()
        for (doc in docs) {
            val allTokens = (doc.nameTokens + doc.descTokens + doc.devTokens).toSet()
            for (t in allTokens) df[t] = (df[t] ?: 0) + 1
        }
        return df
    }

    private fun extractTrigrams(text: String): Set<String> {
        val t = "  $text ".lowercase()
        return t.windowed(NGRAM_SIZE).toSet()
    }

    private fun soundexEncode(text: String): String {
        val s = text.lowercase().replace(Regex("[^a-z]"), "")
        if (s.isEmpty()) return ""
        val first = s[0]
        val encoded = buildString {
            append(first.uppercase())
            var prev = encodeSoundexDigit(first)
            for (c in s.drop(1)) {
                val digit = encodeSoundexDigit(c)
                if (digit != prev && digit != '0') {
                    append(digit)
                    if (length >= 4) return@buildString
                }
                prev = digit
            }
            while (length < 4) append('0')
        }
        return encoded
    }

    private fun encodeSoundexDigit(c: Char): Char {
        return when (c) {
            'b', 'f', 'p', 'v' -> '1'
            'c', 'g', 'j', 'k', 'q', 's', 'x', 'z' -> '2'
            'd', 't' -> '3'
            'l' -> '4'
            'm', 'n' -> '5'
            'r' -> '6'
            else -> '0'
        }
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }
    }
}
