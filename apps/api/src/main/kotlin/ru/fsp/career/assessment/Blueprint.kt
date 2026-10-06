package ru.fsp.career.assessment

import kotlin.random.Random

/**
 * Vacancy text → skills → topic weights → per-topic item quotas ("blueprint").
 *
 * The blueprint only decides WHICH topics the test samples; difficulty comes from the adaptive
 * engine and the score from the IRT model, so candidates tested "под вакансию" stay comparable with
 * everyone else in the specialization (same θ scale, same cut-points).
 */
data class Skill(val id: String, val label: String, val topics: List<String>, val specializations: Set<String>, val terms: List<String>)

data class DetectedSkill(val skill: Skill, val hits: Int, val matched: List<String>)

data class Blueprint(
    val specialization: String,
    val detectedSpecialization: String?,
    val skills: List<DetectedSkill>,
    /** Normalized weights over the topics available in the specialization's pool. */
    val topicWeights: Map<String, Double>,
    /** Number of test items per topic; sums to the test length. */
    val quotas: Map<String, Int>,
    /** Detected skills that the item bank does not cover for this specialization (reported honestly). */
    val uncovered: List<DetectedSkill>,
)

object Blueprints {
    /** Share of the vacancy-derived weights when a vacancy is supplied; the rest is the specialization core. */
    const val VACANCY_SHARE = 0.6

    private val BE = setOf("backend"); private val FE = setOf("frontend"); private val DA = setOf("data")
    private val PR = setOf("product"); private val DS = setOf("design")

    /**
     * Dictionary of ru/en terms. Terms are lowercase stems matched as prefixes of stemmed tokens; Latin terms
     * of up to three letters must match a whole token ("sql", "ts", "ux"). Multi-word terms use spaces.
     */
    val skills: List<Skill> = listOf(
        Skill("kotlin", "Kotlin / JVM", listOf("kotlin"), BE, listOf("kotlin", "котлин", "java", "джав", "jvm")),
        Skill("jvm-web", "Ktor / Spring", listOf("http", "kotlin"), BE, listOf("ktor", "spring")),
        Skill("concurrency", "Корутины и многопоточность", listOf("concurrency"), BE, listOf("корутин", "coroutin", "многопоточ", "multithread", "concurren", "конкурентн", "параллел", "deadlock", "дедлок", "блокировк")),
        Skill("postgresql", "PostgreSQL", listOf("sql", "databases"), BE + DA, listOf("postgres", "постгрес", "pg")),
        Skill("sql", "SQL", listOf("sql"), BE + DA, listOf("sql", "select", "join", "cte")),
        Skill("indexes", "Индексы и оптимизация запросов", listOf("databases"), BE, listOf("индекс", "index", "explain", "оптимизац запрос")),
        Skill("transactions", "Транзакции и изоляция", listOf("databases"), BE, listOf("транзакц", "transaction", "acid", "изоляц", "isolation", "mvcc")),
        Skill("http-api", "REST / HTTP API", listOf("http"), BE + FE, listOf("rest", "api", "http", "grpc", "openapi", "swagger", "эндпоинт", "endpoint")),
        Skill("reliability", "Идемпотентность и надёжная доставка", listOf("distributed"), BE, listOf("идемпотент", "idempot", "outbox", "ретра", "retry", "exactly")),
        Skill("messaging", "Брокеры сообщений (Kafka, RabbitMQ)", listOf("distributed"), BE, listOf("kafka", "кафк", "rabbitmq", "брокер", "nats")),
        Skill("caching", "Кэширование (Redis)", listOf("distributed"), BE, listOf("redis", "кэш", "кеш", "cache", "memcached")),
        Skill("distributed", "Распределённые системы и нагрузка", listOf("distributed"), BE, listOf("микросервис", "microservic", "распредел", "distributed", "highload", "нагрузк", "масштабир", "реплик", "шардир", "rate limit", "rps", "транзакций в секунд")),
        Skill("security", "Безопасность", listOf("security"), BE + FE, listOf("безопасн", "security", "owasp", "oauth", "jwt", "keycloak", "аутентификац", "авторизац", "шифрован", "pci")),
        Skill("algorithms", "Алгоритмы и структуры данных", listOf("algorithms"), BE + FE + DA, listOf("алгоритм", "algorithm", "структур данн", "сложност", "олимпиад", "leetcode")),
        Skill("python", "Python", listOf("python"), BE + DA, listOf("python", "питон", "django", "fastapi")),
        Skill("pandas", "pandas / NumPy", listOf("python"), DA, listOf("pandas", "numpy", "jupyter", "scipy")),
        Skill("statistics", "Статистика и вероятность", listOf("statistics"), DA + PR, listOf("статистик", "statistic", "гипотез", "вероятност", "распределен", "дисперс", "доверительн", "байес", "bayes", "перцентил", "медиан")),
        Skill("experiments", "A/B-тесты", listOf("experiments"), DA + PR, listOf("abtest", "эксперимент", "experiment", "сплит", "значимост", "pvalue", "размер выборк")),
        Skill("ml", "Машинное обучение", listOf("ml"), DA, listOf("машин обучен", "ml", "sklearn", "scikit", "catboost", "xgboost", "классификац", "регресс", "нейросет", "precision", "recall", "прогнозирован")),
        Skill("analytics-sql", "Аналитический SQL и хранилища", listOf("sql"), DA, listOf("оконн", "window", "clickhouse", "витрин", "dwh", "etl", "airflow", "dbt")),
        Skill("bi", "BI и дашборды", listOf("product-metrics"), DA + PR, listOf("дашборд", "dashboard", "tableau", "superset", "datalens", "power bi", "metabase")),
        Skill("product-analytics", "Продуктовые метрики", listOf("product-metrics"), DA + PR, listOf("retention", "удержан", "когорт", "cohort", "воронк", "funnel", "dau", "mau", "конверси", "conversion", "north star", "метрик", "amplitude", "mixpanel")),
        Skill("javascript", "JavaScript", listOf("javascript"), FE, listOf("javascript", "js", "es6", "ecmascript", "event loop", "промис", "promise", "async", "замыкан", "closure")),
        Skill("typescript", "TypeScript", listOf("typescript"), FE, listOf("typescript", "ts", "тайпскрипт")),
        Skill("react", "React", listOf("react"), FE, listOf("react", "реакт", "redux", "хук", "hook", "jsx", "next", "mobx", "zustand")),
        Skill("css", "HTML / CSS и вёрстка", listOf("css"), FE + DS, listOf("css", "html", "верст", "scss", "sass", "tailwind", "flexbox", "кроссбраузер", "блочн модел", "специфичност")),
        Skill("accessibility", "Доступность (WCAG)", listOf("accessibility"), FE + DS, listOf("доступност", "accessibility", "a11y", "wcag", "aria", "скринридер", "контраст")),
        Skill("web-performance", "Производительность фронтенда", listOf("web-performance"), FE, listOf("производительност", "performance", "vitals", "lighthouse", "lcp", "cls", "inp", "бандл", "bundle", "webpack", "vite", "ленив загрузк", "lazy")),
        Skill("unit-economics", "Юнит-экономика", listOf("unit-economics"), PR, listOf("юнит", "unit", "экономик", "ltv", "cac", "arpu", "arppu", "маржинальн", "окупаем", "payback", "монетизац", "monetiz", "выручк", "ценообраз", "цена", "цены", "цену", "pricing")),
        Skill("prioritization", "Приоритизация и цели", listOf("prioritization"), PR, listOf("приоритиз", "prioriti", "rice", "ice", "roadmap", "роадмап", "бэклог", "backlog", "okr", "kpi")),
        Skill("research", "Исследования пользователей", listOf("research"), PR + DS, listOf("custdev", "касдев", "интервью", "interview", "jtbd", "исследован", "research", "опрос", "survey", "персон")),
        Skill("figma", "Figma и дизайн-системы", listOf("design-systems"), DS, listOf("figma", "фигм", "дизайн систем", "design system", "токен", "ui kit", "компонент")),
        Skill("usability", "Юзабилити и UX", listOf("usability"), DS, listOf("юзабилит", "usability", "эвристик", "heuristic", "ux", "cjm", "сценари", "прототип", "prototyp", "sus")),
        Skill("layout", "Сетки и адаптивность", listOf("ui-layout"), DS + FE, listOf("сетк", "grid", "адаптив", "responsive", "ios", "android", "material", "касан")),
        Skill("typography", "Типографика и цвет", listOf("typography"), DS, listOf("типограф", "typograph", "шрифт", "font", "палитр", "цвет")),
    )

