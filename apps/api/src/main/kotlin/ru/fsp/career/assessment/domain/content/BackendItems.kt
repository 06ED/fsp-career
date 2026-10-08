package ru.fsp.career.assessment.domain.content

import ru.fsp.career.assessment.domain.*
import ru.fsp.career.assessment.domain.FamilyKind.*
import ru.fsp.career.assessment.domain.Grade.*
import kotlin.math.ceil
import kotlin.random.Random

/*
 * Backend families. SQL and Python families are shared with the data track. Every parametric family
 * renders a fresh table / code snippet per seed and the server recomputes the answer from the same data.
 */

private val people = listOf("anna", "boris", "vera", "gleb", "dina", "egor", "inna", "kira", "lev", "mila")
private val SQL_BD = setOf("backend", "data")

private fun sqlGroupBy(rng: Random): Generated {
    val customers = people.shuffled(rng).take(rng.range(3, 4))
    var rows: List<List<Any>>
    do {
        rows = (1..rng.range(7, 9)).map { id -> listOf(id, rng.pick(customers), 10 * rng.range(1, 40)) }
    } while (rows.map { it[1] }.toSet().size < 3)
    val totals = rows.groupBy { it[1] as String }.mapValues { (_, r) -> r.sumOf { it[2] as Int } }
    val sorted = totals.values.sorted()
    // The threshold equals one of the totals: rows with exactly that total are excluded by the strict ">".
    val threshold = sorted[rng.nextInt(sorted.size - 1)]
    val answer = totals.values.filter { it > threshold }.sum()
    val code = textTable("orders", listOf("id", "customer", "amount"), rows) +
        "\n\nSELECT customer, SUM(amount) AS total\nFROM orders\nGROUP BY customer\nHAVING SUM(amount) > $threshold;"
    return numeric("Чему равна сумма значений total по всем строкам результата запроса?", answer, code, "sql")
}

private fun sqlJoin(rng: Random): Generated {
    val names = people.shuffled(rng).take(rng.range(4, 5))
    val users = names.mapIndexed { i, n -> listOf(i + 1, n) }
    var orders: List<List<Any>>
    var counts: Map<Int, Int>
    do {
        orders = (1..rng.range(5, 7)).map { id -> listOf(100 + id, rng.range(1, users.size), 100 * rng.range(1, 30)) }
        counts = orders.groupingBy { it[1] as Int }.eachCount()
    } while (counts.size == users.size || counts.values.none { it >= 2 })
    val countStar = rng.nextBoolean()
    val answer = if (countStar) users.sumOf { maxOf(1, counts[it[0] as Int] ?: 0) } else orders.size
    val select = if (countStar) "COUNT(*)" else "COUNT(o.id)"
    val code = textTable("users", listOf("id", "name"), users) + "\n\n" +
        textTable("orders", listOf("id", "user_id", "amount"), orders) +
        "\n\nSELECT $select\nFROM users u\nLEFT JOIN orders o ON o.user_id = u.id;"
    return numeric("Какое число вернёт запрос?", answer, code, "sql")
}

private fun sqlNull(rng: Random): Generated {
    val rows = (1..rng.range(8, 10)).map { id -> listOf(id, people[id - 1], null as Int?) }.toMutableList()
    val nulls = rows.indices.shuffled(rng).take(rng.range(2, 3)).toSet()
    val filled = rows.mapIndexed { i, r -> if (i in nulls) r else listOf(r[0], r[1], 500 * rng.range(2, 12)) }
    val bonuses = filled.mapNotNull { it[2] as Int? }
    val (query, answer, tolerance) = when (rng.nextInt(3)) {
        0 -> {
            val x = rng.pick(bonuses)
            Triple("SELECT COUNT(*) FROM employees WHERE bonus <> $x;", bonuses.count { it != x }.toDouble(), 0.0)
        }
        1 -> Triple("SELECT AVG(bonus) FROM employees;", bonuses.average(), 1.0)
        else -> {
            val x = rng.pick(bonuses)
            val y = rng.pick(bonuses)
            Triple("SELECT COUNT(*) FROM employees WHERE bonus NOT IN ($x, $y);", bonuses.count { it != x && it != y }.toDouble(), 0.0)
        }
    }
    val code = textTable("employees", listOf("id", "name", "bonus"), filled) + "\n\n" + query
    val prompt = if (tolerance > 0) "Какое число вернёт запрос? Дробный результат округлите до целого." else "Какое число вернёт запрос?"
    return numeric(prompt, answer, tolerance, code, "sql")
}

