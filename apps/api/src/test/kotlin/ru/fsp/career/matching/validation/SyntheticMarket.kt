package ru.fsp.career.matching.validation

import ru.fsp.career.achievements.domain.FspPointsPolicy
import ru.fsp.career.matching.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Random
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Deterministic synthetic labour market for offline ranking validation.
 *
 * Every candidate has HIDDEN attributes that the platform never sees:
 *   true specialization, true grade, true competence q ∈ [0,1], true skills, true industry experience.
 * The platform only sees NOISY OBSERVATIONS of them, modelled after what a real job board receives:
 *   - assessed grade: equals the true grade with p = 0.85, otherwise off by one (measurement error);
 *   - test score: 35 + 55·q + N(0, 9²), clipped to 0..100 (a noisy measurement of competence);
 *   - self-reported stack: each true skill listed with p = 0.9, plus invented/inflated skills —
 *     50 % honest (0 fake), 30 % mild (1–2 fake), 20 % «keyword stuffers» (4–6 fake, biased to popular tags);
 *   - self-declared grade: true grade with p = 0.70, inflated by one with p = 0.25, deflated p = 0.05;
 *   - experience text in Russian: true skills mentioned with p = 0.75, fake ones with p = 0.20,
 *     industries mentioned with p = 0.85, plus generic phrases of the specialization; 8 % leave it empty;
 *   - FSP history: present with p = 0.10 + 0.45·q; better q → better places; absent for most candidates;
 *   - last activity: uniform 0..400 days ago (independent of competence).
 *
 * Ground truth for a (need, candidate) pair is computed ONLY from hidden attributes:
 *   u = gradeFit · (0.45·q + 0.40·skillCoverage + 0.15·industryMatch), 0 if the specialization differs,
 *   gradeFit: same 1.0, one above 0.7, one below 0.3, two away 0;
 *   label 3 if u ≥ 0.62, 2 if u ≥ 0.45, 1 if u ≥ 0.28, else 0. «Relevant» = label ≥ 2.
 * The label formula deliberately differs from the engine formula (no test score, no FSP, no text).
 */

data class SynthCandidate(
    val id: String,
    val specialization: String,
    val trueGrade: String,
    val competence: Double,
    val trueSkills: List<String>,
    val domains: List<String>,
    val assessedGrade: String,
    val testScore: Int,
    val declaredGrade: String,
    val selfStack: List<String>,
    val fakeSkills: List<String>,
    val stuffer: Boolean,
    val experience: String,
    val industry: String,
    val fsp: List<AchievementSignal>,
    val fspLinked: Boolean,
    val lastActiveDaysAgo: Int,
)

data class SynthNeed(val id: String, val title: String, val query: NeedQuery)