    /** Words that point at a specialization directly (title words); weighted ×3 in detection. */
    private val titleHints = mapOf(
        "backend" to listOf("backend", "бэкенд", "бекенд", "серверн"),
        "frontend" to listOf("frontend", "фронтенд", "фронт"),
        "data" to listOf("аналитик", "analyst", "data", "сайентист", "scientist"),
        "product" to listOf("product manager", "продакт", "менеджер продукт", "продуктов менеджер"),
        "design" to listOf("дизайнер", "designer", "ux ui", "ui ux"),
    )

    private val suffixes = listOf(
        "иями", "ями", "ами", "ией", "ого", "его", "ому", "ему", "ыми", "ими", "ость", "ости", "ах", "ях", "ов", "ев", "ей", "ий", "ый", "ой",
        "ых", "их", "ым", "им", "ая", "яя", "ое", "ее", "ые", "ие", "ом", "ем", "ам", "ям", "ую", "юю", "ия", "а", "я", "о", "е", "ы", "и", "у", "ю", "ь",
    ).sortedByDescending { it.length }

    /** Light stemming: drop one inflectional ending (Russian) or a plural "s" (English), keeping ≥ 4 letters. */
    fun stem(token: String): String {
        if (token.any { it in 'а'..'я' }) {
            val suffix = suffixes.firstOrNull { token.endsWith(it) && token.length - it.length >= 4 }
            return if (suffix != null) token.dropLast(suffix.length) else token
        }
        return if (token.length > 4 && token.endsWith("s") && !token.endsWith("ss")) token.dropLast(1) else token
    }

