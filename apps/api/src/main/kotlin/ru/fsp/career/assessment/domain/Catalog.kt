package ru.fsp.career.assessment.domain

/** Reference data for the survey that precedes every test (specializations, industries, roles, formats, grades). */
object AssessmentCatalog {
    data class Entry(val id: String, val label: String, val description: String = "")

    val specializations = listOf(
        Entry("backend", "Backend-разработка", "Серверная логика, API, базы данных, надёжность"),
        Entry("frontend", "Frontend-разработка", "Веб-интерфейсы: JavaScript/TypeScript, React, CSS, доступность"),
        Entry("data", "Данные и аналитика", "SQL, Python, статистика, эксперименты, машинное обучение"),
        Entry("product", "Продукт", "Метрики, юнит-экономика, A/B-тесты, приоритизация, исследования"),
        Entry("design", "Дизайн", "UX/UI, юзабилити, исследования, дизайн-системы, доступность"),
    )

    val industries = listOf(
        Entry("fintech", "Финтех и банки"),
        Entry("ecommerce", "E-commerce и ритейл"),
        Entry("edtech", "Образование (EdTech)"),
        Entry("healthtech", "Медицина (HealthTech)"),
        Entry("gamedev", "Игры (GameDev)"),
        Entry("telecom", "Телеком"),
        Entry("govtech", "Госсектор (GovTech)"),
        Entry("logistics", "Логистика и транспорт"),
        Entry("media", "Медиа и развлечения"),
        Entry("industrial", "Промышленность и энергетика"),
        Entry("other", "Другая отрасль"),
    )

    /** Lowercase stems that identify each industry in free text ("банк", "маркетплейс", "доставка", …). */
    private val industryStems = mapOf(
        "fintech" to listOf("fintech", "финтех", "банк", "платеж", "платёж", "финанс", "страхов"),
        "ecommerce" to listOf("ecommerce", "e-commerce", "ритейл", "retail", "магазин", "маркетплейс", "торгов"),
        "edtech" to listOf("edtech", "образован", "онлайн-школ", "обучен", "курс"),
        "healthtech" to listOf("healthtech", "медицин", "здравоохран", "клиник"),
        "gamedev" to listOf("gamedev", "игр", "game"),
        "telecom" to listOf("telecom", "телеком", "связь", "оператор"),
        "govtech" to listOf("govtech", "госсектор", "госуслуг", "государств"),
        "logistics" to listOf("logistics", "логистик", "доставк", "транспорт", "склад"),
        "media" to listOf("media", "медиа", "развлечен"),
        "industrial" to listOf("industrial", "промышлен", "энерг", "производств"),
    )

    /**
     * Maps a catalog id, its label or free text ("финтех", "Банки", "E-commerce") to a catalog industry id.
     * Candidates choose the industry from this catalog in the survey while employers may type it, so matching
     * compares industries through this key instead of raw strings.
     */
    fun industryId(text: String): String? {
        val value = text.trim().lowercase()
        if (value.isEmpty()) return null
        industries.firstOrNull { it.id == value || it.label.lowercase() == value }?.let { return it.id }
        return industryStems.entries.firstOrNull { (_, stems) -> stems.any { value.contains(it) } }?.key
    }

    fun industryStemsFor(id: String): List<String> = industryStems[id].orEmpty()

    /** Human label for an industry id or free text (free text is returned as is). */
    fun industryLabel(text: String): String =
        industryId(text)?.let { id -> industries.first { it.id == id }.label } ?: text

    val teamRoles = listOf(
        Entry("individual", "Исполнитель"),
        Entry("mentor", "Наставник"),
        Entry("tech-lead", "Техлид / ведущий специалист"),
        Entry("architect", "Архитектор"),
        Entry("team-lead", "Руководитель команды"),
        Entry("researcher", "Исследователь"),
    )

    val workFormats = listOf(
        Entry("office", "Офис"),
        Entry("hybrid", "Гибрид"),
        Entry("remote", "Удалённо"),
    )

    val grades = listOf(
        Entry("junior", "Junior", "Решает типовые задачи по образцу и под присмотром; обычно до 1–2 лет опыта."),
        Entry("middle", "Middle", "Самостоятельно решает задачи своей области, понимает компромиссы; обычно 2–5 лет."),
        Entry("senior", "Senior", "Проектирует решения, предвидит риски, отвечает за качество и наставляет; обычно 5+ лет."),
    )

    fun has(list: List<Entry>, id: String) = list.any { it.id == id }
    fun label(list: List<Entry>, id: String) = list.firstOrNull { it.id == id }?.label ?: id

    /**
     * Default content blueprint per specialization (relative topic weights) — used when the candidate
     * does not pick a vacancy and as the 40 % "core" share when they do. Topics missing here can still
     * appear when a vacancy mentions them, as long as the specialization's pool has families for them.
     */
    val defaultTopicWeights: Map<String, Map<String, Double>> = mapOf(
        "backend" to mapOf("sql" to 2.0, "databases" to 1.5, "http" to 1.5, "distributed" to 1.5, "algorithms" to 1.5,
            "kotlin" to 1.0, "concurrency" to 1.0, "security" to 1.0, "python" to 0.5),
        "frontend" to mapOf("javascript" to 2.5, "css" to 2.0, "react" to 2.0, "typescript" to 1.0, "accessibility" to 1.0,
            "web-performance" to 1.0, "http" to 1.0, "algorithms" to 1.0, "ui-layout" to 0.5),
        "data" to mapOf("sql" to 2.5, "statistics" to 2.0, "python" to 1.5, "experiments" to 1.5, "ml" to 1.5,
            "product-metrics" to 0.5, "algorithms" to 0.5),
        "product" to mapOf("product-metrics" to 2.5, "unit-economics" to 2.0, "experiments" to 2.0, "prioritization" to 1.5,
            "research" to 1.5, "statistics" to 0.5),
        "design" to mapOf("usability" to 2.5, "ui-layout" to 2.0, "typography" to 1.5, "accessibility" to 1.5,
            "research" to 1.5, "design-systems" to 1.0, "css" to 0.5),
    )
}