private fun sqlIndex(rng: Random): Generated {
    val (table, columns) = rng.pick(listOf(
        "orders" to listOf("customer_id", "status", "created_at"),
        "events" to listOf("user_id", "event_type", "created_at"),
        "payments" to listOf("merchant_id", "currency", "paid_at"),
        "tickets" to listOf("project_id", "priority", "updated_at"),
        "shipments" to listOf("warehouse_id", "carrier", "shipped_at"),
        "messages" to listOf("chat_id", "sender_id", "sent_at"),
        "sessions" to listOf("user_id", "device", "started_at"),
        "invoices" to listOf("company_id", "state", "due_date"),
    ))
    val (c1, c2, c3) = columns.shuffled(rng)
    val correct = rng.pick(listOf(
        "WHERE $c1 = $1 AND $c2 = $2", "WHERE $c1 = $1 AND $c2 > $2", "WHERE $c1 = $1 AND $c2 = $2 AND $c3 < $3", "WHERE $c1 IN ($1, $2)",
    ))
    return choice(
        rng,
        "В PostgreSQL создан B-tree индекс (см. код). Для какого условия запроса SELECT * FROM $table … индекс позволяет сузить поиск по ведущим столбцам (правило левого префикса), не просматривая весь индекс или таблицу?",
        correct,
        listOf("WHERE $c2 = $1 AND $c3 = $2", "WHERE $c3 = $1", "WHERE $c2 = $1", "WHERE $c1::text = $1", "WHERE $c2 > $1 ORDER BY $c1",
            "WHERE $c3 > $1 AND $c2 = $2", "WHERE $c1 + 0 = $1"),
        "CREATE INDEX ${table}_idx ON $table ($c1, $c2, $c3);", "sql",
    )
}

private fun isolation(rng: Random): Generated {
    val account = rng.range(1000, 9999)
    val counter = rng.range(10, 500)
    return variant(rng, listOf(
        Variant("В PostgreSQL транзакция T1 дважды читает баланс счёта №$account. Между чтениями транзакция T2 изменила этот баланс и зафиксировалась. Какой минимальный уровень изоляции гарантирует, что T1 оба раза увидит одно и то же значение?",
            "REPEATABLE READ", listOf("READ COMMITTED", "READ UNCOMMITTED", "SERIALIZABLE")),
        Variant("Правило: на дежурстве должен оставаться хотя бы один врач. Две транзакции одновременно проверяют, что дежурят двое, и каждая снимает с дежурства «своего» врача. Какой уровень изоляции PostgreSQL сам по себе не даст нарушить правило?",
            "SERIALIZABLE", listOf("REPEATABLE READ", "READ COMMITTED", "Любой уровень — достаточно обернуть код в транзакцию")),
        Variant("Что произойдёт в PostgreSQL после SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED?",
            "Транзакция будет работать как READ COMMITTED — грязное чтение в PostgreSQL невозможно",
            listOf("Транзакция начнёт видеть незафиксированные изменения других транзакций", "Команда завершится ошибкой: уровень не поддерживается",
                "Отключится MVCC, и чтение начнёт блокировать запись")),
        Variant("Две транзакции в READ COMMITTED читают counter = $counter, затем каждая записывает counter = ${counter + 1}. Как надёжно избежать потерянного обновления, не меняя уровень изоляции?",
            "Атомарный UPDATE … SET counter = counter + 1 или SELECT … FOR UPDATE перед изменением",
            listOf("Добавить индекс по столбцу counter", "Прочитать значение дважды и сравнить", "Увеличить размер пула соединений")),
        Variant("Транзакция на уровне REPEATABLE READ получила ошибку «could not serialize access due to concurrent update». Что должно сделать приложение?",
            "Откатить транзакцию и повторить её целиком",
            listOf("Повторить только последний UPDATE внутри той же транзакции", "Понизить уровень изоляции до READ UNCOMMITTED",
                "Проигнорировать ошибку — данные уже записаны")),
        Variant("Возможны ли фантомные чтения на уровне REPEATABLE READ в PostgreSQL?",
            "Нет: транзакция читает снимок данных, сделанный при первом запросе",
            listOf("Да, стандарт SQL это допускает, и PostgreSQL их показывает", "Только для таблиц без индексов", "Только в запросах с агрегатными функциями")),
    ))
}

