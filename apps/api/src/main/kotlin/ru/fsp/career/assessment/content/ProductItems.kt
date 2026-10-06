package ru.fsp.career.assessment.content

import ru.fsp.career.assessment.FamilyKind.PARAMETRIC
import ru.fsp.career.assessment.FamilyKind.VARIANTS
import ru.fsp.career.assessment.Generated
import ru.fsp.career.assessment.Grade.JUNIOR
import ru.fsp.career.assessment.Grade.MIDDLE
import ru.fsp.career.assessment.Grade.SENIOR
import ru.fsp.career.assessment.ItemFamily
import ru.fsp.career.assessment.Variant
import ru.fsp.career.assessment.choice
import ru.fsp.career.assessment.family
import ru.fsp.career.assessment.numeric
import ru.fsp.career.assessment.orderedChoice
import ru.fsp.career.assessment.pick
import ru.fsp.career.assessment.range
import ru.fsp.career.assessment.ru
import ru.fsp.career.assessment.textTable
import ru.fsp.career.assessment.variant
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random

/* Product management families: metrics, unit economics, experiments, prioritization, research. */

private val PR = setOf("product")
private val PR_DATA = setOf("product", "data")
private val PR_DS = setOf("product", "design")

private fun thousands(value: Int): String = "%,d".format(java.util.Locale.ROOT, value).replace(',', ' ')

private fun combinedRetention(rng: Random): Generated {
    val na = 100 * rng.range(8, 50)
    var nb: Int
    do { nb = 100 * rng.range(8, 50) } while (maxOf(na, nb) < 1.5 * minOf(na, nb))
    val ra = rng.range(10, 45); var rb: Int
    do { rb = rng.range(10, 45) } while (kotlin.math.abs(ra - rb) < 6)
    val combined = (na * ra + nb * rb).toDouble() / (na + nb)
    return numeric(
        "Когорта марта: ${thousands(na)} новых пользователей, retention D7 = $ra %. Когорта апреля: ${thousands(nb)} пользователей, retention D7 = $rb %. Чему равен retention D7 объединённой когорты, % (с точностью до десятых)?",
        combined, 0.051, unit = "%")
}

private fun funnel(rng: Random): Generated {
    val visits = 1000 * rng.range(10, 50)
    val cart = (visits * rng.range(8, 25) / 100.0).roundToInt()
    val checkout = (cart * rng.range(30, 70) / 100.0).roundToInt()
    val paid = (checkout * rng.range(40, 85) / 100.0).roundToInt()
    val steps = listOf("Визиты" to visits, "Добавили в корзину" to cart, "Начали оформление" to checkout, "Оплатили" to paid)
    val (from, to) = rng.pick(listOf(0 to 3, 1 to 3, 0 to 2, 1 to 2))
    val table = textTable("funnel", listOf("шаг", "пользователи"), steps.map { listOf(it.first, it.second) })
    return numeric(
        "Воронка интернет-магазина за неделю (см. таблицу). Какова конверсия из шага «${steps[from].first}» в шаг «${steps[to].first}», % (с точностью до десятых)?",
        100.0 * steps[to].second / steps[from].second, 0.051, table, unit = "%")
}

private fun arppu(rng: Random): Generated {
    val users = 1000 * rng.range(50, 400)
    val paying = (users * rng.range(2, 10) / 100.0).roundToInt()
    val revenue = 10_000 * rng.range(100, 980)
    return if (rng.nextBoolean()) {
        numeric("За месяц: ${thousands(users)} активных пользователей (MAU), из них ${thousands(paying)} платящих, выручка ${thousands(revenue)} ₽. Чему равен ARPU за месяц, ₽? Округлите до целого.", revenue.toDouble() / users, 0.51, unit = "₽")
    } else {
        numeric("За месяц: ${thousands(users)} активных пользователей (MAU), из них ${thousands(paying)} платящих, выручка ${thousands(revenue)} ₽. Чему равен ARPPU за месяц, ₽? Округлите до целого.", revenue.toDouble() / paying, 0.51, unit = "₽")
    }
}

