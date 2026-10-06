package ru.fsp.career

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import ru.fsp.career.assessment.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import ru.fsp.career.fsp.MockFspRegistry
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

/**
 * Idempotent synthetic demo data for the public prototype:
 * `java -cp "/app/lib/<all jars>" ru.fsp.career.DemoSeedKt` with the API's DATABASE_ environment (see deploy.sh).
 *
 * Creates a bank of assessed candidates (a third linked to the synthetic FSP registry), three fictional
 * companies with vacancies and weekly tasks, and — when the Keycloak subjects of the demo logins are passed in
 * DEMO_EMPLOYER_SUBJECT / DEMO_ASSESSED_SUBJECT — a ready employer (need, invitations, applications, task
 * answers) and an assessed candidate with an incoming invitation. Seed-only accounts use `demo-seed:*` subjects
 * and cannot log in. All people, companies and FSP results are fictional and marked «демо».
 */
fun main() = runBlocking {
    fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
    Database(env("DATABASE_URL")!!, env("DATABASE_USER")!!, env("DATABASE_PASSWORD")!!).use { db ->
        DemoSeed(db).run(env("DEMO_EMPLOYER_SUBJECT"), env("DEMO_ASSESSED_SUBJECT"))
    }
}

@Serializable private data class VacancyFixture(
    val title: String, val description: String, val specialization: String, val grade: String,
    val techStack: List<String>, val salaryMinRub: Int, val salaryMaxRub: Int, val workFormat: String, val city: String,
)
@Serializable private data class TaskFixture(val title: String, val body: String, val specialization: String, val grade: String? = null)

private data class Track(val skills: List<String>, val projects: List<String>, val duties: List<String>, val roles: List<String>)

internal class DemoSeed(private val db: Database) {
    private val rnd = Random(2026)
    private val now = OffsetDateTime.now(ZoneOffset.UTC)
    private val registry = MockFspRegistry(db)
    private val json = Json { ignoreUnknownKeys = true }

    // FSP-100001…100003 stay free so a visitor can register with that registry e-mail and link the ID live;
    // FSP-100004 belongs to the assessed demo login.
    private val reservedFsp = setOf("FSP-100001", "FSP-100002", "FSP-100003", "FSP-100004")

    // Survey catalog ids (AssessmentCatalog.industries), the same values real candidates get from the survey.
    private val industries = listOf("fintech", "ecommerce", "edtech", "logistics", "govtech", "telecom", "healthtech", "gamedev")

    private val tracks = mapOf(
        "backend" to Track(
            listOf("Kotlin", "Java", "Spring", "Ktor", "Go", "Python", "PostgreSQL", "Kafka", "Redis", "Docker", "Kubernetes", "gRPC", "ClickHouse"),
            listOf("платёжного шлюза", "сервиса заказов", "биллинга", "API мобильного приложения", "системы уведомлений", "каталога товаров"),
            listOf("проектирование REST и gRPC API", "оптимизация SQL-запросов и индексов", "переход с монолита на микросервисы",
                "идемпотентная обработка событий из Kafka", "нагрузочное тестирование и профилирование", "настройка CI/CD и мониторинга"),
            listOf("Backend-разработчик", "Разработчик микросервисов"),
        ),
        "frontend" to Track(
            listOf("TypeScript", "JavaScript", "React", "Vue", "Svelte", "Next.js", "HTML", "CSS", "Redux", "Vite", "Jest", "Figma"),
            listOf("личного кабинета", "интернет-магазина", "админ-панели", "дизайн-системы", "лендингов", "дашборда аналитики"),
            listOf("вёрстка адаптивных интерфейсов", "перевод проекта на TypeScript", "оптимизация загрузки и Core Web Vitals",
                "доступность (a11y) и клавиатурная навигация", "покрытие компонентов тестами", "SSR и кеширование"),
            listOf("Frontend-разработчик", "UI-разработчик"),
        ),
        "data" to Track(
            listOf("SQL", "Python", "Pandas", "ClickHouse", "PostgreSQL", "Airflow", "Spark", "Tableau", "Power BI", "A/B-тесты", "scikit-learn", "dbt"),
            listOf("витрин продаж", "модели оттока", "скоринговой модели", "отчётности для руководства", "DWH", "сквозной аналитики"),
            listOf("построение ETL на Airflow", "дизайн и анализ A/B-тестов", "сегментация клиентов", "прогноз спроса",
                "автоматизация отчётов", "контроль качества данных"),
            listOf("Аналитик данных", "Data Scientist", "Продуктовый аналитик"),
        ),
        "product" to Track(
            listOf("Jira", "SQL", "A/B-тесты", "Amplitude", "CJM", "Customer Development", "Figma", "Unit-экономика", "Roadmap", "Confluence"),
            listOf("мобильного банка", "маркетплейса", "онлайн-школы", "сервиса доставки", "B2B SaaS", "программы лояльности"),
            listOf("приоритизация бэклога по RICE", "запуск MVP за 6 недель", "рост конверсии воронки оформления",
                "интервью с пользователями", "работа с метриками удержания", "координация трёх команд разработки"),
            listOf("Product manager", "Product owner"),
        ),
        "design" to Track(
            listOf("Figma", "UX-исследования", "Прототипирование", "Дизайн-системы", "Adobe Illustrator", "Motion", "Accessibility", "Tilda", "Miro"),
            listOf("мобильного приложения", "дизайн-системы", "личного кабинета", "интернет-магазина", "онбординга", "B2B-платформы"),
            listOf("юзабилити-тестирование", "создание и поддержка UI-кита", "редизайн ключевых сценариев",
                "адаптивные макеты", "проработка доступности", "передача макетов в разработку"),
            listOf("Продуктовый дизайнер", "UX/UI-дизайнер"),
        ),
    )

