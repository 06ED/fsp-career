package ru.fsp.career.matching.validation

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.fsp.career.matching.domain.MatchingEngine
import ru.fsp.career.matching.domain.MatchingWeights
import java.io.File
import java.util.Locale

/*
 * `gradle validateMatching` — offline validation of the matching engine on synthetic «вакансия – кандидат» pairs.
 *
 * Protocol (fixed before the reported run):
 *  - DEVELOPMENT seed 20261006 was used while building the engine (diagnostics, the keyword-stuffing fix).
 *  - REPORTED numbers come from five independent held-out markets (seeds 101…505), never used for tuning.
 *    Each market: 360 candidates (72 per specialization) × the same 24 employer needs.
 *  - Unit of analysis = one need in one market (5 × 24 = 120); 95 % CIs by percentile bootstrap over them.
 *  - Engine weights are the product defaults (a priori), not fitted to the synthetic labels.
 * Outputs: docs/validation/matching-results.md, matching-results.json, matching-pairs.csv, matching-candidates.csv.
 */

private const val DEV_SEED = 20261006L
private val TEST_SEEDS = listOf(101L, 202L, 303L, 404L, 505L)
private const val EXPORT_SEED = 101L

private data class Metric(val key: String, val label: String, val get: (NeedMetrics) -> Double)

private val METRICS = listOf(
    Metric("p5", "P@5", NeedMetrics::p5),
    Metric("p10", "P@10", NeedMetrics::p10),
    Metric("ndcg10", "nDCG@10", NeedMetrics::ndcg10),
    Metric("mrr", "MRR", NeedMetrics::mrr),
)

private data class SystemDef(val key: String, val name: String, val description: String, val random: Boolean = false, val build: (Evaluation) -> (SynthNeed) -> List<String>)

private val SYSTEMS = listOf(
    SystemDef("engine", "Подбор по потребности (движок платформы)",
        "Категорийный фильтр (специализация + подтверждённый грейд) и релевантность: тест 30, текст 25, стек 20, ФСП 15, отрасль 5, актуальность 5.") { e -> e.engine() },
    SystemDef("bank", "Категория + сила профиля (режим банка)",
        "Точная категория, порядок по силе подтверждённого профиля: 65·тест + 25·ФСП + 10·актуальность. Описание потребности не используется.") { e -> e::bankStrength },
    SystemDef("test", "Только балл теста",
        "Тот же категорийный фильтр, что у движка, сортировка только по баллу теста.") { e -> e::testOnly },
    SystemDef("keyword", "Ключевые слова + заявленный стек («как на обычных площадках»)",
        "Фильтр по заявленному (самооценённому) грейду; ранг = доля стека вакансии в заявленном стеке + 0,5·доля ключевых слов вакансии в резюме. Тест и ФСП не используются.") { e -> e::keyword },
    SystemDef("randomCategory", "Случайный порядок внутри категории",
        "Только категорийный фильтр (специализация + подтверждённый грейд), порядок случайный (среднее по 50 перестановкам).", random = true) { e -> e::exactCategory },
    SystemDef("random", "Случайный порядок по специализации",
        "Только специализация, порядок случайный (среднее по 50 перестановкам).", random = true) { e -> e::sameSpecialization },
)

private val ABLATIONS = listOf(
    "Полный движок" to MatchingWeights(),
    "− текст (TF-IDF)" to MatchingWeights(text = 0.0),
    "− стек" to MatchingWeights(stack = 0.0),
    "− ФСП" to MatchingWeights(fsp = 0.0),
    "− тест" to MatchingWeights(test = 0.0),
    "− отрасль и актуальность" to MatchingWeights(industry = 0.0, freshness = 0.0),
    "− текст, стек и отрасль (без описания потребности)" to MatchingWeights(text = 0.0, stack = 0.0, industry = 0.0),
    "Только самоописание (текст + стек + отрасль)" to MatchingWeights(test = 0.0, fsp = 0.0, freshness = 0.0),
    "Без штрафа за грейд выше требуемого" to MatchingWeights(gradeOneUp = 1.0, gradeTwoUp = 1.0),
)

