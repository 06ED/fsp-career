package ru.fsp.career.matching

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Lightweight, dependency-free NLP used by the matching engine.
 *
 * Pipeline: lowercase + `ё→е` → canonicalisation of IT terms that tokenisers usually break
 * (`C++`, `Node.js`, `CI/CD`, `A/B`) → split on non letter/digit → RU/EN stop words →
 * transliterated tech slang (`питон`, `постгрес`, `кафка`) → stemming
 * (Snowball Russian algorithm for Cyrillic tokens, a light suffix stripper for Latin tokens) →
 * small cross-language concept map (`микросервис` ≈ `microservice`).
 *
 * The resulting stems feed a TF-IDF model (sublinear tf, smoothed idf, L2-normalised vectors);
 * relevance is the cosine similarity between the employer need and the candidate profile text.
 * It is deliberately simple, deterministic and explainable: every point of text relevance can be
 * traced back to concrete shared terms, which the explanation shows to the employer.
 */
object TextAnalysis {
    /** A stem plus the first surface form it came from (used to show human-readable matched terms). */
    data class Token(val stem: String, val surface: String)

    private val phraseRules = listOf(
        Regex("c\\+\\+") to " cpp ",
        Regex("c#") to " csharp ",
        Regex("f#") to " fsharp ",
        Regex("\\.net\\b") to " dotnet ",
        Regex("node\\.?js") to " nodejs ",
        Regex("vue\\.?js") to " vue ",
        Regex("next\\.?js") to " nextjs ",
        Regex("nuxt\\.?js") to " nuxtjs ",
        Regex("react\\.?js") to " react ",
        Regex("ci\\s*/\\s*cd") to " cicd ",
        Regex("(?<![\\p{L}\\p{N}])(a|а)\\s*/\\s*(b|б)(?![\\p{L}\\p{N}])") to " abtest ",
        Regex("(?<![\\p{L}\\p{N}])ux\\s*/\\s*ui(?![\\p{L}\\p{N}])") to " ux ui ",
        Regex("(?<![\\p{L}\\p{N}])ui\\s*/\\s*ux(?![\\p{L}\\p{N}])") to " ui ux ",
        Regex("scikit-learn|sklearn") to " scikitlearn ",
        Regex("power\\s+bi") to " powerbi ",
        Regex("ms\\s+sql") to " mssql ",
    )

    /** Tech slang / transliterations / abbreviations mapped to one canonical token before stemming. */
    private val tokenSynonyms = mapOf(
        "бэкенд" to "backend", "бекенд" to "backend", "бэкэнд" to "backend", "бэк" to "backend",
        "фронтенд" to "frontend", "фронтэнд" to "frontend", "фронт" to "frontend",
        "постгрес" to "postgresql", "postgres" to "postgresql", "pg" to "postgresql", "постгресе" to "postgresql",
        "питон" to "python", "питоне" to "python", "джава" to "java", "котлин" to "kotlin", "котлине" to "kotlin",
        "кафка" to "kafka", "кафки" to "kafka", "кафку" to "kafka", "докер" to "docker", "кубернетес" to "kubernetes",
        "k8s" to "kubernetes", "реакт" to "react", "реакте" to "react", "фигма" to "figma", "фигме" to "figma",
        "golang" to "go", "js" to "javascript", "ts" to "typescript", "spark" to "spark", "спарк" to "spark",
        "эйрфлоу" to "airflow", "кликхаус" to "clickhouse", "редис" to "redis", "гит" to "git",
        "мл" to "ml", "машинное" to "ml", "ии" to "ai",
    )

    /** Cross-language concepts: Russian stems that mean the same as an English stem. */
    private val conceptMap = mapOf(
        "микросервис" to "microservice", "микросервисн" to "microservice",
        "дизайн" to "design", "дизайнер" to "design",
        "аналитик" to "analyt", "аналитическ" to "analyt",
        "тестирован" to "test", "тест" to "test",
        "продукт" to "product", "продуктов" to "product",
        "интерфейс" to "interface", "архитектур" to "architectur",
        "платеж" to "payment", "платежн" to "payment",
        "мобильн" to "mobil", "безопасн" to "secur",
        "исследован" to "research", "прототип" to "prototyp", "прототипирован" to "prototyp",
        "алгоритм" to "algorithm", "алгоритмическ" to "algorithm",
        "оптимизац" to "optimiz", "масштабирован" to "scal", "нагрузк" to "load",
    )