    private val maleNames = listOf("Александр", "Дмитрий", "Максим", "Илья", "Кирилл", "Никита", "Артём", "Егор", "Роман", "Павел", "Глеб", "Тимур", "Олег", "Виктор", "Степан")
    private val femaleNames = listOf("Анастасия", "Дарья", "Полина", "Елизавета", "Ксения", "Виктория", "Алина", "Софья", "Юлия", "Вероника", "Ольга", "Надежда", "Марина", "Ирина", "Евгения")
    private val surnames = listOf("Лебедев", "Новиков", "Фёдоров", "Михайлов", "Орлов", "Никитин", "Захаров", "Зайцев", "Павлов", "Семёнов", "Голубев", "Виноградов", "Богданов", "Воробьёв", "Тарасов", "Белов", "Комаров", "Киселёв", "Макаров", "Андреев")
    private val softSkillPool = listOf("Коммуникация", "Ответственность", "Наставничество", "Работа в команде", "Самоорганизация", "Критическое мышление", "Публичные выступления", "Тайм-менеджмент")

    suspend fun run(employerSubject: String?, assessedSubject: String?) {
        val bank = db.tx {
            if (Accounts.selectAll().where { Accounts.subject like "demo-seed:%" }.any()) null
            else seedBank()
        }
        if (bank == null) println("Demo bank already present — skipped")
        else {
            linkFsp(bank.mapNotNull { (id, fspId) -> fspId?.let { id to it } })
            println("Demo bank: ${bank.size} candidates, ${bank.count { it.second != null }} linked to the synthetic FSP registry")
        }
        // Seeded links happened "now"; date them with the profile so freshness in the demo looks realistic.
        db.tx {
            exec("UPDATE candidate_profiles SET fsp_linked_at = updated_at WHERE fsp_linked_at > updated_at " +
                "AND account_id IN (SELECT id FROM accounts WHERE subject LIKE 'demo-seed:%')")
        }
        db.tx { seedCompanies() }
        if (employerSubject != null) db.tx { seedDemoEmployer(employerSubject) }
        if (assessedSubject != null) {
            db.tx { seedAssessedCandidate(assessedSubject) }
            db.tx { boundRole(assessedSubject)?.first?.let { realAssessment(it) } }
            registry.lookup("FSP-100004")?.let { record ->
                db.tx { boundRole(assessedSubject)?.first?.let { id -> val linked = CandidateProfiles.selectAll().where { CandidateProfiles.accountId eq id }.single()[CandidateProfiles.fspId]
                    if (linked == null) importFsp(id, record, registry) } }
            }
        }
        println("Demo seed done")
    }

    // ---- Candidate bank ----