/** Demo vacancies used to show how the system turns a job description into a test blueprint. */
data class DemoVacancy(
    val id: String, val title: String, val company: String, val specialization: String,
    val industry: String, val grade: String, val text: String,
)

object DemoVacancies {
    val all = listOf(
        DemoVacancy(
            "fintech-backend", "Backend-разработчик (Kotlin) — платёжный сервис", "Финтех-компания «Платёжный шлюз»",
            "backend", "fintech", "middle",
            """
            Мы разрабатываем платёжный шлюз, который обрабатывает до 3 000 транзакций в секунду. Ищем backend-разработчика уровня middle+.
            Задачи: развивать сервисы приёма платежей на Kotlin (Ktor, Spring Boot); проектировать REST API для мерчантов;
            обеспечивать идемпотентность платёжных операций и надёжную доставку событий через Kafka (transactional outbox);
            оптимизировать запросы и индексы PostgreSQL, разбираться с блокировками и уровнями изоляции транзакций;
            кэшировать справочники в Redis; участвовать в код-ревью.
            Требования: опыт коммерческой разработки на Kotlin или Java от 2 лет; уверенный SQL и понимание транзакций;
            опыт с корутинами и многопоточностью; понимание принципов безопасности (OAuth 2.0, PCI DSS — плюс).
            """.trimIndent(),
        ),
        DemoVacancy(
            "marketplace-frontend", "Frontend-разработчик (React/TypeScript) — личный кабинет продавца", "Маркетплейс «Полка»",
            "frontend", "ecommerce", "middle",
            """
            Делаем личный кабинет для 200 тысяч продавцов маркетплейса: аналитика продаж, управление товарами, финансы.
            Чем предстоит заниматься: разрабатывать интерфейсы на React и TypeScript; верстать адаптивные экраны (CSS, flexbox, grid);
            следить за производительностью — Web Vitals, ленивая загрузка, разбиение бандла; работать с REST API и кэшированием на клиенте;
            делать интерфейсы доступными (WCAG, клавиатурная навигация, ARIA).
            Мы ждём: уверенное знание JavaScript (замыкания, event loop, промисы, async/await), опыт с хуками React и управлением состоянием,
            понимание специфичности CSS и блочной модели.
            """.trimIndent(),
        ),
        DemoVacancy(
            "edtech-analyst", "Продуктовый аналитик (SQL/Python)", "Онлайн-школа «Код и Числа»",
            "data", "edtech", "middle",
            """
            Онлайн-школа программирования ищет продуктового аналитика. Вы будете считать retention и когортный анализ учеников,
            строить воронки от регистрации до оплаты, проектировать и анализировать A/B-тесты (дизайн эксперимента, размер выборки,
            статистическая значимость), собирать витрины данных в ClickHouse.
            Инструменты: SQL (оконные функции, CTE), Python (pandas, scipy), Jupyter, DataLens.
            Будет плюсом: модели прогнозирования оттока (scikit-learn, catboost), понимание метрик качества классификации
            (precision, recall), опыт с байесовским подходом.
            """.trimIndent(),
        ),
        DemoVacancy(
            "subscription-pm", "Product Manager — подписочный сервис", "Сервис аудиокниг «Слушай»",
            "product", "media", "senior",
            """
            Ищем продакт-менеджера, который отвечает за рост платной подписки. Задачи: управлять юнит-экономикой (LTV, CAC, ARPU, окупаемость),
            ставить цели по OKR и приоритизировать бэклог (RICE), формулировать гипотезы и запускать A/B-тесты вместе с аналитиком,
            проводить интервью с пользователями (JTBD, custdev), выбирать North Star метрику и следить за retention и воронкой оплаты,
            тестировать изменения цены.
            Опыт: от 3 лет в продуктовом менеджменте B2C, уверенная работа с метриками и экспериментами.
            """.trimIndent(),
        ),
        DemoVacancy(
            "bank-designer", "Продуктовый дизайнер — мобильный банк", "Банк «Северный»",
            "design", "fintech", "middle",
            """
            Проектируем мобильный банк для 5 млн клиентов. Ищем продуктового дизайнера.
            Задачи: проектировать пользовательские сценарии и интерфейсы в Figma; развивать дизайн-систему (компоненты, токены, состояния);
            проводить юзабилити-тестирования и интервью, применять эвристики при аудите интерфейсов;
            соблюдать требования доступности WCAG (контраст, размеры зон касания);
            работать с модульной сеткой, типографикой и адаптивностью под iOS и Android.
            Будет плюсом: умение считать SUS и использовать количественные исследования.
            """.trimIndent(),
        ),
    )

    fun find(id: String): DemoVacancy? = all.firstOrNull { it.id == id }
}