private fun sensitivityVariants(): List<Pair<String, MatchingWeights>> {
    val d = MatchingWeights()
    val out = mutableListOf<Pair<String, MatchingWeights>>()
    for (factor in listOf(0.5, 1.5)) {
        val tag = if (factor < 1) "×0,5" else "×1,5"
        out += "тест $tag" to d.copy(test = d.test * factor)
        out += "текст $tag" to d.copy(text = d.text * factor)
        out += "стек $tag" to d.copy(stack = d.stack * factor)
        out += "ФСП $tag" to d.copy(fsp = d.fsp * factor)
        out += "отрасль $tag" to d.copy(industry = d.industry * factor)
        out += "актуальность $tag" to d.copy(freshness = d.freshness * factor)
    }
    out += "насыщение текста при косинусе 0,25" to d.copy(textFullScoreCosine = 0.25)
    out += "насыщение текста при косинусе 0,50" to d.copy(textFullScoreCosine = 0.50)
    out += "грейд выше: ×0,70" to d.copy(gradeOneUp = 0.70, gradeTwoUp = 0.55)
    out += "грейд выше: ×1,00" to d.copy(gradeOneUp = 1.0, gradeTwoUp = 1.0)
    return out
}

private fun f(value: Double, digits: Int = 3) = String.format(Locale.ROOT, "%.${digits}f", value)
private fun fc(value: Double, digits: Int = 3) = f(value, digits).replace('.', ',')
private fun ci(i: RankingMetrics.Interval, digits: Int = 3) = "${fc(i.mean, digits)} [${fc(i.low, digits)}; ${fc(i.high, digits)}]"
private fun signed(value: Double) = (if (value >= 0) "+" else "") + fc(value)