    /** Returns the created candidates with the registry FSP ID each one should be linked to (if any). */
    private fun Transaction.seedBank(): List<Pair<UUID, String?>> {
        val registryPeople = FspRegistryParticipants.selectAll().orderBy(FspRegistryParticipants.fspId).toList()
            .filter { it[FspRegistryParticipants.fspId] !in reservedFsp }
        var nextFsp = 0
        return tracks.keys.flatMap { spec ->
            (1..18).map { n ->
                // Every third candidate is an FSP participant: name and e-mail come from the synthetic registry.
                val fsp = if (n % 3 == 0 && nextFsp < registryPeople.size) registryPeople[nextFsp++] else null
                val name = fsp?.get(FspRegistryParticipants.fullName) ?: run {
                    val female = rnd.nextBoolean()
                    val surname = surnames.random(rnd)
                    "${(if (female) femaleNames else maleNames).random(rnd)} ${if (female) surname + "а" else surname}"
                }
                val email = fsp?.get(FspRegistryParticipants.email) ?: "${spec}.${n}@demo.fsp.local"
                val id = createAccount("demo-seed:$spec-$n", "candidate")
                fillCandidate(id, name, email, spec, gradeFor(n), fspCandidate = fsp != null)
                id to fsp?.get(FspRegistryParticipants.fspId)
            }
        }
    }

    private fun gradeFor(n: Int) = when (n % 8) { 0, 1, 2 -> "junior"; 3, 4, 5 -> "middle"; else -> "senior" }

    private fun Transaction.fillCandidate(id: UUID, name: String, email: String, spec: String, grade: String, fspCandidate: Boolean, attempt: Boolean = true) {
        val track = tracks.getValue(spec)
        val years = when (grade) { "junior" -> rnd.nextInt(0, 2); "middle" -> rnd.nextInt(2, 5); else -> rnd.nextInt(5, 11) }
        val stack = track.skills.shuffled(rnd).take(rnd.nextInt(4, 7))
        val industry = industries.random(rnd)
        val duties = track.duties.shuffled(rnd).take(3)
        val experience = buildString {
            append("${if (years == 0) "Меньше года" else "$years ${yearsWord(years)}"} опыта: разработка и развитие ${track.projects.random(rnd)} ")
            append("в компании из сферы «${AssessmentCatalog.industryLabel(industry)}». Основной стек — ${stack.take(3).joinToString(", ")}. ")
            append("Задачи: ${duties.joinToString("; ")}. ")
            if (fspCandidate) append("Участвую в соревнованиях по спортивному программированию.")
        }
        val history = (0 until (if (grade == "junior") 1 else rnd.nextInt(1, 4))).map { i ->
            CvEntry(
                company = "ООО «${listOf("Северный код", "Точка роста", "Цифровые решения", "Облачный двор", "Квант-Софт", "Ясный путь").random(rnd)}» (демо)",
                role = track.roles.random(rnd),
                startDate = "${2025 - i * 2 - rnd.nextInt(0, 2)}",
                endDate = if (i == 0) "по н.в." else "${2025 - i * 2 + 1}",
                description = track.duties.random(rnd).replaceFirstChar { it.uppercase() },
            )
        }
        CandidateProfiles.update({ CandidateProfiles.accountId eq id }) {
            it[fullName] = name
            it[CandidateProfiles.email] = email
            it[phone] = "+7 900 ${rnd.nextInt(100, 999)}-${rnd.nextInt(10, 99)}-${rnd.nextInt(10, 99)}"
            it[processingConsent] = true
            it[consentPublication] = true
            it[specialization] = spec
            it[CandidateProfiles.industry] = industry
            it[desiredGrade] = grade
            it[techStack] = stack
            it[CandidateProfiles.experience] = experience
            it[CandidateProfiles.softSkills] = softSkillPool.shuffled(rnd).take(3)
            it[targetRoles] = listOf(track.roles.first())
            it[cvHistory] = history
            it[updatedAt] = now.minusDays(rnd.nextLong(1, 200))
        }
        if (attempt) insertAttempt(id, spec, grade)
    }