/** LTV under constant churn: margin per month / monthly churn. */
private fun ltv(rng: Random): Generated {
    val arpu = 50 * rng.range(4, 30); val margin = rng.pick(listOf(40, 50, 60, 70, 80)); val churn = rng.pick(listOf(2, 4, 5, 8, 10))
    val value = arpu * margin / 100.0 / (churn / 100.0)
    val base = "Подписочный сервис: ARPU $arpu ₽ в месяц, валовая маржа $margin %, ежемесячный отток $churn % (считайте его постоянным)."
    return if (rng.nextBoolean()) {
        numeric("$base Оцените LTV клиента по валовой прибыли, ₽.", value, maxOf(1.0, value * 0.01), unit = "₽")
    } else {
        val cac = 100 * rng.range(5, 60)
        numeric("$base CAC = $cac ₽. Чему равно отношение LTV/CAC (LTV по валовой прибыли), с точностью до десятых?", value / cac, 0.051)
    }
}

private fun payback(rng: Random): Generated {
    val arpu = 50 * rng.range(6, 30); val margin = rng.pick(listOf(50, 60, 70, 80))
    val monthly = arpu * margin / 100.0
    val cac = 500 * rng.range(2, 24)
    return numeric(
        "CAC = $cac ₽, ARPU = $arpu ₽ в месяц, валовая маржа $margin %. Через сколько полных месяцев накопленная валовая прибыль с клиента впервые покроет CAC? Отток не учитывайте.",
        ceil(cac / monthly).toInt(), unit = "мес.")
}

private fun rice(rng: Random): Generated {
    val names = listOf("Онбординг-тур", "Оплата через СБП", "Тёмная тема", "Push-напоминания", "Реферальная программа", "Поиск по истории заказов", "Экспорт в Excel")
    var features: List<Pair<String, List<Double>>>
    var scores: List<Double>
    do {
        features = names.shuffled(rng).take(4).map { name ->
            name to listOf(500.0 * rng.range(1, 40), rng.pick(listOf(0.25, 0.5, 1.0, 2.0, 3.0)), rng.pick(listOf(50.0, 80.0, 100.0)), rng.range(1, 6).toDouble())
        }
        scores = features.map { (_, f) -> f[0] * f[1] * f[2] / 100 / f[3] }
        val sorted = scores.sortedDescending()
    } while (sorted[0] < 1.15 * sorted[1])
    val table = textTable("backlog", listOf("фича", "reach / квартал", "impact", "confidence, %", "effort, чел.-мес."),
        features.map { (name, f) -> listOf(name, f[0].toInt(), ru(f[1], 2), f[2].toInt(), f[3].toInt()) })
    val best = features[scores.indices.maxBy { scores[it] }].first
    return choice(rng, "Какую фичу из бэклога взять первой по модели RICE?", best, features.map { it.first } - best, table)
}

private fun abDecision(rng: Random): Generated {
    val ship = "Раскатывать вариант B: эффект значим, сплит корректен, guardrail-метрики в норме"
    val noEffect = "Статистически значимого эффекта нет — нельзя утверждать, что вариант B лучше"
    val srm = "Результатам нельзя доверять: группы разбалансированы (SRM), сначала искать ошибку сплитования"
    val guardrail = "Не раскатывать: ухудшилась guardrail-метрика, хотя основная выросла"
    val peeking = "Вывод ненадёжен: тест остановили досрочно при первом p < 0,05 (peeking)"
    val all = listOf(ship, noEffect, srm, guardrail, peeking)
    val scenario = rng.nextInt(5)
    val planned = rng.pick(listOf(14, 21, 28))
    val days = if (scenario == 4) rng.range(3, 5) else planned
    val total = 1000 * rng.range(60, 200)
    val nA: Int; val nB: Int
    if (scenario == 2) { val skew = rng.range(20, 40) / 1000.0; nA = (total * (0.5 + skew / 2)).roundToInt(); nB = total - nA }
    else { val d = rng.range(-150, 150); nA = total / 2 + d; nB = total - nA }
    val low = if (scenario == 1) -rng.range(1, 3) / 10.0 else rng.range(1, 4) / 10.0
    val high = low + rng.range(3, 6) / 10.0
    val guard = if (scenario == 3) "доля возвратов выросла на ${ru(rng.range(4, 9) / 10.0)} п.п., 95 % ДИ целиком выше согласованного порога в 0,3 п.п."
        else "доля возвратов без значимых изменений"
    val text = "Тест «новая карточка товара», сплит 50/50, план — $planned дней. Тест шёл $days дней" +
        (if (scenario == 4) ", остановлен, как только p-value впервые опустился ниже 0,05" else "") +
        ". Пользователей: A — ${thousands(nA)}, B — ${thousands(nB)}. Конверсия в покупку: разница B − A, 95 % ДИ [${ru(low)}; ${ru(high)}] п.п. Guardrail: $guard. Какой вывод верный?"
    return choice(rng, text, all[scenario], all - all[scenario])
}

