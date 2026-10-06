package ru.fsp.career.assessment.validation

import ru.fsp.career.assessment.AssessmentCatalog
import ru.fsp.career.assessment.Cat
import ru.fsp.career.assessment.Grade
import ru.fsp.career.assessment.Grading
import ru.fsp.career.assessment.ItemBank
import ru.fsp.career.assessment.RetakePolicy
import ru.fsp.career.assessment.Topics

/** Renders the study results as the Russian Markdown report docs/validation/assessment-results.md. */
class Report(private val r: Map<String, Any?>) {
    @Suppress("UNCHECKED_CAST")
    private fun Any?.m(): Map<String, Any?> = this as Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    private fun Any?.l(): List<Any?> = this as List<Any?>
    private fun Any?.d(): Double = (this as Number).toDouble()
    private fun Map<String, Any?>.d(key: String) = this[key].d()

    private fun pct(v: Any?) = if (v == null) "—" else "%.1f %%".format(java.util.Locale.ROOT, v.d() * 100).replace('.', ',')
    private fun num(v: Any?, digits: Int = 2) = if (v == null) "—" else "%.${digits}f".format(java.util.Locale.ROOT, v.d()).replace('.', ',')
    private fun signed(v: Any?, digits: Int = 1) = (if (v.d() >= 0) "+" else "−") + num(kotlin.math.abs(v.d()), digits)
    private fun spec(id: String) = AssessmentCatalog.label(AssessmentCatalog.specializations, id)

    private val specs = AssessmentCatalog.specializations.map { it.id }
    private val meta = r["meta"].m()
    private val main = r["main"].m()
    private val pooled = r["pooled"].m()
    private val legacy = pooled["legacy"].m()
    private val leak = r["leak"].m()
    private val sensitivity = r["sensitivity"].m()

    fun markdown(): String = buildString {
        header(); summary(); method(); accuracy(); consistency(); discrimination(); comparability(); leaks(); sensitivityTables(); limitations()
    }

    private fun StringBuilder.header() {
        appendLine("# Валидация механики тестирования кандидатов")
        appendLine()
        appendLine("> Файл сгенерирован командой `gradle validateAssessment` (`apps/api`), seed ${meta["seed"]}. Сырые числа — в [`assessment-results.json`](assessment-results.json).")
        appendLine("> **Это синтетическое исследование.** Параметры заданий (a, b) — экспертные априорные оценки команды, ответы «кандидатов» сгенерированы моделью IRT. Цифры показывают, как механика ведёт себя при заявленных предпосылках, и служат базой для пилотной калибровки на реальных людях (раздел 9), а не измерением качества на живых кандидатах.")
        appendLine()
    }