    /** A completed attempt consistent with the grade (score = 100·Φ(θ), cut-points as in the assessment engine). */
    private fun Transaction.insertAttempt(id: UUID, spec: String, grade: String) {
        val score = when (grade) { "junior" -> rnd.nextInt(18, 34); "middle" -> rnd.nextInt(38, 78); else -> rnd.nextInt(80, 97) }
        val submitted = now.minusDays(rnd.nextLong(3, 120))
        AssessmentAttempts.insert {
            it[AssessmentAttempts.id] = UUID.randomUUID()
            it[candidateId] = id
            it[specialization] = spec
            it[questionIds] = emptyList()
            it[startedAt] = submitted.minusMinutes(rnd.nextLong(12, 28))
            it[submittedAt] = submitted
            it[AssessmentAttempts.score] = score
            it[AssessmentAttempts.grade] = grade
        }
    }

    /** Same import path as POST /api/candidates/me/fsp: the registry record becomes verified achievements. */
    private suspend fun linkFsp(links: List<Pair<UUID, String>>) {
        links.forEach { (id, fspId) ->
            val record = registry.lookup(fspId) ?: return@forEach
            db.tx { importFsp(id, record, registry) }
        }
    }

    // ---- Companies, vacancies, tasks ----

    private data class DemoCompany(val key: String, val name: String, val industry: String, val description: String, val telegram: String)

    private val companies = listOf(
        DemoCompany("north", "Северная Логистика (демо)", "логистика", "Сервис доставки и складской логистики: маршрутизация, трекинг, мобильные приложения курьеров.", "@north_logistics_hr"),
        DemoCompany("school", "Облачная Школа (демо)", "образование", "Онлайн-школа программирования: платформа курсов, проверка заданий, аналитика обучения.", "@cloud_school_jobs"),
        DemoCompany("pixel", "Пиксель Геймс (демо)", "игры", "Студия мобильных игр: игровые сервисы, аналитика игроков, UI игр.", "@pixel_games_team"),
    )

    private fun Transaction.seedCompanies() {
        val vacancies = json.decodeFromString<List<VacancyFixture>>(resource("vacancies.json"))
        val tasks = json.decodeFromString<List<TaskFixture>>(resource("short_tasks.json"))
        companies.forEachIndexed { i, company ->
            val subject = "demo-seed-employer:${company.key}"
            if (boundRole(subject) != null) return@forEachIndexed
            val id = createAccount(subject, "employer")
            fillCompany(id, company.name, company.industry, company.description, "jobs@${company.key}.demo.fsp.local", company.telegram)
            // The first two (backend) fixtures belong to the demo employer login; the rest are spread here.
            vacancies.drop(2).filterIndexed { v, _ -> v % companies.size == i }.forEach { insertVacancy(id, it) }
            tasks.filter { it.specialization != "backend" }.filterIndexed { t, _ -> t % companies.size == i }.forEach { insertTask(id, it) }
        }
    }

    private fun Transaction.fillCompany(id: UUID, name: String, industry: String, description: String, email: String, telegram: String) {
        EmployerCompanies.update({ EmployerCompanies.accountId eq id }) {
            it[EmployerCompanies.name] = name
            it[EmployerCompanies.description] = description
            it[website] = ""
            it[EmployerCompanies.industry] = industry
            it[contactEmail] = email
            it[contactTelegram] = telegram
        }
    }

    private fun Transaction.insertVacancy(employer: UUID, v: VacancyFixture): UUID {
        val id = UUID.randomUUID()
        val created = now.minusDays(rnd.nextLong(1, 20))
        Vacancies.insert {
            it[Vacancies.id] = id
            it[employerId] = employer
            it[title] = v.title
            it[description] = v.description
            it[specialization] = v.specialization
            it[grade] = v.grade
            it[techStack] = v.techStack
            it[salaryMinRub] = v.salaryMinRub
            it[salaryMaxRub] = v.salaryMaxRub
            it[workFormat] = v.workFormat
            it[city] = v.city
            it[status] = "open"
            it[createdAt] = created
            it[updatedAt] = created
        }
        return id
    }

    private fun Transaction.insertTask(employer: UUID, t: TaskFixture): UUID {
        val id = UUID.randomUUID()
        ShortTasks.insert {
            it[ShortTasks.id] = id
            it[employerId] = employer
            it[title] = t.title
            it[body] = t.body
            it[specialization] = t.specialization
            it[grade] = t.grade
            it[active] = true
            it[createdAt] = now.minusDays(rnd.nextLong(1, 14))
        }
        return id
    }