    private val stopWords = setOf(
        // Russian function words and vacancy boilerplate that carry no matching signal.
        "и", "в", "во", "не", "что", "он", "на", "я", "с", "со", "как", "а", "то", "все", "всё", "она", "так", "его",
        "но", "да", "ты", "к", "у", "же", "вы", "за", "бы", "по", "только", "ее", "мне", "было", "вот", "от", "меня",
        "еще", "нет", "о", "из", "ему", "когда", "даже", "ли", "если", "уже", "или", "ни", "быть", "был", "до",
        "вас", "там", "потом", "себя", "ей", "может", "они", "тут", "где", "есть", "надо", "для", "мы", "их",
        "чем", "была", "сам", "без", "чего", "раз", "тоже", "себе", "под", "будет", "кто", "этот", "того",
        "потому", "этого", "какой", "здесь", "этом", "один", "мой", "тем", "чтобы", "сейчас", "были", "при",
        "об", "другой", "после", "над", "больше", "тот", "через", "эти", "нас", "про", "всего", "них", "много",
        "эту", "этой", "перед", "том", "такой", "им", "более", "всегда", "между", "наш", "наша", "наши", "нашей",
        "нашу", "нашего", "ваш", "это", "также", "который", "которые", "которая", "которых", "которое", "ищем",
        "нужен", "нужна", "нужны", "требуется", "будем", "рады", "плюсом", "плюс", "уровня", "уровень", "лет", "год",
        "года", "очень", "нам", "вам", "свой", "свои", "своих", "весь", "вся", "всех",
        // English function words.
        "a", "an", "the", "and", "or", "of", "to", "in", "on", "for", "with", "at", "by", "from", "is", "are", "was",
        "were", "be", "been", "as", "it", "that", "this", "we", "you", "our", "your", "i", "my", "me", "us",
    )

    private val splitter = Regex("[^\\p{L}\\p{N}]+")

    fun tokens(text: String): List<Token> {
        var normalized = text.lowercase().replace('ё', 'е')
        for ((pattern, replacement) in phraseRules) normalized = pattern.replace(normalized, replacement)
        return splitter.split(normalized).asSequence()
            .filter { it.isNotEmpty() && it !in stopWords }
            .mapNotNull { raw ->
                val word = tokenSynonyms[raw] ?: raw
                if (word.length < 2 && !word.all(Char::isDigit)) return@mapNotNull null
                if (word.all(Char::isDigit)) return@mapNotNull null // years, counts: no topical signal
                val stem = if (word.any { it in 'а'..'я' }) RussianStemmer.stem(word) else EnglishStemmer.stem(word)
                Token(conceptMap[stem] ?: stem, raw)
            }.toList()
    }

    fun terms(text: String): List<String> = tokens(text).map { it.stem }

    /** Canonical key for a skill tag (`Node.js`, `nodejs`, `NodeJS` → `nodejs`; `Postgres` → `postgresql`). */
    fun skillKey(skill: String): String {
        val tokens = tokens(skill)
        if (tokens.isEmpty()) return skill.trim().lowercase()
        return tokens.joinToString("") { it.stem }
    }
}

/**
 * TF-IDF model over a document collection.
 * tf = 1 + ln(count) (sublinear, so repeating a keyword ten times does not dominate);
 * idf = ln((N + 1) / (df + 1)) + 1 (smoothed; terms unseen in the pool get the max idf).
 */
class TfIdfModel(documents: List<List<String>>) {
    private val documentCount = documents.size
    private val documentFrequency: Map<String, Int> = buildMap {
        documents.forEach { doc -> doc.toSet().forEach { term -> put(term, (get(term) ?: 0) + 1) } }
    }

    fun idf(term: String): Double = ln((documentCount + 1.0) / ((documentFrequency[term] ?: 0) + 1.0)) + 1.0

    /** L2-normalised sparse vector; empty documents map to an empty vector (cosine 0). */
    fun vector(terms: List<String>, boost: Map<String, Double> = emptyMap()): Map<String, Double> {
        if (terms.isEmpty()) return emptyMap()
        val raw = terms.groupingBy { it }.eachCount().mapValues { (term, count) ->
            (1.0 + ln(count.toDouble())) * idf(term) * (boost[term] ?: 1.0)
        }
        val norm = sqrt(raw.values.sumOf { it * it })
        return if (norm == 0.0) emptyMap() else raw.mapValues { it.value / norm }
    }

    companion object {
        fun cosine(a: Map<String, Double>, b: Map<String, Double>): Double {
            val (small, large) = if (a.size <= b.size) a to b else b to a
            return small.entries.sumOf { (term, weight) -> weight * (large[term] ?: 0.0) }
        }

        /** Shared terms ordered by their contribution to the cosine (for explanations). */
        fun contributions(a: Map<String, Double>, b: Map<String, Double>): List<Pair<String, Double>> =
            a.entries.mapNotNull { (term, weight) -> b[term]?.let { term to weight * it } }
                .sortedByDescending { it.second }
    }
}

/**
 * Snowball Russian stemmer (Porter's algorithm for Russian, https://snowballstem.org/algorithms/russian/stemmer.html).
 * Implemented directly to avoid a dependency; operates on lowercase words with `ё` already folded to `е`.
 */