    private fun StringBuilder.summary() {
        val attack = leak["attack"].m()
        fun leakAvg(scenario: String, key: String) = specs.map { attack[it].m()[scenario].m().d(key) }.average()
        val gain1000 = leakAvg("leak1000", "scoreGain")
        val hit1000 = leakAvg("leak1000", "hitRate")
        val overlap = specs.map { leak["overlap"].m()[it].m().d("identicalInstanceShare") }.average()
        val legacyOverlap = specs.map { leak["overlap"].m()[it].m().d("legacyIdenticalQuestionShare") }.average()
        appendLine("## 1. Итог")
        appendLine()
        appendLine("${pooled["n"]} синтетических кандидатов (по ${meta["simuleesPerSpecialization"]} на каждую из 5 специализаций), каждый прошёл путь «опрос → заявленный грейд → адаптивный тест → при необходимости тест на уровень ниже/выше» дважды на независимых экземплярах заданий. Тот же набор кандидатов прогнан через прежний механизм (5 случайных вопросов из публичного банка, грейд по доле верных).")
        appendLine()
        appendLine("| Критерий жюри | Метрика | Новый механизм | Прежний механизм |")
        appendLine("|---|---|---|---|")
        appendLine("| Соответствие эталону | точность грейда (accuracy) | **${pct(pooled["accuracy"])}** | ${pct(legacy["accuracy"])} |")
        appendLine("| Соответствие эталону | macro-F1 по трём грейдам | **${num(pooled["macroF1"])}** | ${num(legacy["macroF1"])} |")
        appendLine("| Корректность категоризации | доля завышенных грейдов | **${pct(pooled["overGrading"])}** | ${pct(legacy["overGrading"])} |")
        appendLine("| Согласованность | квадратично-взвешенная каппа, повтор на других экземплярах | **${num(pooled["retestQwk"])}** | ${num(legacy["retestQwk"])} |")
        appendLine("| Согласованность | корреляция оценок двух прохождений | **${num(pooled["retestThetaCorrelation"])}** | ${num(legacy["retestScoreCorrelation"])} |")
        appendLine("| Дискриминативность | корреляция оценки с истинным уровнем θ | **${num(pooled["thetaCorrelation"])}** | ${num(legacy["scoreThetaCorrelation"])} |")
        appendLine("| Дискриминативность | средняя item–rest корреляция семейств | **${num(r["itemAnalysis"].m()["meanItemRest"])}** | — (не измерялась) |")
        appendLine("| Устойчивость к утечкам | доля идентичных заданий у двух кандидатов | **${pct(overlap)}** | ${pct(legacyOverlap)} |")
        appendLine("| Устойчивость к утечкам | доля заданий теста, ответ на которые есть в утечке (1000 показанных вопросов / весь прежний банк из 25) | **${pct(hit1000)}** | 100 % |")
        appendLine("| Устойчивость к утечкам | эффект для выучившего эту утечку | **${signed(gain1000)} балла; завышение грейда ${pct(leakAvg("leak1000", "overGradingHonest"))} → ${pct(leakAvg("leak1000", "overGradingAttack"))}** | 100 баллов и Senior у всех (завышение ${pct(leakAvg("legacyFullBankMemorized", "overGradingAttack"))}) |")
        appendLine()
        appendLine("Главный вывод: прежний механизм почти всех относил к Senior (${pct(legacy["seniorShare"])} кандидатов), поэтому был «стабилен», но не различал уровни, а его банк из 25 вопросов выучивался целиком. Новый механизм различает грейды, воспроизводит их при повторе на других экземплярах заданий, а утечка даже тысячи реально показанных вопросов даёт лишь частичный выигрыш — он сосредоточен в концептуальных семействах с вариантами и в узких генераторах, которые мы видим в разделе 7 и расширяем в первую очередь.")
        appendLine()
    }