private fun elasticity(rng: Random): Generated {
    val up = rng.range(5, 30); val down = rng.range(2, 35)
    return numeric(
        "Цену подписки подняли на $up %, число продаж упало на $down %. На сколько процентов изменилась выручка? Ответ со знаком, с точностью до десятых.",
        ((1 + up / 100.0) * (1 - down / 100.0) - 1) * 100, 0.051, unit = "%")
}

private fun research(rng: Random): Generated {
    val task = rng.pick(listOf("оплачиваете коммунальные услуги", "планируете семейный бюджет", "ищете репетитора для ребёнка", "заказываете продукты на неделю"))
    return variant(rng, listOf(
        Variant("Команда проводит интервью, чтобы понять, как люди решают задачу. Какой вопрос даст наименее искажённые данные?",
            "«Расскажите, как вы в последний раз $task. Что было сложнее всего?»",
            listOf("«Вам бы понравилось приложение, которое делает это за вас?»", "«Сколько бы вы заплатили за такую функцию?»",
                "«Удобно ли вам и быстро ли сейчас это делать?»", "«Вы ведь согласны, что текущие сервисы неудобные?»")),
        Variant("Сколько респондентов обычно достаточно для одного раунда качественного юзабилити-тестирования, чтобы найти основную массу проблем?",
            "5–8 человек на сегмент, затем исправить и повторить раунд",
            listOf("Не меньше 100, иначе выводы незначимы", "1–2 человека — этого всегда достаточно", "Ровно 30 — по центральной предельной теореме")),
        Variant("Продакт хочет проверить, будут ли клиенты платить за новую функцию, до её разработки. Какой способ надёжнее?",
            "Тест спроса с реальным действием: фейковая кнопка/лендинг с оплатой или предзаказом",
            listOf("Спросить в опросе «Будете ли вы платить?»", "Провести фокус-группу из сотрудников", "Посмотреть, есть ли такая функция у конкурентов")),
        Variant("Что описывает формулировка Job To Be Done?",
            "Прогресс, которого человек хочет добиться в определённой ситуации, независимо от конкретного продукта",
            listOf("Список функций, которые нужно разработать", "Демографический портрет целевого пользователя", "Задачу в трекере команды разработки")),
        Variant("В опросе 70 % пользователей сказали, что «точно будут пользоваться» новой функцией. Как к этому относиться?",
            "Как к слабому сигналу: заявленные намерения завышают реальное поведение, нужна проверка действием (прототип, A/B, фейковая дверь)",
            listOf("Как к доказательству: 70 % — это статистически значимо", "Сразу закладывать 70 % проникновения в прогноз выручки", "Повторить тот же опрос, чтобы подтвердить цифру")),
        Variant("После интервью у команды 40 разрозненных цитат пользователей. Как превратить их в выводы?",
            "Сгруппировать наблюдения (аффинити-диаграмма), выделить повторяющиеся паттерны и проверить их на следующих интервью",
            listOf("Выбрать 3 самые яркие цитаты для презентации", "Посчитать долю упоминаний и считать её долей рынка", "Отдать цитаты разработке как список требований")),
        Variant("Кого лучше позвать на интервью, чтобы понять причины оттока из подписки?",
            "Недавно отменивших подписку пользователей целевого сегмента (и для сравнения — активных)",
            listOf("Сотрудников компании, которые пользуются продуктом", "Самых лояльных пользователей с 3+ годами подписки", "Случайных прохожих без опыта использования")),
        Variant("Респондент на интервью предлагает: «Добавьте кнопку экспорта в Excel». Что делать с этим?",
            "Выяснить, какую задачу он решает экспортом и что делает с данными дальше — решение может быть другим",
            listOf("Сразу добавить в бэклог как требование", "Проигнорировать — пользователи не знают, чего хотят", "Спросить, сколько он готов заплатить за кнопку")),
    ))
}