internal object RussianStemmer {
    private const val VOWELS = "аеиоуыэюя"
    private val perfectiveGerund1 = listOf("в", "вши", "вшись")
    private val perfectiveGerund2 = listOf("ив", "ивши", "ившись", "ыв", "ывши", "ывшись")
    private val adjective = listOf(
        "ее", "ие", "ые", "ое", "ими", "ыми", "ей", "ий", "ый", "ой", "ем", "им", "ым", "ом", "его", "ого", "ему",
        "ому", "их", "ых", "ую", "юю", "ая", "яя", "ою", "ею",
    )
    private val participle1 = listOf("ем", "нн", "вш", "ющ", "щ")
    private val participle2 = listOf("ивш", "ывш", "ующ")
    private val reflexive = listOf("ся", "сь")
    private val verb1 = listOf("ла", "на", "ете", "йте", "ли", "й", "л", "ем", "н", "ло", "но", "ет", "ют", "ны", "ть", "ешь", "нно")
    private val verb2 = listOf(
        "ила", "ыла", "ена", "ейте", "уйте", "ите", "или", "ыли", "ей", "уй", "ил", "ыл", "им", "ым", "ен", "ило",
        "ыло", "ено", "ят", "ует", "уют", "ит", "ыт", "ены", "ить", "ыть", "ишь", "ую", "ю",
    )
    private val noun = listOf(
        "а", "ев", "ов", "ие", "ье", "е", "иями", "ями", "ами", "еи", "ии", "и", "ией", "ей", "ой", "ий", "й", "иям",
        "ям", "ием", "ем", "ам", "ом", "о", "у", "ах", "иях", "ях", "ы", "ь", "ию", "ью", "ю", "ия", "ья", "я",
    )

    /**
     * Snowball `among` semantics: pick the LONGEST suffix across both groups; group-1 suffixes must be
     * preceded by `а` or `я` (which stays). If the longest candidate fails its condition, the step fails.
     */
    private fun removeAmong(rv: String, group1: List<String>, group2: List<String>): String? {
        val best = (group1.map { it to true } + group2.map { it to false })
            .filter { rv.endsWith(it.first) }
            .maxByOrNull { it.first.length } ?: return null
        val (suffix, needsAYa) = best
        val rest = rv.dropLast(suffix.length)
        if (needsAYa && (rest.isEmpty() || rest.last() !in "ая")) return null
        return rest
    }

    private fun removeAdjectival(rv: String): String? {
        val withoutAdjective = removeAmong(rv, emptyList(), adjective) ?: return null
        return removeAmong(withoutAdjective, participle1, participle2) ?: withoutAdjective
    }

    fun stem(word: String): String {
        if (word.length < 3) return word
        val rvStart = word.indexOfFirst { it in VOWELS }.takeIf { it >= 0 }?.plus(1) ?: return word
        val prefix = word.substring(0, rvStart)
        var rv = word.substring(rvStart)

        // Step 1: perfective gerund, otherwise reflexive + (adjectival | verb | noun).
        val gerund = removeAmong(rv, perfectiveGerund1, perfectiveGerund2)
        if (gerund != null) rv = gerund else {
            rv = removeAmong(rv, emptyList(), reflexive) ?: rv
            rv = removeAdjectival(rv) ?: removeAmong(rv, verb1, verb2) ?: removeAmong(rv, emptyList(), noun) ?: rv
        }
        // Step 2: trailing и.
        if (rv.endsWith("и")) rv = rv.dropLast(1)
        // Step 3: derivational ость/ост, only inside R2.
        val word3 = prefix + rv
        val r2 = regionR2(word3)
        for (suffix in listOf("ость", "ост")) {
            if (word3.endsWith(suffix) && word3.length - suffix.length >= r2) { rv = rv.dropLast(suffix.length); break }
        }
        // Step 4: undouble н, superlative, soft sign.
        when {
            rv.endsWith("ейше") -> rv = rv.dropLast(4).let { if (it.endsWith("нн")) it.dropLast(1) else it }
            rv.endsWith("ейш") -> rv = rv.dropLast(3).let { if (it.endsWith("нн")) it.dropLast(1) else it }
            rv.endsWith("нн") -> rv = rv.dropLast(1)
            rv.endsWith("ь") -> rv = rv.dropLast(1)
        }
        return prefix + rv
    }

    /** R1 = after the first non-vowel following a vowel; R2 = the same rule applied inside R1. */
    private fun regionR2(word: String): Int {
        fun next(from: Int): Int {
            var i = from
            while (i < word.length && word[i] !in VOWELS) i++
            while (i < word.length && word[i] in VOWELS) i++
            return if (i < word.length) i + 1 else word.length
        }
        return next(next(0))
    }
}

/** Light English suffix stripping: enough to merge plural/verb forms (`services`→`service`, `testing`→`test`). */
internal object EnglishStemmer {
    fun stem(word: String): String {
        if (word.length <= 3 || word.any { !it.isLetter() }) return word
        var w = word
        w = when {
            w.endsWith("ies") && w.length > 4 -> w.dropLast(3) + "y"
            w.endsWith("sses") -> w.dropLast(2)
            w.endsWith("xes") || w.endsWith("ches") || w.endsWith("shes") -> w.dropLast(2)
            w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is") -> w.dropLast(1)
            else -> w
        }
        w = when {
            w.endsWith("ing") && w.length > 5 -> w.dropLast(3)
            w.endsWith("ed") && w.length > 4 -> w.dropLast(2)
            else -> w
        }
        return w
    }
}