fun main(args: Array<String>) {
    val outDir = File(args.getOrElse(0) { "docs/validation" }).also { it.mkdirs() }

    // ---- held-out markets
    val evaluations = TEST_SEEDS.associateWith { Evaluation(SyntheticMarket(seed = it)) }
    val runs: Map<String, List<SystemRun>> = SYSTEMS.associate { def ->
        def.key to evaluations.values.map { e ->
            if (def.random) e.runRandom(def.key, def.name, def.description) { def.build(e)(it) }
            else e.run(def.key, def.name, def.description, def.build(e))
        }
    }
    fun pooled(key: String, metric: (NeedMetrics) -> Double) = runs.getValue(key).flatMap { it.values(metric) }
    fun pooledEngine(weights: MatchingWeights, metric: (NeedMetrics) -> Double) =
        evaluations.values.flatMap { e -> e.run("v", "v", "", e.engine(weights)).values(metric) }

    val main = SYSTEMS.associate { def -> def.key to METRICS.associate { m -> m.key to RankingMetrics.bootstrap(pooled(def.key, m.get)) } }
    val perSeed = SYSTEMS.associate { def -> def.key to runs.getValue(def.key).map { run -> run.values(NeedMetrics::ndcg10).average() } }
    val stuffing = SYSTEMS.associate { def -> def.key to pooled(def.key, NeedMetrics::stufferShare10).average() }
    val stufferBase = evaluations.values.flatMap { it.market.candidates }.let { all -> all.count { it.stuffer }.toDouble() / all.size }
    val diffs = SYSTEMS.filter { it.key != "engine" }.associate { def ->
        def.key to METRICS.associate { m -> m.key to RankingMetrics.pairedDifference(pooled("engine", m.get), pooled(def.key, m.get)) }
    }
    val ablation = ABLATIONS.map { (name, w) ->
        val ndcg = pooledEngine(w, NeedMetrics::ndcg10)
        val p10 = pooledEngine(w, NeedMetrics::p10)
        Triple(name, RankingMetrics.bootstrap(ndcg) to RankingMetrics.bootstrap(p10),
            RankingMetrics.pairedDifference(ndcg, pooled("engine", NeedMetrics::ndcg10)))
    }
    val sensitivity = sensitivityVariants().map { (name, w) -> name to pooledEngine(w, NeedMetrics::ndcg10).average() }
    val baseNdcg = main.getValue("engine").getValue("ndcg10").mean

    // Label statistics and gate recall (share of relevant candidates the engine returns at all).
    val labelRows = evaluations.values.flatMap { e -> SyntheticMarket.needs.map { n -> e.labels.getValue(n.id).values } }
    val relevantPerNeed = labelRows.map { l -> l.count { it >= 2 } }.average()
    val idealPerNeed = labelRows.map { l -> l.count { it == 3 } }.average()
    val gateRecall = runs.getValue("engine").sumOf { r -> r.perNeed.values.sumOf { it.relevantReturned } }.toDouble() /
        labelRows.sumOf { l -> l.count { it >= 2 } }

    // ---- development seed (transparency only)
    val dev = Evaluation(SyntheticMarket(seed = DEV_SEED))
    val devMain = SYSTEMS.associate { def ->
        val run = if (def.random) dev.runRandom(def.key, def.name, def.description) { def.build(dev)(it) } else dev.run(def.key, def.name, def.description, def.build(dev))
        def.key to METRICS.associate { m -> m.key to run.values(m.get).average() }
    }

    // ---- exports from one held-out market
    val export = evaluations.getValue(EXPORT_SEED)
    writePairs(File(outDir, "matching-pairs.csv"), export, runs.mapValues { (_, list) -> list[TEST_SEEDS.indexOf(EXPORT_SEED)] })
    writeCandidates(File(outDir, "matching-candidates.csv"), export)
    val exampleNeed = SyntheticMarket.needs.first()
    val example = MatchingEngine(MatchingWeights(), export.market.now).rankForNeed(exampleNeed.query, export.pool)
    val perNeedEngine = runs.getValue("engine")[TEST_SEEDS.indexOf(EXPORT_SEED)].perNeed
    val perNeedKeyword = runs.getValue("keyword")[TEST_SEEDS.indexOf(EXPORT_SEED)].perNeed
    val perNeedBank = runs.getValue("bank")[TEST_SEEDS.indexOf(EXPORT_SEED)].perNeed

    // ---- JSON
    val json = buildJsonObject {
        put("generatedBy", "gradle validateMatching (apps/api/src/test/kotlin/ru/fsp/career/matching/validation)")
        put("syntheticLabels", true)
        put("evaluationDate", SyntheticMarket.NOW.toString())
        put("developmentSeed", DEV_SEED)
        put("heldOutSeeds", JsonArray(TEST_SEEDS.map { JsonPrimitive(it) }))
        put("candidatesPerMarket", export.market.candidates.size)
        put("needs", SyntheticMarket.needs.size)
        put("needInstances", labelRows.size)
        put("relevantPerNeed", relevantPerNeed)
        put("idealPerNeed", idealPerNeed)
        put("engineGateRecall", gateRecall)
        put("weights", Json.parseToJsonElement(Json.encodeToString(WeightsDto.serializer(), WeightsDto.from(MatchingWeights()))))
        put("systems", buildJsonArray {
            SYSTEMS.forEach { def ->
                add(buildJsonObject {
                    put("key", def.key); put("name", def.name); put("description", def.description)
                    METRICS.forEach { m -> put(m.key, interval(main.getValue(def.key).getValue(m.key))) }
                    put("ndcg10PerSeed", JsonArray(perSeed.getValue(def.key).map { JsonPrimitive(round(it)) }))
                    put("stufferShareTop10", round(stuffing.getValue(def.key)))
                    put("developmentSeed", buildJsonObject { METRICS.forEach { m -> put(m.key, round(devMain.getValue(def.key).getValue(m.key))) } })
                })
            }
        })
        put("stufferBaseRate", round(stufferBase))
        put("engineMinusBaseline", buildJsonObject {
            diffs.forEach { (key, byMetric) -> put(key, buildJsonObject { byMetric.forEach { (m, i) -> put(m, interval(i)) } }) }
        })
        put("ablations", buildJsonArray {
            ablation.forEach { (name, intervals, delta) ->
                add(buildJsonObject { put("variant", name); put("ndcg10", interval(intervals.first)); put("p10", interval(intervals.second)); put("deltaNdcg10VsFull", interval(delta)) })
            }
        })
        put("sensitivity", buildJsonArray {
            sensitivity.forEach { (name, value) -> add(buildJsonObject { put("variant", name); put("ndcg10", round(value)); put("delta", round(value - baseNdcg)) }) }
        })
    }
    File(outDir, "matching-results.json").writeText(Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), json) + "\n")

    // ---- Markdown report (Russian)
    val md = buildString {
        appendLine("# Валидация механики подбора: синтетический набор пар «вакансия – кандидат»")
        appendLine()
        appendLine("> Файл сгенерирован командой `cd apps/api && gradle validateMatching` (детерминированно, без сети и БД). ")
        appendLine("> **Метки эталона синтетические**: они вычислены из скрытых атрибутов сгенерированных кандидатов, а не поставлены экспертами. ")
        appendLine("> Это проверка того, что ранжирование восстанавливает заранее заданную «истину» по зашумлённым наблюдаемым сигналам, а не доказательство качества найма на реальном рынке.")
        appendLine()
        appendLine("## Ключевые результаты")
        appendLine()
        val e = main.getValue("engine"); val k = main.getValue("keyword"); val r = main.getValue("random"); val b = main.getValue("bank")
        appendLine("- Доля релевантных кандидатов в топ-10 (P@10): **${fc(e.getValue("p10").mean, 2)}** у движка против ${fc(k.getValue("p10").mean, 2)} у поиска по ключевым словам и заявленному стеку и ${fc(r.getValue("p10").mean, 2)} у случайного порядка.")
        appendLine("- nDCG@10: **${fc(e.getValue("ndcg10").mean)}** (95 % ДИ ${fc(e.getValue("ndcg10").low)}–${fc(e.getValue("ndcg10").high)}); разница с ключевыми словами ${signed(diffs.getValue("keyword").getValue("ndcg10").mean)} [${fc(diffs.getValue("keyword").getValue("ndcg10").low)}; ${fc(diffs.getValue("keyword").getValue("ndcg10").high)}].")
        appendLine("- Режим банка (только подтверждённый профиль в точной категории) уже даёт nDCG@10 ${fc(b.getValue("ndcg10").mean)}; учёт описания потребности добавляет ${signed(diffs.getValue("bank").getValue("ndcg10").mean)} [${fc(diffs.getValue("bank").getValue("ndcg10").low)}; ${fc(diffs.getValue("bank").getValue("ndcg10").high)}].")
        val needAblation = ablation.first { it.first.startsWith("− текст, стек") }.third
        appendLine("- Без описания потребности (TF-IDF-текст + стек + отрасль) nDCG@10 падает на ${fc(-needAblation.mean)} [${fc(-needAblation.high)}; ${fc(-needAblation.low)}]; по отдельности текст и стек частично взаимозаменяемы (см. абляции).")
        appendLine("- «Накрутчики» стека (20 % пула, 4–6 выдуманных навыков) занимают ${fc(stuffing.getValue("keyword") * 100, 0)} % топ-10 у поиска по ключевым словам и ${fc(stuffing.getValue("engine") * 100, 0)} % у движка (базовая доля в пуле ${fc(stufferBase * 100, 0)} %).")
        appendLine()
        appendLine("## Что именно проверяется")
        appendLine()
        appendLine("Критерий 1 ТЗ: «доля релевантных кандидатов в топе выдачи на тестовом наборе пар «вакансия – кандидат»». Мы генерируем рынок, где у каждого кандидата есть **скрытая истина** (реальная специализация, грейд, компетентность, навыки, отраслевой опыт), а платформа видит только **зашумлённые наблюдения** — те же, что и в продукте: подтверждённый тестом грейд и балл, заявленный стек, текст опыта, результаты ФСП. Эталонная релевантность пары считается только по скрытым атрибутам, поэтому система не может «подсмотреть» ответ.")
        appendLine()
        appendLine("## Данные")
        appendLine()
        appendLine("| Параметр | Значение |")
        appendLine("|---|---|")
        appendLine("| Рынков (независимых seed) | ${TEST_SEEDS.size} отчётных (${TEST_SEEDS.joinToString(", ")}) + 1 для разработки ($DEV_SEED) |")
        appendLine("| Кандидатов на рынок | ${export.market.candidates.size} (по 72 на специализацию: backend, frontend, data, product, design) |")
        appendLine("| Потребностей работодателей | ${SyntheticMarket.needs.size} описаний в стиле вакансий (5 специализаций × junior/middle/senior × 9 отраслей) |")
        appendLine("| Единиц анализа | ${labelRows.size} пар «потребность × рынок» |")
        appendLine("| Релевантных (метка ≥ 2) на потребность | в среднем ${fc(relevantPerNeed, 1)}, из них идеальных (3) — ${fc(idealPerNeed, 1)} |")
        appendLine()
        appendLine("Модель шума наблюдений (параметры зафиксированы в `SyntheticMarket.kt`):")
        appendLine()
        appendLine("- грейд по тесту совпадает с истинным с вероятностью 0,85, иначе ошибается на одну ступень;")
        appendLine("- балл теста = 35 + 55·q + N(0; 9²), где q — скрытая компетентность;")
        appendLine("- заявленный стек: истинный навык указан с вероятностью 0,9; 50 % кандидатов честные, 30 % добавляют 1–2 выдуманных навыка, 20 % — «накрутчики» с 4–6 выдуманными популярными навыками;")
        appendLine("- самооценка грейда завышена на ступень у 25 % кандидатов;")
        appendLine("- текст опыта на русском: истинные навыки упомянуты с вероятностью 0,75, выдуманные — 0,2, отрасли — 0,85; 8 % оставляют поле пустым;")
        appendLine("- история ФСП есть с вероятностью 0,10 + 0,45·q (у большинства её нет), места лучше при большей q;")
        appendLine("- последняя активность — равномерно 0–400 дней назад и **не связана** с релевантностью (фактор актуальности здесь — чистый шум).")
        appendLine()
        appendLine("## Эталонная разметка")
        appendLine()
        appendLine("`u = соответствиеГрейда × (0,45·q + 0,40·покрытиеСтека + 0,15·отрасль)`, 0 при другой специализации; соответствие грейда: тот же 1,0, на ступень выше 0,7, ниже 0,3, на две ступени 0.")
        appendLine("Метка: 3 при u ≥ 0,62; 2 при u ≥ 0,45; 1 при u ≥ 0,28; иначе 0. **Релевантный = метка ≥ 2.** Формула эталона намеренно отличается от формулы движка: в ней нет балла теста, ФСП, текста и заявленного стека — только скрытая истина.")
        appendLine()
        appendLine("## Сравниваемые системы")
        appendLine()
        SYSTEMS.forEach { appendLine("- **${it.name}** — ${it.description}") }
        appendLine()
        appendLine("## Результаты (5 отчётных рынков, 95 % бутстрэп-ДИ по ${labelRows.size} потребностям)")
        appendLine()
        appendLine("| Система | P@5 | P@10 | nDCG@10 | MRR | nDCG@10 по рынкам (мин–макс) |")
        appendLine("|---|---|---|---|---|---|")
        SYSTEMS.forEach { def ->
            val m = main.getValue(def.key)
            val seeds = perSeed.getValue(def.key)
            appendLine("| ${def.name} | ${ci(m.getValue("p5"), 2)} | ${ci(m.getValue("p10"), 2)} | ${ci(m.getValue("ndcg10"))} | ${ci(m.getValue("mrr"))} | ${fc(seeds.min())}–${fc(seeds.max())} |")
        }
        appendLine()
        appendLine("Парная разница «движок − базовая линия» (бутстрэп по тем же потребностям):")
        appendLine()
        appendLine("| Базовая линия | Δ P@5 | Δ P@10 | Δ nDCG@10 | Δ MRR |")
        appendLine("|---|---|---|---|---|")
        diffs.forEach { (key, byMetric) ->
            val name = SYSTEMS.first { it.key == key }.name
            appendLine("| $name | " + METRICS.joinToString(" | ") { m -> byMetric.getValue(m.key).let { "${signed(it.mean)} [${fc(it.low)}; ${fc(it.high)}]" } } + " |")
        }
        appendLine()
        appendLine("Покрытие категорийного фильтра: движок возвращает ${fc(gateRecall * 100, 1)} % всех релевантных кандидатов (остальные отсечены из-за ошибки теста на ступень вниз — плата за принцип «выдача по подтверждённой категории»; работодатель может включить «показывать грейд ниже»).")
        appendLine()
        appendLine("## Устойчивость к накрутке заявленного стека")
        appendLine()
        appendLine("| Система | Доля «накрутчиков» в топ-10 |")
        appendLine("|---|---|")
        SYSTEMS.forEach { appendLine("| ${it.name} | ${fc(stuffing.getValue(it.key) * 100, 1)} % |") }
        appendLine("| *Доля в пуле* | *${fc(stufferBase * 100, 1)} %* |")
        appendLine()
        appendLine("Заявленный, но не описанный в опыте навык получает не более 0,5 балла покрытия, умноженного на согласованность всего заявленного стека с текстом опыта; теги стека не входят в TF-IDF-документ, чтобы не учитываться дважды. Это изменение сделано на рынке разработки ($DEV_SEED) после диагностики: там доля накрутчиков в топ-10 снизилась с 29 % до 25 %.")
        appendLine()
        appendLine("## Абляции (вклад факторов)")
        appendLine()
        appendLine("| Вариант | nDCG@10 | P@10 | Δ nDCG@10 к полному |")
        appendLine("|---|---|---|---|")
        ablation.forEach { (name, intervals, delta) ->
            appendLine("| $name | ${ci(intervals.first)} | ${ci(intervals.second, 2)} | ${signed(delta.mean)} [${fc(delta.low)}; ${fc(delta.high)}] |")
        }
        appendLine()
        appendLine("Как читать: отрицательная Δ — фактор полезен на этих данных. Фактор актуальности на синтетике по построению не связан с эталоном, поэтому его исключение не должно ухудшать метрики; в продукте он отражает доступность кандидата.")
        appendLine()
        appendLine("## Чувствительность к весам")
        appendLine()
        appendLine("Каждый вес по очереди умножен на 0,5 или 1,5 (остальные масштабируются до 100). Базовое nDCG@10 = ${fc(baseNdcg)}.")
        appendLine()
        appendLine("| Вариант | nDCG@10 | Δ |")
        appendLine("|---|---|---|")
        sensitivity.forEach { (name, value) -> appendLine("| $name | ${fc(value)} | ${signed(value - baseNdcg)} |") }
        appendLine()
        val spread = sensitivity.map { it.second }
        appendLine("Разброс nDCG@10 при всех вариациях: ${fc(spread.min())}–${fc(spread.max())} — ранжирование не держится на точной настройке одного веса.")
        appendLine()
        appendLine("## Пример объяснимой выдачи (рынок $EXPORT_SEED, потребность ${exampleNeed.id})")
        appendLine()
        appendLine("**${exampleNeed.title}.** ${exampleNeed.query.description} Стек: ${exampleNeed.query.stack.joinToString(", ")}; грейд ${exampleNeed.query.grade}; отрасль ${exampleNeed.query.industry}.")
        appendLine()
        appendLine("Пример не отбирался вручную: первая потребность первого отчётного рынка, первые три позиции. В скобках — скрытая истина, которую платформа не видит.")
        appendLine()
        example.results.take(3).forEachIndexed { index, ranked ->
            val label = export.labels.getValue(exampleNeed.id).getValue(ranked.candidate.id)
            val hidden = export.byId.getValue(ranked.candidate.id)
            val gradeNote = if (hidden.trueGrade != hidden.assessedGrade) "; **тест ошибся с грейдом**" else ""
            appendLine("${index + 1}. **${ranked.candidate.id}** — релевантность ${fc(ranked.relevance, 1)} / 100, эталонная метка $label " +
                "(скрыто: истинный грейд ${hidden.trueGrade}, q = ${fc(hidden.competence, 2)}, истинные навыки ${hidden.trueSkills.joinToString(", ")}$gradeNote). ${ranked.summary}")
            ranked.factors.forEach { factor ->
                val points = if (factor.code == "grade") "×${fc(factor.value, 2)}" else "${fc(factor.points, 1)} из ${fc(factor.weight, 1)}"
                appendLine("   - ${factor.label} ($points): ${factor.text}")
            }
            if (ranked.flags.isNotEmpty()) appendLine("   - Отметки: ${ranked.flags.joinToString("; ")}")
        }
        appendLine()
        appendLine("## По потребностям (рынок $EXPORT_SEED)")
        appendLine()
        appendLine("| ID | Потребность | Релевантных | P@10 движок | P@10 банк | P@10 ключевые слова | nDCG@10 движок |")
        appendLine("|---|---|---|---|---|---|---|")
        SyntheticMarket.needs.forEach { n ->
            val relevant = export.labels.getValue(n.id).values.count { it >= 2 }
            appendLine("| ${n.id} | ${n.title} | $relevant | ${fc(perNeedEngine.getValue(n.id).p10, 1)} | ${fc(perNeedBank.getValue(n.id).p10, 1)} | ${fc(perNeedKeyword.getValue(n.id).p10, 1)} | ${fc(perNeedEngine.getValue(n.id).ndcg10)} |")
        }
        appendLine()
        appendLine("## Рынок разработки (для прозрачности)")
        appendLine()
        appendLine("На seed $DEV_SEED велась отладка, поэтому эти числа не используются как оценка качества.")
        appendLine()
        appendLine("| Система | P@5 | P@10 | nDCG@10 | MRR |")
        appendLine("|---|---|---|---|---|")
        SYSTEMS.forEach { def -> appendLine("| ${def.name} | " + METRICS.joinToString(" | ") { fc(devMain.getValue(def.key).getValue(it.key)) } + " |") }
        appendLine()
        appendLine("## Файлы для перепроверки жюри")
        appendLine()
        appendLine("- `matching-pairs.csv` — все пары «потребность – кандидат» одной специализации на рынке $EXPORT_SEED: эталонная метка и u, ранги движка и базовых линий, наблюдаемые сигналы и скрытые атрибуты.")
        appendLine("- `matching-candidates.csv` — кандидаты рынка $EXPORT_SEED с текстом опыта, заявленным и истинным стеком, результатами ФСП.")
        appendLine("- `matching-results.json` — все числа этого отчёта (включая ДИ и рынок разработки).")
        appendLine()
        appendLine("## Ограничения")
        appendLine()
        appendLine("- Эталон синтетический: результат показывает, что движок правильно использует сигналы **при наших допущениях** о шуме (тест информативнее самоописания, ФСП коррелирует с компетентностью). Если на реальном рынке допущения неверны, числа будут другими.")
        appendLine("- Генератор и движок написаны одной командой; чтобы снизить подгонку, веса заданы заранее, а отчёт построен на рынках, не использованных при разработке. Анализ чувствительности показывает, насколько вывод зависит от весов.")
        appendLine("- Тексты опыта собраны из шаблонов, поэтому TF-IDF здесь проще, чем на реальных резюме; вклад текста на реальных данных может отличаться в обе стороны.")
        appendLine("- Метрики считаются по всему пулу, а не по выборке; опция «показывать грейд ниже» в отчёте выключена.")
        val fspDelta = ablation.first { it.first == "− ФСП" }.third
        val textHalf = sensitivity.first { it.first == "текст ×0,5" }.second - baseNdcg
        appendLine("- Вес ФСП (15) на синтетике не помогает: без ФСП nDCG@10 меняется на ${signed(fspDelta.mean)} [${fc(fspDelta.low)}; ${fc(fspDelta.high)}]. В генераторе история ФСП — редкий и шумный признак компетентности. Вес сохранён осознанно: ТЗ требует поднимать внутри категории кандидатов с подтверждёнными достижениями ФСП, и это единственный сигнал, независимый от нашего теста. Калибровать его нужно на реальной обратной связи работодателей.")
        appendLine("- Уменьшение веса текста вдвое меняет nDCG@10 на ${signed(textHalf)}: на шаблонных синтетических текстах информативность TF-IDF ограничена. Мы не меняли веса по отчётным рынкам — это была бы подгонка под тест.")
        appendLine()
        appendLine("## Как провести такую же оценку на реальных данных")
        appendLine()
        appendLine("1. Зафиксировать версию движка, пул согласившихся кандидатов (обезличенный), 20–30 реальных потребностей работодателей и `k` (5 и 10) **до** разметки.")
        appendLine("2. Для каждой потребности отдать на разметку не только топ движка, но и случайную выборку остальных кандидатов категории (иначе нерелевантность неразмеченных неизвестна); перемешать порядок и скрыть источник.")
        appendLine("3. Не менее двух независимых экспертов-нанимающих (тимлиды/HR из отрасли) ставят метку 0–3 по той же шкале; согласованность — взвешенная каппа Коэна, расхождения разбирает третий эксперт.")
        appendLine("4. Посчитать P@5/P@10, nDCG@10, MRR с бутстрэп-ДИ по потребностям, сравнить с теми же базовыми линиями (ключевые слова, только тест, случайный порядок) и абляциями — этот код (`RankingMetrics.kt`) переиспользуется без изменений: достаточно заменить синтетические метки на экспертные.")
        appendLine("5. Онлайн-метрики после запуска: доля принятых приглашений по позиции в выдаче, время до первого приглашения, доля откликов «не подходит» — и периодическое переобучение весов (learning-to-rank) на этой обратной связи с контролем смещения позиции.")
        appendLine()
        appendLine("## Как воспроизвести")
        appendLine()
        appendLine("```")
        appendLine("cd apps/api")
        appendLine("gradle validateMatching   # ≈ 10–20 с, пишет docs/validation/matching-*")
        appendLine("```")
        appendLine()
        appendLine("Код: `apps/api/src/test/kotlin/ru/fsp/career/matching/validation/` (генератор `SyntheticMarket.kt`, метрики `RankingMetrics.kt`, базовые линии `MatchingValidation.kt`, отчёт `ValidateMatching.kt`). Движок: `apps/api/src/main/kotlin/ru/fsp/career/matching/`.")
    }
    File(outDir, "matching-results.md").writeText(md)
    println("Wrote ${outDir.absolutePath}/matching-results.{md,json}, matching-pairs.csv, matching-candidates.csv")
    SYSTEMS.forEach { def ->
        val m = main.getValue(def.key)
        println(String.format(Locale.ROOT, "%-15s P@5=%.3f P@10=%.3f nDCG@10=%.3f MRR=%.3f stuffers@10=%.3f", def.key,
            m.getValue("p5").mean, m.getValue("p10").mean, m.getValue("ndcg10").mean, m.getValue("mrr").mean, stuffing.getValue(def.key)))
    }
}