private fun idempotency(rng: Random): Generated {
    val amount = 100 * rng.range(5, 500)
    return variant(rng, listOf(
        Variant("Мобильное приложение отправило POST /payments на $amount ₽, получило таймаут и повторило запрос. Как гарантировать, что деньги спишутся один раз?",
            "Клиент передаёт ключ идемпотентности (Idempotency-Key), сервер сохраняет результат по ключу и при повторе возвращает его",
            listOf("Увеличить таймаут клиента до минуты", "Сервер всегда отвечает 409 на второй запрос с той же суммой", "Запретить приложению повторять запросы")),
        Variant("Консьюмер Kafka записал заказ в БД и упал до коммита offset. Что произойдёт после рестарта и как к этому подготовиться?",
            "Сообщение придёт повторно; обработка должна быть идемпотентной (дедупликация по идентификатору сообщения)",
            listOf("Сообщение будет потеряно; нужно включить acks=all", "Kafka сама откатит запись в БД", "Ничего: offset коммитится автоматически до обработки")),
        Variant("Как гарантировать, что событие «заказ создан» попадёт в брокер тогда и только тогда, когда заказ сохранён в БД?",
            "Transactional outbox: запись события в таблицу outbox в той же транзакции и отдельный процесс публикации",
            listOf("Сначала отправить событие, потом сохранить заказ", "Отправлять событие в брокер внутри транзакции БД перед COMMIT",
                "Публиковать событие из триггера на клиенте")),
        Variant("Сервис вызывает нестабильный внешний API. Какая стратегия повторов корректна?",
            "Ограниченное число попыток с экспоненциальной задержкой и джиттером — только для идемпотентных операций",
            listOf("Повторять немедленно до успеха", "Повторять с фиксированным интервалом 10 мс без ограничения", "Повторять любые запросы, включая неидемпотентный POST, без ключа")),
        Variant("Запрос PUT /users/${rng.range(10, 999)} с полным представлением пользователя выполнился дважды из-за повтора. Каким будет результат?",
            "Таким же, как после одного вызова: PUT идемпотентен",
            listOf("Будут созданы два пользователя", "Второй запрос обязательно вернёт 409 Conflict", "Результат не определён стандартом HTTP")),
    ))
}

private fun kotlinCollections(rng: Random): Generated {
    val xs = List(rng.range(6, 8)) { rng.range(1, 30) }
    val list = "listOf(${xs.joinToString(", ")})"
    return when (rng.nextInt(3)) {
        0 -> {
            val m = rng.range(2, 4); val r = rng.range(0, m - 1); val k = rng.range(2, 5)
            numeric("Что выведет программа?", xs.filter { it % m == r }.map { it * k }.sum(),
                "val xs = $list\nval result = xs.filter { it % $m == $r }.map { it * $k }.sum()\nprintln(result)", "kotlin")
        }
        1 -> {
            val n = rng.range(2, 4)
            numeric("Что выведет программа?", xs.sortedDescending().take(n).sum(),
                "val xs = $list\nprintln(xs.sortedDescending().take($n).sum())", "kotlin")
        }
        else -> {
            val t = rng.range(8, 20); val init = rng.range(0, 10)
            numeric("Что выведет программа?", xs.fold(init) { acc, x -> if (x > t) acc + x else acc - 1 },
                "val xs = $list\nval r = xs.fold($init) { acc, x -> if (x > $t) acc + x else acc - 1 }\nprintln(r)", "kotlin")
        }
    }
}