    // ---- Demo logins ----

    private fun Transaction.seedDemoEmployer(subject: String) {
        if (boundRole(subject) != null) return
        val id = createAccount(subject, "employer")
        fillCompany(id, "Демо Финтех", "финтех",
            "Демо-компания: платёжный шлюз для банков-партнёров, антифрод и мобильный банк. Команды по 5–7 человек, гибридный формат.",
            "hr@fintech.demo.fsp.local", "@demo_fintech_hr")
        EmployerNeeds.update({ EmployerNeeds.accountId eq id }) {
            it[specialization] = "backend"
            it[industry] = "fintech"
            it[minGrade] = "middle"
            it[techStack] = listOf("Kotlin", "PostgreSQL", "Kafka", "Docker")
            it[description] = "Ищем backend-разработчика в команду платёжного шлюза: микросервисы на Kotlin, транзакции в PostgreSQL, " +
                "события через Kafka, всё в Docker. Задачи — обработка платежей, идемпотентность, интеграции с банками, антифрод."
        }
        val vacancies = json.decodeFromString<List<VacancyFixture>>(resource("vacancies.json"))
        val tasks = json.decodeFromString<List<TaskFixture>>(resource("short_tasks.json"))
        val ownVacancies = vacancies.take(2).map { insertVacancy(id, it) }
        val ownTasks = tasks.filter { it.specialization == "backend" }.map { insertTask(id, it) }

        val backend = bankCandidates("backend")
        // Invitations in every status: the jury sees tracking and that contacts open only after acceptance.
        backend.take(3).forEachIndexed { i, candidate ->
            InvitationsTable.insert {
                it[InvitationsTable.id] = UUID.randomUUID()
                it[employerId] = id
                it[candidateId] = candidate
                it[description] = "Backend-разработчик в команду платёжного шлюза: Kotlin, PostgreSQL, Kafka. Гибрид, 2 дня в офисе."
                it[salaryMinRub] = 220_000 + i * 20_000
                it[salaryMaxRub] = 300_000 + i * 20_000
                it[status] = listOf("accepted", "viewed", "sent")[i]
                it[contactMethod] = "Telegram @demo_fintech_hr или hr@fintech.demo.fsp.local"
                it[createdAt] = now.minusDays(5L - i)
                it[updatedAt] = now.minusDays(4L - i)
            }
        }
        // Applications to own vacancies reveal the applicants' contacts to this employer (spec 2.2 rule 2).
        backend.drop(3).take(3).forEachIndexed { i, candidate ->
            VacancyApplications.insert {
                it[VacancyApplications.id] = UUID.randomUUID()
                it[vacancyId] = ownVacancies[i % ownVacancies.size]
                it[candidateId] = candidate
                it[coverNote] = "Здравствуйте! Интересна задача с платежами, есть опыт с Kotlin и PostgreSQL. Готов(а) обсудить."
                it[status] = listOf("sent", "viewed", "sent")[i]
                it[createdAt] = now.minusDays(3L - i)
                it[updatedAt] = now.minusDays(3L - i)
            }
        }
        ownTasks.firstOrNull()?.let { task ->
            backend.drop(6).take(3).forEachIndexed { i, candidate ->
                ShortTaskSubmissions.insert {
                    it[ShortTaskSubmissions.id] = UUID.randomUUID()
                    it[taskId] = task
                    it[candidateId] = candidate
                    it[kind] = if (i == 1) "approach" else "solution"
                    it[answer] = listOf(
                        "Клиент передаёт Idempotency-Key; сервер хранит ключ и результат в таблице с уникальным индексом, второй запрос с тем же ключом ждёт блокировку строки и возвращает сохранённый ответ.",
                        "Подход: уникальный ключ операции + статусная машина платежа; повтор после завершения возвращает 200 с тем же телом, повтор во время обработки — 409 с Retry-After.",
                        "INSERT … ON CONFLICT (idempotency_key) DO NOTHING, затем SELECT FOR UPDATE; результат кешируется в Redis на сутки.",
                    )[i]
                    it[submittedAt] = now.minusDays(2L - i.coerceAtMost(1))
                    it[ratingComment] = ""
                }
            }
        }
    }