private fun northStar(rng: Random) = variant(rng, listOf(
    Variant("Маркетплейс услуг (мастера и клиенты). Какая метрика лучше подходит как North Star?",
        "Число успешно выполненных заказов в неделю",
        listOf("Число установок приложения", "Число зарегистрированных мастеров", "Средняя оценка в сторе", "Число просмотров карточек")),
    Variant("B2B-сервис для командной работы. Какая метрика лучше подходит как North Star?",
        "Число активных команд, выполнивших ключевое действие за неделю",
        listOf("Число регистраций", "Число отправленных маркетинговых писем", "Количество функций в продукте", "Число посещений лендинга")),
    Variant("Онлайн-школа программирования. Какая метрика лучше подходит как North Star?",
        "Число учеников, завершивших хотя бы один урок за неделю",
        listOf("Число купленных курсов", "Число подписчиков в соцсетях", "Время на сайте", "Число записей на вебинары")),
    Variant("Сервис подкастов. Какая метрика лучше подходит как North Star?",
        "Суммарное время прослушивания в неделю",
        listOf("Число скачиваний приложения", "Число подкастов в каталоге", "Число нажатий кнопки «Поделиться»", "Количество регистраций")),
    Variant("Сервис бронирования отелей. Какая метрика лучше подходит как North Star?",
        "Число ночей, забронированных и проведённых гостями, в неделю",
        listOf("Число просмотров страниц отелей", "Число отелей в каталоге", "Число поисковых запросов", "Средний чек бронирования")),
    Variant("Банковское приложение для малого бизнеса. Какая метрика лучше подходит как North Star?",
        "Число компаний, проводящих через приложение регулярные платежи каждую неделю",
        listOf("Число скачиваний приложения", "Число открытых счетов за всё время", "Число пушей, отправленных клиентам", "Число обращений в поддержку")),
    Variant("Сервис доставки продуктов. Какая метрика лучше подходит как North Star?",
        "Число заказов, доставленных вовремя и без замен, в неделю",
        listOf("Число установок приложения", "Число товаров в ассортименте", "Число промокодов, выданных за месяц", "Средняя оценка курьеров")),
    Variant("Платформа онлайн-курсов для компаний. Какая метрика лучше подходит как North Star?",
        "Число сотрудников клиентов, завершивших назначенное обучение за месяц",
        listOf("Число курсов в каталоге", "Число демо-звонков отдела продаж", "Количество лайков под курсами", "Число регистраций HR-менеджеров")),
))

private class OkrContext(val product: String, val metric: String, val unit: String, val growing: Boolean)

private fun okr(rng: Random): Generated {
    val c = rng.pick(listOf(
        OkrContext("мобильного банка", "долю клиентов, оформивших вклад онлайн,", "%", true),
        OkrContext("сервиса доставки", "долю заказов, доставленных вовремя,", "%", true),
        OkrContext("онлайн-кинотеатра", "долю подписчиков, продливших подписку,", "%", true),
        OkrContext("edtech-платформы", "долю учеников, дошедших до 5-го урока,", "%", true),
        OkrContext("службы поддержки", "среднее время первого ответа", "мин", false),
        OkrContext("маркетплейса", "долю возвратов", "%", false),
        OkrContext("приложения такси", "среднее время подачи машины", "мин", false),
        OkrContext("B2B SaaS", "долю клиентов, использующих продукт еженедельно,", "%", true),
    ))
    val from = rng.range(12, 40); val delta = rng.range(4, 10); val q = rng.range(1, 4)
    val correct = if (c.growing) "Увеличить ${c.metric} с $from ${c.unit} до ${from + delta} ${c.unit} к концу Q$q"
    else "Снизить ${c.metric} с ${from + delta} ${c.unit} до $from ${c.unit} к концу Q$q"
    return choice(rng, "Цель команды ${c.product} на квартал. Какой ключевой результат (KR) сформулирован правильно?", correct,
        listOf("Запустить новый экран онбординга", "Улучшить пользовательский опыт", "Провести 20 интервью с пользователями",
            "Сделать продукт лучше конкурентов", "Выпустить 5 релизов за квартал", "Стремиться к улучшению метрики «${c.metric.trimEnd(',')}»"))
}

