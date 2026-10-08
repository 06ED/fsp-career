package ru.fsp.career.assessment.domain.content

import ru.fsp.career.assessment.domain.*
import ru.fsp.career.assessment.domain.FamilyKind.*
import ru.fsp.career.assessment.domain.Grade.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.random.Random

/* Data/analytics families; descriptive statistics and A/B families are shared with the product track. */

private val DATA = setOf("data")
private val DATA_PR = setOf("data", "product")

private fun centralTendency(rng: Random): Generated {
    val (context, unit) = rng.pick(listOf("Время ответа API, мс" to "мс", "Сумма чека, ₽" to "₽", "Длительность сессии, мин" to "мин", "Число заказов в день" to "шт."))
    val values = List(rng.range(7, 10)) { rng.range(3, 90) }
    val sorted = values.sorted()
    val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2].toDouble()
    else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
    return if (rng.nextBoolean()) {
        numeric("$context: ${values.joinToString(", ")}. Найдите медиану.", median, 0.0, unit = unit)
    } else {
        numeric("$context: ${values.joinToString(", ")}. Найдите среднее арифметическое (с точностью до десятых).", values.average(), 0.05, unit = unit)
    }
}

private fun percentile(rng: Random): Generated {
    val n = rng.pick(listOf(10, 12, 16, 20)); val p = rng.pick(listOf(75, 90, 95))
    val values = (100..999).shuffled(rng).take(n)
    val rank = ceil(p * n / 100.0).toInt()
    return numeric(
        "Время ответа сервиса (мс) для $n запросов: ${values.joinToString(", ")}. Найдите $p-й перцентиль методом ближайшего ранга (nearest-rank: значение с рангом ⌈p/100 · n⌉ в отсортированном списке).",
        values.sorted()[rank - 1], unit = "мс")
}

private fun standardDeviation(rng: Random): Generated {
    val values = List(rng.range(5, 6)) { rng.range(1, 20) }
    val mean = values.average()
    val sd = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    return numeric("Значения: ${values.joinToString(", ")}. Найдите стандартное отклонение генеральной совокупности (делитель n), с точностью до сотых.", sd, 0.011)
}

/**
 * Two-proportion z-test with pooled variance. The data are re-drawn until |z| is at least 0.12 away from
 * every bin boundary (z = 1.28, 1.96, 2.58), so a correct computation never lands on an ambiguous edge.
 */
private fun abSignificance(rng: Random): Generated {
    val bounds = listOf(1.2816, 1.96, 2.5758)
    // The correct bin is drawn uniformly first, so "always pick p < 0.01" is not a winning strategy.
    val targetBin = rng.nextInt(4)
    fun binOf(p: Double) = when { p < 0.01 -> 0; p < 0.05 -> 1; p < 0.2 -> 2; else -> 3 }
    var n1: Int; var n2: Int; var x1: Int; var x2: Int; var z: Double
    do {
        n1 = 500 * rng.range(2, 12); n2 = n1 + 10 * rng.range(-20, 20)
        val base = rng.range(30, 200) / 1000.0
        val lift = rng.range(-5, 40) / 1000.0
        x1 = (n1 * base).toInt(); x2 = (n2 * (base + lift)).toInt()
        val pooled = (x1 + x2).toDouble() / (n1 + n2)
        z = (x2.toDouble() / n2 - x1.toDouble() / n1) / sqrt(pooled * (1 - pooled) * (1.0 / n1 + 1.0 / n2))
    } while (bounds.any { abs(abs(z) - it) < 0.12 } || binOf(2 * (1 - Irt.normalCdf(abs(z)))) != targetBin)
    return orderedChoice(
        "A/B-тест конверсии в покупку. Контроль: $x1 покупок из $n1 пользователей; вариант: $x2 из $n2. Оцените двусторонний p-value z-теста для разницы долей (объединённая дисперсия).",
        listOf("p < 0,01", "0,01 ≤ p < 0,05", "0,05 ≤ p < 0,20", "p ≥ 0,20"), targetBin,
    )
}