    private fun StringBuilder.method() {
        appendLine("## 2. Механика и модель исследования")
        appendLine()
        appendLine("**Механика (production-код, `apps/api/src/main/kotlin/ru/fsp/career/assessment`).**")
        appendLine()
        appendLine("- Банк — ${meta["families"]} семейств заданий (${meta["parametricFamilies"]} параметрических генераторов и ${ItemBank.families.size - meta["parametricFamilies"].d().toInt()} наборов изоморфных вариантов). Семейство = один навык одной сложности с параметрами 2PL: дискриминативность *a* и трудность *b*; у заданий с выбором ответа фиксирована нижняя асимптота *c* = 1/число вариантов. Каждый кандидат получает свой экземпляр (новые числа, таблицы, код), ответ сервер пересчитывает из seed и хранит только у себя.")
        appendLine("- Размер пула по специализациям: " + specs.joinToString(", ") { "${spec(it)} — ${meta["poolSizes"].m()[it]}" } + ".")
        appendLine("- Адаптивный тест: ${Cat.TEST_LENGTH} заданий, ${Cat.EXPIRY_MINUTES} минут. Старт в якоре заявленного грейда (junior ${num(Grade.JUNIOR.anchor)}, middle ${num(Grade.MIDDLE.anchor)}, senior ${num(Grade.SENIOR.anchor)}); следующее семейство — максимум информации Фишера при текущей оценке в пределах квот тематического blueprint, среди почти равных (≥ ${(Cat.SIMILAR_INFORMATION * 100).toInt()} % от лучшей информации) — наименее показанное (контроль экспозиции).")
        appendLine("- Оценка уровня: EAP с популяционным априорным N(0, 1) — заявленный грейд влияет только на маршрут теста, но не на итоговую оценку. Балл 0–100 = 100·Φ(θ̂).")
        appendLine("- Пороги грейдов на шкале θ: Middle ≥ ${num(Grade.MIDDLE.cut, 1)}, Senior ≥ ${num(Grade.SENIOR.cut, 1)} (≈ ${Grading.score(Grade.MIDDLE.cut)} и ${Grading.score(Grade.SENIOR.cut)} баллов); входной уровень Junior — ${num(Grade.JUNIOR.cut, 1)}. Заявленный грейд подтверждён, если θ̂ − k·SE ≥ порога, k = ${num(Grading.K)} (≈ «апостериорная вероятность ≥ Φ(k)»). «Уверенно» — та же нижняя граница выше порога следующего грейда.")
        appendLine("- Грейд не понижается: подтверждённый грейд = max(прежний, заявленный при успехе); первая неудача даёт Junior по умолчанию. Пересдачи — раз в ${RetakePolicy.COOLDOWN_DAYS} дней; сразу можно только (а) после неудачи — тест на уровень ниже, (б) после уверенного успеха — тест на уровень выше (если он не был провален в окне). Брошенная попытка закрывается как неудачная.")
        appendLine()
        appendLine("**Модель симуляции.**")
        appendLine()
        appendLine("- Истинный уровень θ ~ N(0, 1); **эталонная разметка** — полоса θ по тем же порогам: Junior < ${num(Grade.MIDDLE.cut, 1)} ≤ Middle < ${num(Grade.SENIOR.cut, 1)} ≤ Senior (распределение эталона: ${pooled["referenceDistribution"].m().entries.joinToString(", ") { "${it.key} ${it.value}" }}).")
        appendLine("- Самооценка: заявленный грейд = полоса (θ + 0,15 + 0,6·ε) — кандидаты в среднем немного переоценивают себя.")
        appendLine("- Ответ на экземпляр: вероятность по 3PL с параметрами семейства, но трудность конкретного экземпляра смещена на δ ~ N(0; ${num(meta["sigmaInstance"])}²) — моделируем неидеальную изоморфность. Движок о δ не знает и оценивает по параметрам семейства (консервативная предпосылка).")
        appendLine("- Симуляция вызывает те же объекты, что и API: генераторы `ItemBank`, `Blueprints`, `Cat.nextFamily`, EAP `LikelihoodGrid`, `Grading`, `RetakePolicy`.")
        appendLine("- Прежний механизм: 3 вопроса специализации + 2 общих из публичного банка из 25 вопросов с 3 вариантами; априорные параметры этих вопросов по их содержанию: a ∈ [0,6; 1,0], b ∈ [−2,6; −1,4], c = 1/3; грейд: ≥ 80 % Senior, ≥ 60 % Middle.")
        appendLine()
    }

    private fun confusion(title: String, matrix: Any?) = buildString {
        appendLine("| $title | → Junior | → Middle | → Senior |")
        appendLine("|---|---|---|---|")
        matrix.l().forEachIndexed { i, row -> appendLine("| эталон ${Grade.entries[i].label} | " + row.l().joinToString(" | ") + " |") }
    }