/** Lehr's rule: n per group ≈ 16·p(1 − p) / δ² for α = 0.05 (two-sided) and power 80 %. */
private fun sampleSize(rng: Random): Generated {
    val p = rng.pick(listOf(2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30))
    val delta = rng.pick(listOf(0.3, 0.5, 0.8, 1.0, 1.5, 2.0, 3.0).filter { it < p / 2.0 })
    val n = 16 * (p / 100.0) * (1 - p / 100.0) / ((delta / 100) * (delta / 100))
    // Two significant digits: options are 4× apart, so rounding never makes two of them equal.
    fun label(v: Double): String {
        val magnitude = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(v)) - 1)
        return "≈ ${thousands(((v / magnitude).roundToInt() * magnitude).roundToInt())}"
    }
    // The correct value sits at position 2 or 3 of four ordered options.
    val shown = if (rng.nextBoolean()) listOf(n / 16, n / 4, n, n * 4) else listOf(n / 4, n, n * 4, n * 16)
    return orderedChoice(
        "Конверсия в покупку — $p %. Хотим заметить абсолютный рост на ${ru(delta)} п.п. (α = 0,05, двусторонний тест, мощность 80 %). Какой размер выборки нужен на каждую группу?",
        shown.map(::label), shown.indexOf(n))
}

val productFamilies: List<ItemFamily> = listOf(
    family("pr.retention", "Retention объединённых когорт", "product-metrics", PR, MIDDLE, 1.5, -0.4, PARAMETRIC, 0, ::combinedRetention),
    family("pr.funnel", "Конверсии воронки", "product-metrics", PR_DATA, JUNIOR, 1.4, -0.8, PARAMETRIC, 0, ::funnel),
    family("pr.arppu", "ARPU и ARPPU", "product-metrics", PR, JUNIOR, 1.4, -1.0, PARAMETRIC, 0, ::arppu),
    family("pr.ltv", "LTV и LTV/CAC", "unit-economics", PR, MIDDLE, 1.4, 0.4, PARAMETRIC, 0, ::ltv),
    family("pr.payback", "Срок окупаемости CAC", "unit-economics", PR, MIDDLE, 1.3, 0.2, PARAMETRIC, 0, ::payback),
    family("pr.elasticity", "Цена и выручка", "unit-economics", PR, MIDDLE, 1.3, 0.0, PARAMETRIC, 0, ::elasticity),
    family("pr.rice", "Приоритизация RICE", "prioritization", PR, JUNIOR, 1.2, -0.4, PARAMETRIC, 4, ::rice),
    family("pr.okr", "Формулировка OKR", "prioritization", PR, JUNIOR, 1.0, -0.9, VARIANTS, 4, ::okr),
    family("pr.ab.decision", "Интерпретация A/B-теста", "experiments", PR, SENIOR, 1.2, 1.0, PARAMETRIC, 4, ::abDecision),
    family("pr.sample-size", "Размер выборки A/B-теста", "experiments", PR_DATA, SENIOR, 1.1, 1.3, PARAMETRIC, 4, ::sampleSize),
    family("pr.research", "Интервью и проверка спроса", "research", PR_DS, MIDDLE, 1.0, 0.1, VARIANTS, 4, ::research),
    family("pr.northstar", "North Star метрика", "product-metrics", PR, MIDDLE, 1.0, 0.5, VARIANTS, 4, ::northStar),
)