private fun sqlWindow(rng: Random): Generated {
    val users = rng.range(3, 4)
    var rows: List<List<Any>>
    do {
        rows = (1..rng.range(8, 10)).map { listOf(rng.range(1, users), 50 * rng.range(1, 40)) }
    } while (rows.groupBy { it[0] }.values.any { g -> g.map { it[1] }.toSet().size != g.size } || rows.groupBy { it[0] }.size < users)
    val k = rng.range(1, 2)
    val answer = rows.groupBy { it[0] as Int }.values.sumOf { g -> g.map { it[1] as Int }.sortedDescending().take(k).sum() }
    val query = """
        SELECT SUM(amount)
        FROM (
          SELECT amount,
                 ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY amount DESC) AS rn
          FROM purchases
        ) t
        WHERE rn <= $k;
    """.trimIndent()
    val code = textTable("purchases", listOf("user_id", "amount"), rows) + "\n\n" + query
    return numeric("Какое число вернёт запрос?", answer, code, "sql")
}

private fun pandasGroupBy(rng: Random): Generated {
    val cities = listOf("Казань", "Пермь", "Томск", "Самара", "Омск").shuffled(rng).take(3)
    val rows = List(rng.range(6, 8)) { rng.pick(cities) to 10 * rng.range(5, 60) }
    val byCity = rows.groupBy({ it.first }, { it.second })
    val df = "import pandas as pd\n\ndf = pd.DataFrame({\n    \"city\": [${rows.joinToString(", ") { "\"${it.first}\"" }}],\n    \"price\": [${rows.joinToString(", ") { it.second.toString() }}],\n})\n"
    return when (rng.nextInt(3)) {
        0 -> numeric("Какое число выведет программа? Дробный результат округлите до сотых.", byCity.values.maxOf { it.average() }, 0.011,
            df + "print(df.groupby(\"city\")[\"price\"].mean().max())", "python")
        1 -> numeric("Какое число выведет программа?", byCity.values.minOf { it.sum() }.toDouble(), 0.0,
            df + "print(df.groupby(\"city\")[\"price\"].sum().min())", "python")
        else -> {
            val threshold = 10 * rng.range(15, 40)
            val filtered = rows.filter { it.second > threshold }.groupingBy { it.first }.eachCount()
            numeric("Какое число выведет программа? Если строк не останется, ответ 0.", (filtered.values.maxOrNull() ?: 0).toDouble(), 0.0,
                df + "res = df[df[\"price\"] > $threshold].groupby(\"city\").size()\nprint(res.max() if len(res) else 0)", "python")
        }
    }
}

private fun classificationMetrics(rng: Random): Generated {
    val tp = rng.range(20, 200); val fp = rng.range(5, 150); val fn = rng.range(5, 150); val tn = rng.range(500, 5000)
    val table = textTable("confusion_matrix", listOf("", "предсказано: фрод", "предсказано: норма"),
        listOf(listOf("факт: фрод", tp, fn), listOf("факт: норма", fp, tn)))
    return if (rng.nextBoolean()) {
        numeric("Модель антифрода на тестовой выборке (см. матрицу ошибок). Найдите precision для класса «фрод» с точностью до сотых.", tp.toDouble() / (tp + fp), 0.011, table)
    } else {
        numeric("Модель антифрода на тестовой выборке (см. матрицу ошибок). Найдите recall для класса «фрод» с точностью до сотых.", tp.toDouble() / (tp + fn), 0.011, table)
    }
}

private fun mlValidation(rng: Random) = variant(rng, listOf(
    Variant("Аналитик нормировал признаки (StandardScaler) на всём датасете, а затем разбил его на train и test. Что не так?",
        "Утечка данных: статистики test попали в обучение; scaler нужно обучать только на train (лучше — внутри Pipeline)",
        listOf("Ничего: нормировка не зависит от разбиения", "Нужно нормировать только test", "Нормировка вообще не нужна для деревьев, поэтому ошибки нет")),
    Variant("Модель прогнозирует спрос по дням за ${rng.range(2, 5)} года. Как правильно валидировать?",
        "Разбиение по времени (TimeSeriesSplit / walk-forward): обучение на прошлом, проверка на будущем",
        listOf("Случайный KFold с перемешиванием", "Leave-one-out по случайным дням", "Проверять на обучающей выборке")),
    Variant("Доля мошеннических транзакций — ${rng.range(1, 3)} %. Модель показывает accuracy ${rng.range(97, 99)} %. Какой вывод верный?",
        "Accuracy неинформативна при сильном дисбалансе; смотреть recall/precision, PR-AUC",
        listOf("Модель отличная, её можно выкатывать", "Нужно увеличить accuracy до 100 %", "Нужно удалить редкий класс из данных")),
    Variant("На train качество ${rng.range(95, 99)} %, на валидации ${rng.range(65, 75)} %. Что это и что делать?",
        "Переобучение: регуляризация, упрощение модели, больше данных, ранняя остановка",
        listOf("Недообучение: усложнить модель", "Утечка в валидации: удалить валидацию", "Нормальная ситуация, ничего делать не нужно")),
    Variant("В модели оттока клиентов самым важным признаком оказалась «дата закрытия договора». Что это означает?",
        "Утечка целевой переменной: признак известен только после события, его нужно исключить",
        listOf("Признак отличный — его надо усилить", "Нужно нормализовать дату", "Это мультиколлинеарность, нужно добавить L2")),
))