    private fun StringBuilder.accuracy() {
        appendLine("## 3. Соответствие эталону и корректность категоризации")
        appendLine()
        appendLine("| Специализация | N | Accuracy | Macro-F1 | Завышение | Занижение | Попыток на путь | Прежний: accuracy |")
        appendLine("|---|---|---|---|---|---|---|---|")
        for (s in specs) {
            val x = main[s].m()
            appendLine("| ${spec(s)} | ${x["n"]} | ${pct(x["accuracy"])} | ${num(x["macroF1"])} | ${pct(x["overGrading"])} | ${pct(x["underGrading"])} | ${num(x["meanAttempts"])} | ${pct(x["legacy"].m()["accuracy"])} |")
        }
        appendLine("| **Все** | ${pooled["n"]} | **${pct(pooled["accuracy"])}** | **${num(pooled["macroF1"])}** | ${pct(pooled["overGrading"])} | ${pct(pooled["underGrading"])} | ${num(pooled["meanAttempts"])} | ${pct(legacy["accuracy"])} |")
        appendLine()
        appendLine("Матрица ошибок нового механизма (все специализации, подтверждённый грейд после пути):")
        appendLine()
        append(confusion("Эталон \\ присвоено", pooled["confusion"]))
        appendLine()
        appendLine("Та же выборка, прежний механизм:")
        appendLine()
        append(confusion("Эталон \\ присвоено", legacy["confusion"]))
        appendLine()
        val by = pooled["byDeclaration"].m()
        appendLine("Влияние самооценки (путь с тестом на уровень ниже/выше исправляет неверно заявленный грейд):")
        appendLine()
        appendLine("| Заявленный грейд | Доля | Accuracy | Попыток на путь |")
        appendLine("|---|---|---|---|")
        for ((key, label) in listOf("overDeclared" to "завышен", "exact" to "верный", "underDeclared" to "занижен")) {
            val g = by[key].m()
            appendLine("| $label | ${pct(g.d("n") / pooled.d("n"))} | ${pct(g["accuracy"])} | ${num(g["meanAttempts"])} |")
        }
        appendLine()
        appendLine("Решение по одной попытке относительно заявленного грейда: чувствительность (истинный уровень ≥ порога → подтверждён) ${pct(pooled["decisionSensitivity"])}, специфичность (ниже порога → не подтверждён) ${pct(pooled["decisionSpecificity"])}.")
        appendLine()
    }

    private fun StringBuilder.consistency() {
        appendLine("## 4. Согласованность: повторное прохождение")
        appendLine()
        appendLine("Каждый кандидат проходит весь путь второй раз на независимо сгенерированных экземплярах (другие числа, таблицы, варианты) — это строже обычного test–retest, где задания совпадают.")
        appendLine()
        appendLine("| Специализация | Совпадение грейда | Каппа (квадр.) | Каппа (лин.) | r(θ̂₁, θ̂₂) | r(θ̂, θ) | Средняя SE | Надёжность | Совпадение решения |")
        appendLine("|---|---|---|---|---|---|---|---|---|")
        for (s in specs + "all") {
            val x = if (s == "all") pooled else main[s].m()
            val name = if (s == "all") "**Все**" else spec(s)
            appendLine("| $name | ${pct(x["retestAgreement"])} | ${num(x["retestQwk"])} | ${num(x["retestLinearKappa"])} | ${num(x["retestThetaCorrelation"])} | ${num(x["thetaCorrelation"])} | ${num(x["meanSe"])} | ${num(x["marginalReliability"])} | ${pct(x["decisionConsistency"])} |")
        }
        appendLine()
        appendLine("Прежний механизм: совпадение грейда ${pct(legacy["retestAgreement"])}, каппа ${num(legacy["retestQwk"])}, r двух баллов ${num(legacy["retestScoreCorrelation"])} — высокое совпадение объясняется тем, что почти все получают Senior, а не надёжностью (каппа около нуля).")
        appendLine()
        appendLine("«Надёжность» — маргинальная надёжность var(θ̂) / (var(θ̂) + mean SE²); «совпадение решения» — одинаковый исход (подтверждён / нет) двух первых попыток на один и тот же заявленный грейд.")
        appendLine()
    }