private fun kotlinNullSafety(rng: Random): Generated {
    val names = people.shuffled(rng).take(rng.range(4, 5)).map<String, String?> { it } + List(rng.range(1, 2)) { null }
    val shuffled = names.shuffled(rng)
    val literal = shuffled.joinToString(", ") { if (it == null) "null" else "\"$it\"" }
    return when (rng.nextInt(3)) {
        0 -> {
            val d = rng.range(2, 9)
            numeric("Что выведет программа?", shuffled.sumOf { it?.length ?: d },
                "val names: List<String?> = listOf($literal)\nprintln(names.sumOf { it?.length ?: $d })", "kotlin")
        }
        1 -> {
            val t = rng.range(3, 4)
            numeric("Что выведет программа?", shuffled.mapNotNull { it?.takeIf { s -> s.length > t }?.length }.sum(),
                "val names: List<String?> = listOf($literal)\nval r = names.mapNotNull { it?.takeIf { s -> s.length > $t }?.length }.sum()\nprintln(r)", "kotlin")
        }
        else -> {
            val present = shuffled.filterNotNull().take(3)
            val values = present.associateWith { rng.range(1, 20) }
            val keys = (present + people.filter { it !in present }.shuffled(rng).take(2)).shuffled(rng)
            val d = rng.range(1, 9)
            val map = values.entries.joinToString(", ") { "\"${it.key}\" to ${it.value}" }
            numeric("Что выведет программа?", keys.sumOf { values[it] ?: d },
                "val stock = mapOf($map)\nval keys = listOf(${keys.joinToString(", ") { "\"$it\"" }})\nprintln(keys.sumOf { stock[it] ?: $d })", "kotlin")
        }
    }
}

private fun threadPool(rng: Random): Generated {
    val p = rng.range(2, 8); val n = rng.range(p + 1, 40); val t = 50 * rng.range(1, 8)
    return numeric(
        "Пул из $p потоков обрабатывает $n независимых задач. Каждая задача занимает ровно $t мс процессорного времени и никогда не блокируется; ядер не меньше, чем потоков, накладные расходы не учитывайте. За какое минимальное время (мс) будут выполнены все задачи?",
        ceil(n.toDouble() / p).toInt() * t, unit = "мс")
}

private fun amdahl(rng: Random): Generated {
    val serial = rng.range(3, 30)
    val s = serial / 100.0
    return if (rng.nextInt(4) == 0) {
        numeric("Последовательная (не распараллеливаемая) часть программы занимает $serial % времени выполнения на одном ядре. Каково максимально возможное ускорение при неограниченном числе ядер? Ответ — во сколько раз, с точностью до десятых.",
            1.0 / s, 0.06)
    } else {
        val cores = rng.pick(listOf(2, 3, 4, 6, 8, 12, 16, 32))
        numeric("Последовательная (не распараллеливаемая) часть программы занимает $serial % времени выполнения на одном ядре, остальное идеально распараллеливается. Каким будет ускорение на $cores ядрах по закону Амдала? Ответ — во сколько раз, с точностью до десятых.",
            1.0 / (s + (1 - s) / cores), 0.06)
    }
}

private fun deadlocks(rng: Random) = variant(rng, listOf(
    Variant("Перевод денег: транзакция A блокирует счёт ${rng.range(1, 50)}, затем ${rng.range(51, 99)}; транзакция B — в обратном порядке. Периодически PostgreSQL прерывает одну из них с ошибкой deadlock detected. Как устранить причину?",
        "Во всех транзакциях блокировать строки в одном порядке, например по возрастанию id",
        listOf("Увеличить deadlock_timeout", "Перейти на уровень READ UNCOMMITTED", "Добавить индекс по столбцу balance")),
    Variant("В обработчике Ktor на Dispatchers.Default выполняется блокирующий JDBC-запрос на несколько секунд. Чем это опасно и как исправить?",
        "Блокируются потоки ограниченного пула для CPU-задач; блокирующий вызов надо перенести в withContext(Dispatchers.IO)",
        listOf("Ничем: корутины никогда не блокируют потоки", "Нужно сделать обработчик обычной (не suspend) функцией", "Нужно обернуть запрос в runBlocking")),
    Variant("Поток A захватил lock1 и ждёт lock2, поток B захватил lock2 и ждёт lock1. Как называется ситуация и какое условие Коффмана проще всего нарушить?",
        "Взаимная блокировка (deadlock); нарушить круговое ожидание, введя единый порядок захвата блокировок",
        listOf("Livelock; добавить случайные задержки перед захватом", "Голодание; повысить приоритет одного из потоков", "Гонка данных; объявить поля volatile")),
    Variant("Счётчик var hits = 0 увеличивают ${rng.range(2, 8)} потоков без синхронизации (hits++). Что верно?",
        "Итог может оказаться меньше ожидаемого: hits++ — это чтение-изменение-запись; нужен AtomicInteger или блокировка",
        listOf("Итог всегда точен: операции с Int атомарны", "Достаточно объявить hits как @Volatile", "Итог может оказаться больше ожидаемого из-за повторов")),
    Variant("Почему вызов runBlocking внутри корутины, работающей на однопоточном диспетчере, может привести к зависанию?",
        "Он блокирует единственный поток, а вложенные корутины ждут освобождения этого же потока",
        listOf("runBlocking отменяет родительскую корутину", "runBlocking всегда создаёт новый поток, поэтому зависание невозможно", "Из-за переполнения стека вызовов")),
))