/** Bayes' rule on an alarm: P(fraud | alarm) = p·TPR / (p·TPR + (1 − p)·FPR). */
private fun bayesAlarm(rng: Random): Generated {
    val prevalence = rng.pick(listOf(0.2, 0.5, 1.0, 2.0, 3.0, 5.0, 8.0, 10.0))
    val sensitivity = rng.pick(listOf(70, 75, 80, 85, 90, 95, 98, 99))
    val fpr = rng.pick(listOf(1, 2, 3, 4, 5, 8, 10))
    val p = prevalence / 100
    val posterior = 100 * p * sensitivity / 100 / (p * sensitivity / 100 + (1 - p) * fpr / 100)
    return numeric(
        "Доля мошеннических транзакций — ${ru(prevalence)} %. Антифрод-модель помечает $sensitivity % мошеннических транзакций и ошибочно помечает $fpr % честных. Какова вероятность (в %), что помеченная транзакция действительно мошенническая? Округлите до целого.",
        posterior, 0.6, unit = "%")
}

private fun dedup(rng: Random): Generated {
    val days = listOf("2025-03-01", "2025-03-02", "2025-03-03")
    val rows = List(rng.range(8, 10)) { listOf(rng.range(1, 4), rng.pick(days), rng.pick(listOf("view", "click"))) }
    val pairs = rows.groupingBy { it[0] to it[1] }.eachCount()
    return if (rng.nextBoolean()) {
        numeric("Какое число вернёт запрос?", pairs.size,
            textTable("events", listOf("user_id", "event_date", "event"), rows) + "\n\nSELECT COUNT(*)\nFROM (SELECT DISTINCT user_id, event_date FROM events) t;", "sql")
    } else {
        numeric("Какое число вернёт запрос?", pairs.values.count { it > 1 },
            textTable("events", listOf("user_id", "event_date", "event"), rows) +
                "\n\nSELECT COUNT(*)\nFROM (\n  SELECT user_id, event_date\n  FROM events\n  GROUP BY user_id, event_date\n  HAVING COUNT(*) > 1\n) t;", "sql")
    }
}

val dataFamilies: List<ItemFamily> = listOf(
    family("da.stats.central", "Медиана и среднее", "statistics", DATA_PR, JUNIOR, 1.6, -1.0, PARAMETRIC, 0, ::centralTendency),
    family("da.stats.percentile", "Перцентили", "statistics", DATA, MIDDLE, 1.4, 0.3, PARAMETRIC, 0, ::percentile),
    family("da.stats.std", "Стандартное отклонение", "statistics", DATA, MIDDLE, 1.4, 0.0, PARAMETRIC, 0, ::standardDeviation),
    family("da.ab.ztest", "Значимость A/B-теста", "experiments", DATA_PR, SENIOR, 1.3, 1.3, PARAMETRIC, 4, ::abSignificance),
    family("da.sql.window", "SQL: оконные функции", "sql", DATA, SENIOR, 1.4, 0.8, PARAMETRIC, 0, ::sqlWindow),
    family("da.pandas.groupby", "pandas: группировки", "python", DATA, MIDDLE, 1.4, 0.1, PARAMETRIC, 0, ::pandasGroupBy),
    family("da.ml.metrics", "Precision и recall", "ml", DATA, MIDDLE, 1.5, 0.0, PARAMETRIC, 0, ::classificationMetrics),
    family("da.ml.validation", "Валидация моделей и утечки", "ml", DATA, MIDDLE, 1.1, 0.5, VARIANTS, 4, ::mlValidation),
    family("da.prob.bayes", "Теорема Байеса", "statistics", DATA, SENIOR, 1.3, 1.2, PARAMETRIC, 0, ::bayesAlarm),
    family("da.etl.dedup", "Дедупликация данных", "sql", DATA, JUNIOR, 1.3, -0.5, PARAMETRIC, 0, ::dedup),
)