    private fun StringBuilder.discrimination() {
        val ia = r["itemAnalysis"].m()
        appendLine("## 5. Дискриминативность заданий")
        appendLine()
        appendLine("Классический анализ заданий проводится на «пилотном» линейном дизайне, как его будем делать на реальных людях: ${ia["simulees"]} симулированных участников по каждой специализации решают по одному экземпляру **каждого** семейства пула. p — доля верных, r_ir — корреляция задания с суммой остальных (item–rest, точечно-бисериальная), r_θ — корреляция с истинным уровнем, E[I] — средняя информация Фишера по популяции N(0, 1), экспозиция — доля адаптивных попыток, в которых семейство было показано.")
        appendLine()
        appendLine("Средняя r_ir = **${num(ia["meanItemRest"])}**, минимальная ${num(ia["minItemRest"])}; семейств с r_ir < 0,20: ${ia["flaggedBelow020"].l().size}" + (if (ia["flaggedBelow020"].l().isEmpty()) "." else " (${ia["flaggedBelow020"].l().joinToString(", ")}) — кандидаты на переработку после пилота.") + " Максимальная экспозиция семейства в адаптивном тесте: ${pct(ia["maxCatExposure"])}.")
        appendLine()
        appendLine("| Семейство | Навык | Тема | Тип | Уровень | a | b | p | r_ir | r_θ | E[I] | Экспозиция |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|---|---|")
        for (row in ia["families"].l().map { it.m() }) {
            appendLine("| `${row["id"]}` | ${row["title"]} | ${Topics.label(row["topic"] as String)} | ${if (row["kind"] == "PARAMETRIC") "генератор" else "варианты"} | ${row["level"]} | ${num(row["a"], 1)} | ${num(row["b"], 1)} | ${num(row["pValue"])} | ${num(row["itemRest"])} | ${num(row["pointBiserialTheta"])} | ${num(row["meanInformation"])} | ${pct(row["catExposure"])} |")
        }
        appendLine()
    }

    private fun StringBuilder.comparability() {
        val c = r["comparability"].m()
        appendLine("## 6. Сопоставимость форм")
        appendLine()
        appendLine("Разные кандидаты получают разные наборы экземпляров, поэтому сопоставимость проверяется так: фиксируем истинный уровень θ и много раз прогоняем тест на независимо сгенерированных формах (${AssessmentStudy.N_CONDITIONAL} прогонов на каждую из 5 специализаций). Смещение — средняя ошибка θ̂, SD — разброс оценки между формами.")
        appendLine()
        appendLine("| θ | Заявлено | Смещение θ̂ | SD θ̂ между формами | Средняя SE | SD балла | Доля «подтверждён» |")
        appendLine("|---|---|---|---|---|---|---|")
        for (row in c["conditional"].l().map { it.m() }) {
            appendLine("| ${num(row["theta"])} | ${row["target"]} | ${signed(row["bias"], 2)} | ${num(row["sdTheta"])} | ${num(row["meanSe"])} | ${num(row["sdScore"], 1)} | ${pct(row["passRate"])} |")
        }
        appendLine()
        val lf = c["linearFormsAtTheta0"].m()
        appendLine("Почему оценка — θ по IRT, а не процент верных: ожидаемый процент верных у кандидата среднего уровня (θ = 0) на случайных формах нового банка колеблется с SD ${num(lf["newBankExpectedPercentSd"], 1)} п.п. (среднее ${num(lf["newBankExpectedPercentMean"], 1)} %), на формах прежнего банка — SD ${num(lf["oldBankExpectedPercentSd"], 1)} п.п. (среднее ${num(lf["oldBankExpectedPercentMean"], 1)} %). Процент зависит от того, какие задания выпали; θ̂ учитывает трудность каждого семейства, поэтому смещение в таблице выше остаётся малым при любом наборе заданий. Смещение к центру на краях шкалы — ожидаемое свойство EAP при 12 заданиях.")
        appendLine()
    }