class SyntheticMarket(seed: Long = 20261006L, candidatesPerSpecialization: Int = 72, val now: Instant = NOW) {
    companion object {
        val NOW: Instant = Instant.parse("2026-10-01T00:00:00Z")

        val skillPools = mapOf(
            "backend" to listOf("PostgreSQL", "Docker", "Kotlin", "Java", "Python", "Go", "Kafka", "Kubernetes", "Redis", "Spring", "RabbitMQ", "ClickHouse", "gRPC"),
            "frontend" to listOf("TypeScript", "JavaScript", "React", "CSS", "Vue", "Redux", "Next.js", "Webpack", "Jest", "GraphQL", "Svelte", "Figma"),
            "data" to listOf("Python", "SQL", "Pandas", "Airflow", "Spark", "ClickHouse", "scikit-learn", "Tableau", "PyTorch", "dbt", "Kafka"),
            "product" to listOf("SQL", "Jira", "A/B-тесты", "Amplitude", "CJM", "CustDev", "Unit-экономика", "Miro", "Figma", "Python"),
            "design" to listOf("Figma", "Прототипирование", "Дизайн-системы", "UX-исследования", "Photoshop", "Illustrator", "Tilda", "After Effects", "Protopie", "Miro"),
        )

        private val specPhrases = mapOf(
            "backend" to listOf("высоконагруженные сервисы", "проектирование REST API", "микросервисная архитектура", "очереди сообщений", "оптимизация SQL-запросов", "покрытие кода тестами"),
            "frontend" to listOf("SPA-приложения", "адаптивная вёрстка", "библиотека UI-компонентов", "производительность интерфейса", "доступность интерфейсов", "код-ревью"),
            "data" to listOf("ETL-пайплайны", "витрины данных", "модели машинного обучения", "дашборды для бизнеса", "A/B-тесты и статистика", "качество данных"),
            "product" to listOf("продуктовые гипотезы", "метрики удержания", "приоритизация бэклога", "интервью с пользователями", "запуск новых функций", "стратегия и roadmap"),
            "design" to listOf("интерфейсы мобильных приложений", "дизайн-система", "пользовательские сценарии", "макеты и прототипы", "юзабилити-тестирование", "иллюстрации и айдентика"),
        )

        val industries = listOf("финтех", "e-commerce", "ритейл", "образование", "медицина", "госсектор", "логистика", "телеком", "игры")

        private val industryPhrases = mapOf(
            "финтех" to listOf("платёжный шлюз", "антифрод", "кредитный скоринг", "банковские транзакции", "мобильный банк"),
            "e-commerce" to listOf("интернет-магазин", "каталог товаров", "корзина и оформление заказа", "программа лояльности"),
            "ритейл" to listOf("розничная сеть", "продажи в магазинах", "управление складом", "кассовые системы"),
            "образование" to listOf("онлайн-курсы", "платформа обучения", "домашние задания", "онлайн-школа"),
            "медицина" to listOf("медицинская информационная система", "запись к врачу", "электронная медкарта", "медицинские изображения"),
            "госсектор" to listOf("портал госуслуг", "электронный документооборот", "заявления граждан", "межведомственные интеграции"),
            "логистика" to listOf("маршрутизация доставки", "трекинг грузов", "складская логистика", "курьерская доставка"),
            "телеком" to listOf("биллинг", "абонентская база", "оператор связи", "отток абонентов"),
            "игры" to listOf("игровой сервер", "матчмейкинг", "внутриигровой магазин", "игровое лобби"),
        )

        private val skillTemplates = listOf(
            "Работал с %s в продакшене.", "Использовал %s в ежедневной работе.", "Внедрил %s в команде.",
            "Отвечал за направление %s.", "Развивал проект на %s.",
        )
        private val fakeTemplates = listOf("Знаком с %s.", "Изучал %s на курсах.")

        val needs: List<SynthNeed> = listOf(
            need("N01", "Backend middle · платежи", "backend", "middle", "финтех", listOf("Kotlin", "PostgreSQL", "Kafka", "Docker"),
                "Команда платёжного шлюза ищет backend-разработчика уровня middle. Пишем микросервисы на Kotlin, храним транзакции в PostgreSQL, события передаём через Kafka, всё запускаем в Docker. Задачи: антифрод-проверки, обработка платежей, интеграции с банками."),
            need("N02", "Backend senior · каталог магазина", "backend", "senior", "e-commerce", listOf("Go", "PostgreSQL", "Redis", "Kubernetes"),
                "Ищем senior Go-разработчика в команду каталога товаров и корзины интернет-магазина. Высоконагруженные сервисы, кэширование в Redis, деплой в Kubernetes, оптимизация SQL-запросов к PostgreSQL. Нужен опыт проектирования архитектуры."),
            need("N03", "Backend junior · онлайн-курсы", "backend", "junior", "образование", listOf("Python", "PostgreSQL", "Docker"),
                "Нужен junior backend-разработчик на Python для образовательной платформы: API для онлайн-курсов и домашних заданий, база PostgreSQL, Docker. Обучим, важны база и желание расти."),
            need("N04", "Backend middle · доставка", "backend", "middle", "логистика", listOf("Java", "Spring", "PostgreSQL", "RabbitMQ"),
                "Сервис маршрутизации доставки: Java и Spring, очереди RabbitMQ, PostgreSQL. Ищем middle-разработчика, который будет развивать трекинг грузов и интеграции со складами."),
            need("N05", "Backend senior · биллинг", "backend", "senior", "телеком", listOf("Java", "Kafka", "ClickHouse", "Kubernetes"),
                "Биллинг для абонентской базы в десятки миллионов: потоковая обработка в Kafka, аналитическое хранилище ClickHouse, сервисы на Java в Kubernetes. Нужен senior с опытом высоких нагрузок."),
            need("N06", "Frontend middle · интернет-банк", "frontend", "middle", "финтех", listOf("TypeScript", "React", "Redux", "Jest"),
                "Интернет-банк для малого бизнеса: SPA на React и TypeScript, состояние в Redux, тесты на Jest. Ищем middle frontend-разработчика: формы платежей, выписки, дашборды."),
            need("N07", "Frontend junior · магазин", "frontend", "junior", "e-commerce", listOf("JavaScript", "CSS", "Vue"),
                "Junior frontend-разработчик в команду интернет-магазина: вёрстка карточек каталога товаров и корзины, Vue, адаптивная вёрстка на CSS, JavaScript."),
            need("N08", "Frontend senior · медицина", "frontend", "senior", "медицина", listOf("TypeScript", "React", "Next.js", "GraphQL"),
                "Медицинская информационная система: запись к врачу, электронная медкарта. Senior frontend на React и Next.js с TypeScript, данные через GraphQL, важны производительность интерфейса и доступность."),
            need("N09", "Frontend middle · игры", "frontend", "middle", "игры", listOf("TypeScript", "Svelte", "Webpack"),
                "Веб-клиент игрового сервиса: игровое лобби, матчмейкинг, внутриигровой магазин. Svelte и TypeScript, сборка Webpack. Нужен middle-разработчик, который любит анимацию интерфейса."),
            need("N10", "Frontend middle · госуслуги", "frontend", "middle", "госсектор", listOf("TypeScript", "React", "CSS", "Figma"),
                "Региональный портал госуслуг: формы заявлений граждан, личный кабинет, электронный документооборот. React и TypeScript, вёрстка по макетам из Figma, доступность интерфейсов для всех пользователей."),
            need("N11", "Data middle · скоринг", "data", "middle", "финтех", listOf("Python", "SQL", "scikit-learn", "Airflow"),
                "Команда кредитного скоринга ищет middle data scientist: модели машинного обучения на Python и scikit-learn, признаки из SQL, пайплайны в Airflow. Антифрод и скоринг заявок."),
            need("N12", "Data senior · DWH магазина", "data", "senior", "e-commerce", listOf("Spark", "Airflow", "ClickHouse", "Python"),
                "Senior data engineer для интернет-магазина: ETL-пайплайны на Spark и Airflow, витрины данных в ClickHouse, Python. Данные о заказах, каталоге товаров и программе лояльности."),
            need("N13", "Data junior · розница", "data", "junior", "ритейл", listOf("SQL", "Python", "Tableau"),
                "Junior-аналитик данных в розничную сеть: отчёты по продажам в магазинах и складу, SQL-запросы, дашборды в Tableau, немного Python."),
            need("N14", "Data middle · медизображения", "data", "middle", "медицина", listOf("Python", "PyTorch", "SQL"),
                "Анализ медицинских изображений и электронных медкарт: модели машинного обучения на PyTorch, Python, выборки на SQL. Ищем middle ML-инженера."),
            need("N15", "Data middle · телеком", "data", "middle", "телеком", listOf("Python", "Pandas", "SQL", "ClickHouse"),
                "Аналитика абонентской базы оператора связи: отток абонентов, биллинг, сегментация. Python, Pandas, SQL, ClickHouse. Middle-аналитик с опытом A/B-тестов и статистики."),
            need("N16", "Product middle · мобильный банк", "product", "middle", "финтех", listOf("SQL", "A/B-тесты", "Amplitude", "Jira"),
                "Продакт-менеджер мобильного банка: продуктовые гипотезы, A/B-тесты, метрики в Amplitude, приоритизация бэклога в Jira, SQL для самостоятельной аналитики."),
            need("N17", "Product senior · онлайн-школа", "product", "senior", "образование", listOf("CJM", "CustDev", "Unit-экономика", "SQL"),
                "Senior product manager в онлайн-школу: интервью с пользователями (CustDev, CJM), unit-экономика онлайн-курсов, стратегия и roadmap платформы обучения."),
            need("N18", "Product junior · оформление заказа", "product", "junior", "e-commerce", listOf("Jira", "SQL", "Miro"),
                "Junior продакт в команду оформления заказа интернет-магазина: сбор требований, бэклог в Jira, схемы пользовательских сценариев в Miro, базовый SQL."),
            need("N19", "Product middle · доставка", "product", "middle", "логистика", listOf("SQL", "A/B-тесты", "Unit-экономика", "Jira"),
                "Продукт для трекинга грузов и курьерской доставки: метрики удержания клиентов, unit-экономика доставки, A/B-тесты, работа с командой разработки в Jira."),
            need("N20", "Design middle · мобильный банк", "design", "middle", "финтех", listOf("Figma", "Дизайн-системы", "Прототипирование"),
                "Продуктовый дизайнер в мобильный банк: интерфейсы платежей и переводов, развитие дизайн-системы в Figma, прототипирование и проверка гипотез."),
            need("N21", "Design senior · магазин", "design", "senior", "e-commerce", listOf("Figma", "UX-исследования", "Дизайн-системы", "Protopie"),
                "Senior UX/UI-дизайнер для интернет-магазина: UX-исследования покупателей, дизайн-система, сложные прототипы в Protopie, наставничество дизайнеров."),
            need("N22", "Design junior · онлайн-курсы", "design", "junior", "образование", listOf("Figma", "Illustrator", "Tilda"),
                "Junior-дизайнер на образовательную платформу: лендинги онлайн-курсов на Tilda, иллюстрации в Illustrator, интерфейсы в Figma."),
            need("N23", "Design middle · игры", "design", "middle", "игры", listOf("Figma", "After Effects", "Photoshop"),
                "Дизайнер интерфейсов для игр: игровое лобби, внутриигровой магазин, анимация интерфейсов в After Effects, ассеты в Photoshop, макеты в Figma."),
            need("N24", "Design middle · медицина", "design", "middle", "медицина", listOf("Figma", "UX-исследования", "Прототипирование"),
                "Дизайнер медицинской информационной системы: сценарии записи к врачу, интервью с врачами и пациентами, UX-исследования, прототипы в Figma, доступность интерфейсов."),
        )

        private fun need(id: String, title: String, spec: String, grade: String, industry: String, stack: List<String>, description: String) =
            SynthNeed(id, title, NeedQuery(spec, grade, industry, stack, description))

        private val specIntro = mapOf(
            "backend" to "backend-разработке", "frontend" to "frontend-разработке", "data" to "анализе данных",
            "product" to "управлении продуктом", "design" to "дизайне интерфейсов",
        )
        private val fspEvents = listOf(
            "Чемпионат России по спортивному программированию" to "federal",
            "Кубок России по спортивному программированию" to "federal",
            "Всероссийский хакатон по продуктовому программированию" to "federal",
            "Региональный чемпионат по спортивному программированию" to "regional",
            "Первенство региона по спортивному программированию" to "regional",
            "Международные соревнования по спортивному программированию" to "international",
        )
    }