@kotlinx.serialization.Serializable
private data class WeightsDto(
    val test: Double, val text: Double, val stack: Double, val fsp: Double, val industry: Double, val freshness: Double,
    val bankTest: Double, val bankFsp: Double, val bankFreshness: Double,
    val gradeOneUp: Double, val gradeTwoUp: Double, val gradeOneDown: Double, val textFullScoreCosine: Double,
) {
    companion object {
        fun from(w: MatchingWeights) = WeightsDto(w.test, w.text, w.stack, w.fsp, w.industry, w.freshness, w.bankTest, w.bankFsp,
            w.bankFreshness, w.gradeOneUp, w.gradeTwoUp, w.gradeOneDown, w.textFullScoreCosine)
    }
}

private fun round(value: Double) = Math.round(value * 10000) / 10000.0

private fun interval(i: RankingMetrics.Interval): JsonObject = buildJsonObject {
    put("mean", round(i.mean)); put("ciLow", round(i.low)); put("ciHigh", round(i.high))
}

private fun csv(value: Any?): String {
    val text = value?.toString().orEmpty()
    return if (text.any { it == ',' || it == '"' || it == '\n' || it == ';' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
}

private fun writePairs(file: File, e: Evaluation, runs: Map<String, SystemRun>) {
    val ranks = runs.filterValues { it.rankings.isNotEmpty() }.mapValues { (_, run) ->
        run.rankings.mapValues { (_, ids) -> ids.withIndex().associate { it.value to it.index + 1 } }
    }
    val engine = MatchingEngine(MatchingWeights(), e.market.now)
    file.bufferedWriter().use { out ->
        out.write("need_id,candidate_id,label,utility,engine_rank,engine_relevance,bank_rank,test_only_rank,keyword_rank," +
            "specialization,true_grade,assessed_grade,declared_grade,competence_q,test_score,true_skills,self_stack,fake_skills," +
            "stuffer,true_industries,industry_field,fsp_points,last_active_days\n")
        for (need in SyntheticMarket.needs) {
            val relevance = engine.rankForNeed(need.query, e.pool).results.associate { it.candidate.id to it.relevance }
            for (c in e.market.candidates.filter { it.specialization == need.query.specialization }) {
                fun rank(key: String) = ranks[key]?.get(need.id)?.get(c.id)?.toString().orEmpty()
                val row = listOf(
                    need.id, c.id, e.labels.getValue(need.id).getValue(c.id), f(e.market.utility(need.query, c), 4),
                    rank("engine"), relevance[c.id]?.let { f(it, 2) }.orEmpty(), rank("bank"), rank("test"), rank("keyword"),
                    c.specialization, c.trueGrade, c.assessedGrade, c.declaredGrade, f(c.competence, 3), c.testScore,
                    c.trueSkills.joinToString("; "), c.selfStack.joinToString("; "), c.fakeSkills.joinToString("; "),
                    c.stuffer, c.domains.joinToString("; "), c.industry, c.fsp.sumOf { it.points }, c.lastActiveDaysAgo,
                )
                out.write(row.joinToString(",") { csv(it) } + "\n")
            }
        }
    }
}

private fun writeCandidates(file: File, e: Evaluation) {
    file.bufferedWriter().use { out ->
        out.write("candidate_id,specialization,true_grade,assessed_grade,declared_grade,competence_q,test_score,true_skills,self_stack," +
            "fake_skills,stuffer,true_industries,industry_field,fsp_results,last_active_days,experience_text\n")
        for (c in e.market.candidates) {
            val fsp = c.fsp.joinToString("; ") { "${it.level}/${it.result}/${it.eventDate}/${it.points}" }
            val row = listOf(c.id, c.specialization, c.trueGrade, c.assessedGrade, c.declaredGrade, f(c.competence, 3), c.testScore,
                c.trueSkills.joinToString("; "), c.selfStack.joinToString("; "), c.fakeSkills.joinToString("; "), c.stuffer,
                c.domains.joinToString("; "), c.industry, fsp, c.lastActiveDaysAgo, c.experience)
            out.write(row.joinToString(",") { csv(it) } + "\n")
        }
    }
}