private fun lruMisses(rng: Random): Generated {
    val capacity = rng.range(2, 3)
    val keys = listOf("A", "B", "C", "D", "E").take(capacity + 2)
    var sequence: List<String>
    var misses: Int
    do {
        sequence = List(10) { rng.pick(keys) }
        val cache = LinkedHashSet<String>()
        misses = 0
        for (k in sequence) {
            if (k in cache) cache.remove(k) else {
                misses++
                if (cache.size == capacity) cache.remove(cache.first())
            }
            cache.add(k)
        }
    } while (misses !in 4..9)
    return numeric(
        "Кэш с вытеснением LRU (least recently used) вмещает $capacity элемента и изначально пуст. Сколько промахов (cache miss) произойдёт при обращениях к ключам в таком порядке: ${sequence.joinToString(" ")}?",
        misses)
}

private fun quorum(rng: Random): Generated {
    val n = rng.pick(listOf(3, 4, 5, 6, 7, 9))
    val intro = "Хранилище с репликацией (как Cassandra или Dynamo): каждая запись хранится на N = $n узлах."
    return when (rng.nextInt(4)) {
        // R = W = Q and R + W > N  ⇒  Q = ⌊N/2⌋ + 1 (a majority)
        3 -> numeric("$intro Чтения и записи используют одинаковый кворум Q узлов. Каково минимальное Q, при котором любое чтение гарантированно видит последнюю подтверждённую запись?", n / 2 + 1)
        0 -> {
            val w = rng.range(1, n)
            numeric("$intro Запись считается успешной после подтверждения W = $w узлами. Каким должно быть минимальное число узлов R, опрашиваемых при чтении, чтобы чтение гарантированно видело последнюю подтверждённую запись?", n - w + 1)
        }
        1 -> {
            val r = rng.range(1, n)
            numeric("$intro Чтение опрашивает R = $r узлов. Каким должно быть минимальное число подтверждений записи W, чтобы любое чтение гарантированно видело последнюю подтверждённую запись?", n - r + 1)
        }
        else -> {
            val w = rng.range(1, n - 1)
            numeric("$intro Запись требует подтверждения W = $w узлами. Сколько узлов может одновременно отказать, чтобы запись всё ещё могла выполниться?", n - w)
        }
    }
}

private fun tokenBucket(rng: Random): Generated {
    val capacity = rng.range(5, 15); val refill = rng.range(1, 4); val perSecond = rng.range(refill + 1, refill + 6); val seconds = rng.range(3, 6)
    var tokens = capacity
    var allowed = 0
    for (t in 0 until seconds) {
        if (t > 0) tokens = minOf(capacity, tokens + refill)
        val passed = minOf(tokens, perSecond)
        allowed += passed
        tokens -= passed
    }
    return numeric(
        "Rate limiter «token bucket»: ёмкость ведра $capacity токенов, в начале оно полное. В моменты t = 1, 2, 3, … с в ведро добавляется $refill ${plural(refill, "токен", "токена", "токенов")} (сверх ёмкости не накапливается). Клиент в моменты t = 0, 1, …, ${seconds - 1} с отправляет по $perSecond запросов; каждый пропущенный запрос забирает 1 токен, при пустом ведре запрос отклоняется. Сколько запросов будет пропущено всего?",
        allowed)
}