    private val rng = Random(seed)
    val candidates: List<SynthCandidate> = skillPools.keys.flatMap { spec ->
        (1..candidatesPerSpecialization).map { n -> generate(spec, "$spec-${n.toString().padStart(3, '0')}") }
    }

    private fun pick(weights: List<Double>): Int {
        var r = rng.nextDouble() * weights.sum()
        weights.forEachIndexed { i, w -> r -= w; if (r <= 0) return i }
        return weights.lastIndex
    }

    /** Popularity-weighted sample without replacement (popular tags are both more common and more often faked). */
    private fun sampleSkills(pool: List<String>, count: Int, exclude: Set<String> = emptySet()): List<String> {
        val available = pool.filter { it !in exclude }.toMutableList()
        val out = mutableListOf<String>()
        repeat(min(count, available.size)) {
            val index = pick(available.map { s -> 1.0 / (1.0 + 0.25 * pool.indexOf(s)) })
            out += available.removeAt(index)
        }
        return out
    }

    private fun shiftGrade(grade: String, delta: Int) = GRADES[(GRADES.indexOf(grade) + delta).coerceIn(0, 2)]

    private fun generate(spec: String, id: String): SynthCandidate {
        val trueGrade = GRADES[pick(listOf(0.35, 0.40, 0.25))]
        val q = (rng.nextDouble() + rng.nextDouble()) / 2.0
        val pool = skillPools.getValue(spec)
        val skillCount = when (trueGrade) { "junior" -> 3 + rng.nextInt(2); "middle" -> 4 + rng.nextInt(2); else -> 5 + rng.nextInt(2) }
        val trueSkills = sampleSkills(pool, skillCount)
        val domains = sampleDomains(if (rng.nextDouble() < 0.4) 2 else 1)

        val assessedGrade = if (rng.nextDouble() < 0.85) trueGrade else shiftGrade(trueGrade, if (rng.nextBoolean()) 1 else -1)
        val testScore = (35 + 55 * q + rng.nextGaussian() * 9).roundToInt().coerceIn(0, 100)
        val declaredGrade = when (pick(listOf(0.70, 0.25, 0.05))) { 0 -> trueGrade; 1 -> shiftGrade(trueGrade, 1); else -> shiftGrade(trueGrade, -1) }

        val profile = pick(listOf(0.5, 0.3, 0.2))
        val fakeCount = when (profile) { 0 -> 0; 1 -> 1 + rng.nextInt(2); else -> 4 + rng.nextInt(3) }
        val fakes = sampleSkills(pool, fakeCount, exclude = trueSkills.toSet())
        val listed = trueSkills.filter { rng.nextDouble() < 0.9 }
        val selfStack = (listed + fakes).shuffled(rng)

        val experience = if (rng.nextDouble() < 0.08) "" else buildList {
            val years = when (trueGrade) { "junior" -> 1 + rng.nextInt(2); "middle" -> 2 + rng.nextInt(4); else -> 5 + rng.nextInt(6) }
            add("Опыт $years г. в ${specIntro.getValue(spec)}.")
            trueSkills.filter { rng.nextDouble() < 0.75 }.forEach { add(skillTemplates[rng.nextInt(skillTemplates.size)].format(it)) }
            fakes.filter { rng.nextDouble() < 0.20 }.forEach { add(fakeTemplates[rng.nextInt(fakeTemplates.size)].format(it)) }
            domains.filter { rng.nextDouble() < 0.85 }.forEach { d ->
                val phrases = industryPhrases.getValue(d).shuffled(rng).take(2)
                add("Проект в сфере «$d»: ${phrases.joinToString(", ")}.")
            }
            specPhrases.getValue(spec).shuffled(rng).take(1 + rng.nextInt(2)).forEach { add("Занимался темой: $it.") }
        }.shuffled(rng).joinToString(" ")

        val hasFsp = rng.nextDouble() < 0.10 + 0.45 * q
        val fsp = if (!hasFsp) emptyList() else (1..(1 + rng.nextInt(3))).map { n -> fspResult(q, n) }
        return SynthCandidate(
            id = id, specialization = spec, trueGrade = trueGrade, competence = q, trueSkills = trueSkills, domains = domains,
            assessedGrade = assessedGrade, testScore = testScore, declaredGrade = declaredGrade, selfStack = selfStack,
            fakeSkills = fakes, stuffer = profile == 2, experience = experience,
            industry = if (rng.nextDouble() < 0.8) domains.first() else "",
            fsp = fsp, fspLinked = hasFsp || rng.nextDouble() < 0.05, lastActiveDaysAgo = rng.nextInt(401),
        )
    }