    fun tokens(text: String): List<String> = text.lowercase()
        .replace('ё', 'е')
        .replace(Regex("""[aа]\s*/\s*[bб](?:[\s-]*(?:тест|test)[a-zа-я]*)?"""), " abtest ")
        .replace("p-value", " pvalue ")
        .split(Regex("[^a-zа-я0-9]+"))
        .filter { it.isNotBlank() }
        .map(::stem)

    private fun matches(tokens: List<String>, index: Int, term: String): Boolean {
        val parts = term.split(' ')
        if (index + parts.size > tokens.size) return false
        return parts.indices.all { i ->
            val token = tokens[index + i]
            val part = parts[i]
            if (part.length <= 3 && part.all { it in 'a'..'z' || it in '0'..'9' }) token == part else token.startsWith(part)
        }
    }

    fun detect(text: String): List<DetectedSkill> {
        val tokens = tokens(text)
        return skills.mapNotNull { skill ->
            val matched = mutableListOf<String>()
            var hits = 0
            for (i in tokens.indices) for (term in skill.terms) if (matches(tokens, i, term)) {
                hits++
                matched += tokens.subList(i, i + term.split(' ').size).joinToString(" ")
            }
            if (hits == 0) null else DetectedSkill(skill, hits, matched.distinct().take(6))
        }.sortedByDescending { it.hits }
    }

    fun detectSpecialization(text: String, detected: List<DetectedSkill> = detect(text)): String? {
        val tokens = tokens(text)
        val score = mutableMapOf<String, Double>()
        for (d in detected) for (s in d.skill.specializations) score.merge(s, minOf(d.hits, 3).toDouble() / d.skill.specializations.size, Double::plus)
        for ((spec, hints) in titleHints) for (i in tokens.indices) if (hints.any { matches(tokens, i, it) }) score.merge(spec, 3.0, Double::plus)
        return score.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
    }

    fun blueprintFor(specialization: String, vacancyText: String?, length: Int = Cat.TEST_LENGTH): Blueprint {
        val pool = ItemBank.pool(specialization)
        val caps = pool.groupingBy { it.topic }.eachCount()
        val defaults = normalize(AssessmentCatalog.defaultTopicWeights[specialization].orEmpty().filterKeys { it in caps })
        val text = vacancyText?.takeIf { it.isNotBlank() }
        val detected = text?.let(::detect).orEmpty()
        // Each detected skill contributes min(hits, 3), split across its topics present in this pool.
        val vacancy = mutableMapOf<String, Double>()
        for (d in detected) {
            val topics = d.skill.topics.filter { it in caps }
            for (t in topics) vacancy.merge(t, minOf(d.hits, 3).toDouble() / topics.size, Double::plus)
        }
        val vacancyNorm = normalize(vacancy)
        val weights = if (vacancyNorm.isEmpty()) defaults else normalize(
            caps.keys.associateWith { (1 - VACANCY_SHARE) * (defaults[it] ?: 0.0) + VACANCY_SHARE * (vacancyNorm[it] ?: 0.0) },
        )
        return Blueprint(
            specialization = specialization,
            detectedSpecialization = text?.let { detectSpecialization(it, detected) },
            skills = detected.filter { d -> d.skill.topics.any { it in caps } },
            topicWeights = weights.filterValues { it > 0 }.toList().sortedByDescending { it.second }.toMap(),
            quotas = allocate(weights, caps, length),
            uncovered = detected.filter { d -> d.skill.topics.none { it in caps } },
        )
    }

    /**
     * Sainte-Laguë apportionment of `length` items to topics (highest weight / (allocated + 0.5) first),
     * never exceeding the number of families in a topic. Topics with zero weight only fill seats that
     * weighted topics cannot take (e.g. when they run out of families).
     */
    fun allocate(weights: Map<String, Double>, caps: Map<String, Int>, length: Int): Map<String, Int> {
        val seats = minOf(length, caps.values.sum())
        val allocation = caps.keys.associateWith { 0 }.toMutableMap()
        repeat(seats) {
            val topic = caps.keys.filter { allocation.getValue(it) < caps.getValue(it) }
                .maxWith(compareBy<String> { ((weights[it] ?: 0.0) + 1e-3) / (allocation.getValue(it) + 0.5) }.thenByDescending { it })
            allocation[topic] = allocation.getValue(topic) + 1
        }
        return allocation.filterValues { it > 0 }.toList().sortedByDescending { it.second }.toMap()
    }

    /** A few concrete instances for the preview: for the heaviest topics, the family closest to the target's anchor. */
    fun samples(blueprint: Blueprint, target: Grade, count: Int, rng: Random): List<Pair<ItemFamily, Generated>> {
        val pool = ItemBank.pool(blueprint.specialization)
        return blueprint.quotas.keys.take(count).mapNotNull { topic ->
            pool.filter { it.topic == topic }.minByOrNull { kotlin.math.abs(it.b - target.anchor) }
        }.map { it to it.instance(rng.nextLong()) }
    }

    private fun normalize(map: Map<String, Double>): Map<String, Double> {
        val total = map.values.sum()
        return if (total <= 0) emptyMap() else map.mapValues { it.value / total }
    }
}