    private fun StringBuilder.leaks() {
        appendLine("## 7. Устойчивость к распространению заданий")
        appendLine()
        appendLine("**(а) Пересечение тестов двух кандидатов.** Доля заданий, совпадающих дословно (тот же текст и код), у двух случайных кандидатов одной специализации:")
        appendLine()
        appendLine("| Специализация | Идентичные экземпляры | Пары хотя бы с одним совпадением | Общие семейства (навыки) | Прежний банк: идентичные вопросы |")
        appendLine("|---|---|---|---|---|")
        for (s in specs) {
            val o = leak["overlap"].m()[s].m()
            appendLine("| ${spec(s)} | ${pct(o["identicalInstanceShare"])} | ${pct(o["pairsWithAnyIdenticalInstance"])} | ${pct(o["sameFamilyShare"])} | ${pct(o["legacyIdenticalQuestionShare"])} |")
        }
        appendLine()
        val col = leak["collisions"].m()
        val worst = col["families"].l().map { it.m() }.sortedByDescending { it.d("collisionProbability") }.take(5)
        appendLine("Совпадают навыки (так и задумано — тесты сопоставимы), но не сами задания. Вероятность, что два независимых экземпляра семейства совпадут (по ${col["drawsPerFamily"]} генерациям): в среднем ${num(col.d("meanParametric") * 100, 3)} % для генераторов и ${num(col.d("meanVariants") * 100, 1)} % для наборов вариантов. Наиболее «узкие» семейства: " +
            worst.joinToString(", ") { "`${it["id"]}` (${it["distinctQuestions"]} разных вопросов, ${pct(it["collisionProbability"])})" } + " — именно их первыми расширяем и ротируем.")
        appendLine()
        appendLine("**(б) Атака заучиванием.** Утечка — дамп из K вопросов с ответами, которые реально видели честные кандидаты (выборка по показам, т. е. популярные вопросы утекают первыми). Атакующий отвечает верно на всё, что есть в дампе, остальное — по своему уровню; сравниваем с его «честным двойником» с тем же уровнем и тем же генератором случайности. Отдельно — худший случай: утекли все варианты всех концептуальных семейств.")
        appendLine()
        appendLine("| Специализация | Сценарий | Вопросов в утечке | Доля заданий из утечки | Прирост балла | Прирост θ̂ | Завышение грейда: честно → с утечкой |")
        appendLine("|---|---|---|---|---|---|---|")
        val labels = linkedMapOf("leak200" to "утечка 200", "leak1000" to "утечка 1000", "leak5000" to "утечка 5000", "allVariantFamilies" to "все варианты концептуальных семейств", "legacyFullBankMemorized" to "прежний банк выучен целиком")
        for (s in specs) {
            val a = leak["attack"].m()[s].m()
            for ((key, label) in labels) {
                val x = a[key].m()
                appendLine("| ${spec(s)} | $label | ${x["leakedQuestions"]} | ${pct(x["hitRate"])} | ${signed(x["scoreGain"])} | ${if (x["thetaGain"] == null) "—" else signed(x["thetaGain"], 2)} | ${pct(x["overGradingHonest"])} → ${pct(x["overGradingAttack"])} |")
            }
        }
        appendLine()
        appendLine("Почему так: у параметрических семейств пространство экземпляров огромно, и дамп даже из тысяч вопросов почти не пересекается с новым тестом; знание чужого экземпляра даёт только метод решения — то есть сам проверяемый навык. Уязвимы концептуальные семейства с вариантами: при полной утечке всех их вариантов прирост заметен, поэтому они составляют меньшинство, ротируются и контролируются по экспозиции и дрейфу доли верных ответов (раздел 9). Прежний банк из 25 вопросов при утечке давал 100 % верных и Senior всем.")
        appendLine()
    }