    private fun sampleDomains(count: Int): List<String> = industries.shuffled(rng).take(count)

    private fun fspResult(q: Double, n: Int): AchievementSignal {
        val (event, level) = fspEvents[pick(listOf(0.22, 0.12, 0.08, 0.30, 0.20, 0.08))]
        val roll = rng.nextDouble()
        val result = when {
            roll < 0.15 * q -> "winner"
            roll < 0.40 * q -> "prize"
            roll < 0.30 + 0.35 * q -> "finalist"
            else -> "participant"
        }
        val place = when (result) { "winner" -> 1; "prize" -> 2 + rng.nextInt(2); "finalist" -> 4 + rng.nextInt(12); else -> null }
        val date = now.atZone(ZoneOffset.UTC).toLocalDate().minusDays(rng.nextInt(5 * 365).toLong())
        return AchievementSignal(
            title = "$event ${date.year} (синтетика) #$n", issuer = "синтетический реестр", source = "fsp",
            points = FspPointsPolicy.points(level, result), eventName = "$event ${date.year} (синтетика)",
            eventDate = date, level = level, place = place, result = result,
        )
    }

    /** Hidden-attribute utility u ∈ [0,1] of a candidate for a need (ground truth, see header). */
    fun utility(need: NeedQuery, c: SynthCandidate): Double {
        if (c.specialization != need.specialization) return 0.0
        val gradeFit = when (GRADES.indexOf(c.trueGrade) - GRADES.indexOf(need.grade)) { 0 -> 1.0; 1 -> 0.7; -1 -> 0.3; else -> 0.0 }
        val skillFit = need.stack.count { it in c.trueSkills }.toDouble() / max(1, need.stack.size)
        val domainFit = if (need.industry.isBlank()) 0.5 else if (need.industry in c.domains) 1.0 else 0.0
        return gradeFit * (0.45 * c.competence + 0.40 * skillFit + 0.15 * domainFit)
    }

    fun label(need: NeedQuery, c: SynthCandidate): Int = utility(need, c).let { u ->
        when { u >= 0.62 -> 3; u >= 0.45 -> 2; u >= 0.28 -> 1; else -> 0 }
    }

    /** What the platform is allowed to see: noisy observations only, never the hidden attributes. */
    fun signals(c: SynthCandidate) = CandidateSignals(
        id = c.id,
        displayName = c.id,
        specialization = c.specialization,
        industry = c.industry,
        assessedGrade = c.assessedGrade,
        testScore = c.testScore,
        assessedAt = now.minus(30, ChronoUnit.DAYS),
        selfReportedSkills = c.selfStack,
        profileText = c.experience,
        achievements = c.fsp,
        fspLinked = c.fspLinked,
        lastActiveAt = now.minus(c.lastActiveDaysAgo.toLong(), ChronoUnit.DAYS),
    )
}