    private fun Transaction.seedAssessedCandidate(subject: String) {
        if (boundRole(subject) != null) return
        val id = createAccount(subject, "candidate")
        fillCandidate(id, "Мария Волкова", "maria.volkova@demo.fsp.local", "backend", "middle", fspCandidate = true, attempt = false)
        CandidateProfiles.update({ CandidateProfiles.accountId eq id }) {
            it[techStack] = listOf("Kotlin", "PostgreSQL", "Kafka", "Docker", "Redis")
            it[industry] = "fintech"
            it[experience] = "3 года опыта: разработка платёжных микросервисов на Kotlin и Ktor в финтех-компании. Транзакции и индексы в PostgreSQL, " +
                "события через Kafka, деплой в Docker и Kubernetes. Задачи: идемпотентная обработка платежей, интеграции с банками, антифрод-правила. " +
                "Участвую в соревнованиях по спортивному программированию."
        }
        val north = boundRole("demo-seed-employer:north")?.first ?: return
        InvitationsTable.insert {
            it[InvitationsTable.id] = UUID.randomUUID()
            it[employerId] = north
            it[candidateId] = id
            it[description] = "Backend-разработчик сервиса маршрутизации курьеров: Kotlin, PostgreSQL, Kafka. Удалённо, команда из 6 человек."
            it[salaryMinRub] = 230_000
            it[salaryMaxRub] = 290_000
            it[status] = "sent"
            it[contactMethod] = "Telegram @north_logistics_hr"
            it[createdAt] = now.minusDays(1)
            it[updatedAt] = now.minusDays(1)
        }
    }

    /**
     * The demo login's history should look like a real one (survey, served items, θ), so its attempt goes through the
     * production engine — startAttempt/answerItem — answered by a simulated middle-level candidate. Replaces an
     * earlier synthetic row from older seeds; does nothing once a real attempt exists.
     */
    private fun Transaction.realAssessment(id: UUID) {
        val attempts = AssessmentAttempts.selectAll().where { AssessmentAttempts.candidateId eq id }.toList()
        if (attempts.any { it[AssessmentAttempts.engineVersion] == ENGINE_VERSION }) return
        AssessmentAttempts.deleteWhere { AssessmentAttempts.candidateId eq id }
        val sim = Random(7)
        val plan = planStart(StartAssessmentRequest(
            AssessmentSurvey(industry = "fintech", specialization = "backend", experienceYears = 3,
                teamRoles = listOf("individual", "mentor"), workFormat = "hybrid"),
            targetGrade = "middle", vacancyId = "fintech-backend",
        ))
        var clock = now.minusDays(12)
        val started = startAttempt(id, plan, clock) as? StartOutcome.Started ?: return
        val attemptId = UUID.fromString(started.attempt.attemptId)
        repeat(Cat.TEST_LENGTH) {
            val item = AssessmentItems.selectAll()
                .where { (AssessmentItems.attemptId eq attemptId) and AssessmentItems.answeredAt.isNull() }
                .singleOrNull() ?: return
            val view = item[AssessmentItems.view]
            val key = item[AssessmentItems.answerKey]
            val right = sim.nextDouble() < 0.8
            val answer = when {
                view.format == "choice" && right -> AnswerKey.optionIds[key.toInt()]
                view.format == "choice" -> view.options.first { it.id != AnswerKey.optionIds[key.toInt()] }.id
                right -> key
                else -> (key.toDouble() + item[AssessmentItems.tolerance] * 10 + 13).toString()
            }
            clock = clock.plusSeconds(70L + sim.nextLong(0, 80))
            if (answerItem(id, attemptId, AssessmentAnswerRequest(item[AssessmentItems.position], answer), clock).done) return
        }
    }

    private fun Transaction.bankCandidates(spec: String): List<UUID> =
        Accounts.selectAll().where { Accounts.subject like "demo-seed:$spec-%" }
            .map { it[Accounts.id] to it[Accounts.subject] }
            .sortedBy { it.second.substringAfterLast('-').toInt() }
            .map { it.first }

    private fun resource(name: String): String =
        DemoSeed::class.java.classLoader.getResource("demo/$name")!!.readText()

    private fun yearsWord(n: Int) = when { n % 10 == 1 && n % 100 != 11 -> "год"; n % 10 in 2..4 && n % 100 !in 12..14 -> "года"; else -> "лет" }
}