    private fun StringBuilder.sensitivityTables() {
        appendLine("## 8. Чувствительность и выбор параметров")
        appendLine()
        appendLine("По ${sensitivity["simuleesPerSpecialization"]} кандидатов на специализацию на каждую точку.")
        appendLine()
        fun table(key: String, title: String, format: (Any?) -> String) {
            appendLine("| $title | Accuracy | Macro-F1 | Завышение | Занижение | L = 2·зав. + заниж. | Каппа повтора | r(θ̂, θ) | Средняя SE | Попыток |")
            appendLine("|---|---|---|---|---|---|---|---|---|---|")
            for (row in sensitivity[key].l().map { it.m() }) {
                appendLine("| ${format(row[key])} | ${pct(row["accuracy"])} | ${num(row["macroF1"])} | ${pct(row["overGrading"])} | ${pct(row["underGrading"])} | ${pct(2 * row.d("overGrading") + row.d("underGrading"))} | ${num(row["retestQwk"])} | ${num(row["thetaCorrelation"])} | ${num(row["meanSe"])} | ${num(row["meanAttempts"])} |")
            }
            appendLine()
        }
        val rows = sensitivity["k"].l().map { it.m() }
        fun loss(row: Map<String, Any?>) = 2 * row.d("overGrading") + row.d("underGrading")
        val best = rows.minBy(::loss)
        appendLine("**Запас решения k** (θ̂ − k·SE ≥ порога). Критерий выбора — потери L = 2·завышение + занижение: завышенный грейд считаем вдвое дороже, потому что он вводит в заблуждение работодателя, а заниженный кандидат может исправить тестом на уровень выше или через 90 дней. Минимум L в сетке — при k = ${num(best["k"])} (L = ${pct(loss(best))}); в продукте k = ${num(Grading.K)}. EAP с популяционным априорным уже сдвигает оценку к среднему, поэтому дополнительный запас лишь переносит ошибки из завышения в занижение с невыгодным курсом.")
        appendLine()
        table("k", "k") { num(it) }
        appendLine("**Неидеальная изоморфность** σ — разброс трудности экземпляров внутри семейства, о котором движок не знает:")
        appendLine()
        table("sigmaInstance", "σ") { num(it) }
        appendLine("**Длина теста** (выбрано ${Cat.TEST_LENGTH} заданий за ${Cat.EXPIRY_MINUTES} минут — компромисс точности и времени кандидата):")
        appendLine()
        table("length", "Заданий") { it.toString() }
        appendLine("**Запас для «уверенного» результата** (открывает немедленный тест на грейд выше; выбрано ${num(Grading.K_UPGRADE)} — это лишь право на ещё одну попытку, сам грейд выше всё равно нужно подтвердить):")
        appendLine()
        table("kUpgrade", "k для «уверенно»") { num(it) }
        appendLine("**Правило выбора задания.** Проверили альтернативу: брать задание с максимумом информации не в текущей оценке θ̂, а в ближайшем пороге решения (как в классификационных тестах). Она не дала выигрыша, поэтому оставлен стандартный критерий — он ещё и точнее измеряет балл, по которому кандидаты ранжируются внутри категории.")
        appendLine()
        table("selection", "Критерий") { if (it == "cut") "информация в пороге" else "информация в θ̂" }
    }

    private fun StringBuilder.limitations() {
        appendLine("## 9. Ограничения и план калибровки на реальных данных")
        appendLine()
        appendLine("- **Параметры — экспертные априорные.** a и b назначены командой по содержанию заданий; эталон в симуляции определяется той же моделью, что и ответы. Поэтому результаты — верхняя оценка при верной модели; основная неопределённость — расхождение реальных параметров с априорными, частично учтённое через σ (раздел 8).")
        appendLine("- **Пилот.** Линейные формы с якорными семействами, ≥ 200–300 ответов на семейство в каждой специализации; оценка 2PL/3PL методом маргинального правдоподобия (например, R `mirt`), проверка согласия заданий, одномерности внутри специализации, локальной независимости и DIF по отраслям/форматам работы. Семейства с a < 0,6 или item–rest < 0,2 перерабатываются.")
        appendLine("- **Изоморфность.** Модель со случайным эффектом экземпляра (или LLTM по признакам генератора) — дисперсия трудности экземпляров; если σ > 0,3, семейство делится на более узкие.")
        appendLine("- **Эталонная разметка.** 60–100 добровольцев с независимой экспертной оценкой (структурированное техническое интервью двумя экспертами вслепую, согласованность экспертов по каппе); сравнение с грейдом теста по accuracy и взвешенной каппе; при необходимости — сдвиг порогов θ.")
        appendLine("- **Мониторинг дрейфа и утечек.** Для каждого семейства — доля верных ответов при фиксированном θ (контрольные карты CUSUM); резкий рост у семейства с вариантами — признак утечки: вывод из ротации и добавление вариантов. Контроль экспозиции, версионирование банка (`engine_version` в попытке), пересчёт параметров раз в квартал на накопленных ответах.")
        appendLine("- **Честность прохождения.** Время ответа на задание (аномально быстрые верные ответы на трудные задания), лимит 30 минут, одна открытая попытка, брошенная попытка = неудачная; корректность ответа во время теста не раскрывается.")
        appendLine()
    }
}