private fun pythonOutput(rng: Random): Generated {
    val xs = List(rng.range(6, 8)) { rng.range(1, 15) }
    val list = "[${xs.joinToString(", ")}]"
    return when (rng.nextInt(3)) {
        0 -> numeric("Что выведет программа?", xs.filter { it % 2 == 1 }.sumOf { it * it },
            "xs = $list\nprint(sum(x * x for x in xs if x % 2 == 1))", "python")
        1 -> {
            val start = rng.range(0, 2); val end = rng.range(start + 3, xs.size); val step = rng.range(2, 3)
            numeric("Что выведет программа?", (start until end step step).sumOf { xs[it] },
                "xs = $list\nprint(sum(xs[$start:$end:$step]))", "python")
        }
        else -> {
            val words = List(rng.range(7, 10)) { rng.pick(listOf("api", "db", "cache", "queue", "auth")) }
            val counts = words.groupingBy { it }.eachCount()
            numeric("Что выведет программа?", counts.values.max() * counts.size,
                "words = \"${words.joinToString(" ")}\".split()\ncounts = {}\nfor w in words:\n    counts[w] = counts.get(w, 0) + 1\nprint(max(counts.values()) * len(counts))", "python")
        }
    }
}

val backendFamilies: List<ItemFamily> = listOf(
    family("be.sql.groupby", "SQL: GROUP BY и HAVING", "sql", SQL_BD, MIDDLE, 1.5, -0.3, PARAMETRIC, 0, ::sqlGroupBy),
    family("be.sql.join", "SQL: LEFT JOIN и COUNT", "sql", SQL_BD, MIDDLE, 1.5, 0.1, PARAMETRIC, 0, ::sqlJoin),
    family("be.sql.null", "SQL: семантика NULL", "sql", SQL_BD, SENIOR, 1.4, 0.9, PARAMETRIC, 0, ::sqlNull),
    family("be.sql.index", "Составные индексы", "databases", setOf("backend"), MIDDLE, 1.2, 0.4, PARAMETRIC, 4, ::sqlIndex),
    family("be.tx.isolation", "Транзакции и уровни изоляции", "databases", setOf("backend"), SENIOR, 1.2, 1.2, VARIANTS, 4, ::isolation),
    family("be.idempotency", "Идемпотентность и надёжная доставка", "distributed", setOf("backend"), MIDDLE, 1.1, 0.0, VARIANTS, 4, ::idempotency),
    family("be.kotlin.collections", "Kotlin: коллекции", "kotlin", setOf("backend"), JUNIOR, 1.5, -0.8, PARAMETRIC, 0, ::kotlinCollections),
    family("be.kotlin.nullsafety", "Kotlin: null-безопасность", "kotlin", setOf("backend"), MIDDLE, 1.3, 0.3, PARAMETRIC, 0, ::kotlinNullSafety),
    family("be.concurrency.pool", "Пулы потоков", "concurrency", setOf("backend"), JUNIOR, 1.3, -0.6, PARAMETRIC, 0, ::threadPool),
    family("be.concurrency.amdahl", "Закон Амдала", "concurrency", setOf("backend"), MIDDLE, 1.3, 0.6, PARAMETRIC, 0, ::amdahl),
    family("be.concurrency.deadlock", "Блокировки и корутины", "concurrency", setOf("backend"), SENIOR, 1.2, 1.1, VARIANTS, 4, ::deadlocks),
    family("be.cache.lru", "Кэш LRU", "distributed", setOf("backend"), SENIOR, 1.5, 0.8, PARAMETRIC, 0, ::lruMisses),
    family("be.distributed.quorum", "Кворумы репликации", "distributed", setOf("backend"), SENIOR, 1.3, 1.5, PARAMETRIC, 0, ::quorum),
    family("be.ratelimit", "Rate limiting: token bucket", "distributed", setOf("backend"), MIDDLE, 1.4, 0.6, PARAMETRIC, 0, ::tokenBucket),
    family("be.python.output", "Python: выражения и коллекции", "python", SQL_BD, JUNIOR, 1.5, -0.7, PARAMETRIC, 0, ::pythonOutput),
)
