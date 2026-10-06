package ru.fsp.career.assessment.content

import ru.fsp.career.assessment.FamilyKind.PARAMETRIC
import ru.fsp.career.assessment.FamilyKind.VARIANTS
import ru.fsp.career.assessment.Grade.JUNIOR
import ru.fsp.career.assessment.Grade.MIDDLE
import ru.fsp.career.assessment.Grade.SENIOR
import ru.fsp.career.assessment.Generated
import ru.fsp.career.assessment.ItemFamily
import ru.fsp.career.assessment.TECH
import ru.fsp.career.assessment.Variant
import ru.fsp.career.assessment.choice
import ru.fsp.career.assessment.family
import ru.fsp.career.assessment.numeric
import ru.fsp.career.assessment.pick
import ru.fsp.career.assessment.range
import ru.fsp.career.assessment.variant
import kotlin.random.Random

/*
 * Shared computer-science families for the engineering specializations (backend, frontend, data).
 * Parameters a/b are expert-assigned priors (see docs/validation/assessment-results.md, "Калибровка").
 */

private val complexities = listOf("O(1)", "O(log n)", "O(n)", "O(n log n)", "O(n²)", "O(n³)")
private val functionNames = listOf("process", "handle", "compute", "evaluate", "run", "solve", "walk", "scan", "analyze", "measure", "calc", "traverse")
private val arrayNames = listOf("items", "values", "data", "nums", "xs", "arr")
private val accumulators = listOf("acc", "total", "work", "steps", "pairs", "inversions")

/** Identifiers are renamed per instance (surface variation); the algorithmic shape — the measured skill — is not. */
private fun complexityItem(rng: Random, code: String, correct: String): Generated {
    var renamed = code.replace(Regex("\\bitems\\b"), rng.pick(arrayNames))
    accumulators.firstOrNull { Regex("\\b$it\\b").containsMatchIn(renamed) }?.let { name ->
        renamed = renamed.replace(Regex("\\b$name\\b"), rng.pick(listOf("acc", "total", "work", "res", "sum", "cnt")))
    }
    return choice(
        rng,
        "Оцените временную сложность функции в худшем случае (n — размер входного массива или значение параметра n).",
        correct, complexities, renamed, "kotlin",
    )
}

private fun basicComplexity(rng: Random) = run {
    val name = rng.pick(functionNames)
    when (rng.nextInt(4)) {
        0 -> complexityItem(rng, """
            fun $name(items: IntArray): Long {
                var acc = 0L
                for (i in items.indices step ${rng.range(2, 5)}) {
                    acc += items[i]
                }
                return acc
            }""".trimIndent(), "O(n)")
        1 -> complexityItem(rng, """
            fun $name(n: Int): Int {
                var steps = 0
                var i = 1
                while (i < n) {
                    i *= ${rng.range(2, 4)}
                    steps++
                }
                return steps
            }""".trimIndent(), "O(log n)")
        2 -> complexityItem(rng, """
            fun $name(items: IntArray): Int {
                var pairs = 0
                for (i in items.indices) {
                    for (j in items.indices) {
                        if (items[i] + items[j] == ${rng.range(10, 99)}) pairs++
                    }
                }
                return pairs
            }""".trimIndent(), "O(n²)")
        else -> complexityItem(rng, """
            fun $name(items: IntArray): Int {
                var max = Int.MIN_VALUE
                for (x in items) if (x > max) max = x
                var count = 0
                for (x in items) if (x == max) count++
                return count
            }""".trimIndent(), "O(n)")
    }
}

private fun nestedComplexity(rng: Random) = run {
    val name = rng.pick(functionNames)
    when (rng.nextInt(4)) {
        0 -> complexityItem(rng, """
            fun $name(n: Int): Long {
                var total = 0L
                for (i in 0 until n) {
                    var j = 1
                    while (j < n) {
                        total += j
                        j *= 2
                    }
                }
                return total
            }""".trimIndent(), "O(n log n)")
        1 -> complexityItem(rng, """
            fun $name(n: Int): Long {
                var total = 0L
                for (i in 0 until n) {
                    for (j in i until n) {
                        for (k in 0 until ${rng.range(5, 50)}) total += k
                    }
                }
                return total
            }""".trimIndent(), "O(n²)")
        2 -> complexityItem(rng, """
            fun $name(text: String): IntArray {
                val counts = IntArray(26)
                for (ch in text) {
                    for (k in 0 until 26) {
                        if (ch == 'a' + k) counts[k]++
                    }
                }
                return counts
            }""".trimIndent(), "O(n)")
        else -> complexityItem(rng, """
            fun $name(items: IntArray): Int {
                var inversions = 0
                for (i in items.indices) {
                    for (j in 0 until i) {
                        if (items[j] > items[i]) inversions++
                    }
                }
                return inversions
            }""".trimIndent(), "O(n²)")
    }
}

private fun amortizedComplexity(rng: Random) = run {
    val name = rng.pick(functionNames)
    when (rng.nextInt(4)) {
        0 -> complexityItem(rng, """
            fun $name(n: Int): Long {
                var work = 0L
                var size = n
                while (size > 0) {
                    for (i in 0 until size) work++
                    size /= 2
                }
                return work
            }""".trimIndent(), "O(n)")
        1 -> complexityItem(rng, """
            fun $name(n: Int): Long {
                var work = 0L
                for (i in 1..n) {
                    var j = i
                    while (j <= n) {
                        work++
                        j += i
                    }
                }
                return work
            }""".trimIndent(), "O(n log n)")
        2 -> complexityItem(rng, """
            fun $name(n: Int): Long {
                var work = 0L
                var i = 1
                while (i <= n) {
                    for (j in 0 until i) work++
                    i *= 2
                }
                return work
            }""".trimIndent(), "O(n)")
        else -> complexityItem(rng, """
            // все элементы items неотрицательны
            fun $name(items: IntArray, limit: Int): Int {
                var best = 0
                var left = 0
                var sum = 0
                for (right in items.indices) {
                    sum += items[right]
                    while (sum > limit) {
                        sum -= items[left]
                        left++
                    }
                    best = maxOf(best, right - left + 1)
                }
                return best
            }""".trimIndent(), "O(n)")
    }
}

private fun bits(rng: Random) = run {
    val x = rng.range(16, 255)
    var y = rng.range(16, 255)
    while (y == x) y = rng.range(16, 255)
    val (symbol, value) = when (rng.nextInt(3)) {
        0 -> "&" to (x and y)
        1 -> "|" to (x or y)
        else -> "^" to (x xor y)
    }
    numeric("Что выведет программа?", value, "a = $x\nb = $y\nprint(a $symbol b)", "python")
}

private fun stackQueue(rng: Random) = run {
    val queue = rng.nextBoolean()
    val contents = ArrayDeque<Int>()
    val lines = mutableListOf<String>()
    if (queue) lines += listOf("from collections import deque", "q = deque()") else lines += "s = []"
    val name = if (queue) "q" else "s"
    var pops = 0
    var ops = 0
    while (ops < 7 || pops < 2 || contents.isEmpty()) {
        val push = contents.size < 2 || rng.nextInt(3) != 0
        if (push) {
            val v = rng.range(1, 99)
            contents.addLast(v)
            lines += "$name.append($v)"
        } else {
            if (queue) contents.removeFirst() else contents.removeLast()
            lines += if (queue) "q.popleft()" else "s.pop()"
            pops++
        }
        ops++
    }
    val last = if (queue) contents.first() else contents.last()
    lines += if (queue) "print(q.popleft())" else "print(s.pop())"
    numeric("Что выведет программа?", last, lines.joinToString("\n"), "python")
}

/** Tracing a two-branch recursion: either count the calls or compute the returned value (no memoization). */
private fun recursionCalls(rng: Random) = run {
    val base = rng.range(0, 2)
    val step = rng.range(2, 3)
    val k = rng.range(5, 9)
    val m1 = rng.range(1, 2)
    val m2 = rng.range(1, 3)
    fun calls(n: Int): Int = if (n <= base) 1 else 1 + calls(n - 1) + calls(n - step)
    fun value(n: Int): Long = if (n <= base) 1 else m1 * value(n - 1) + m2 * value(n - step)
    fun term(m: Int, call: String) = if (m == 1) call else "$m * $call"
    val body = term(m1, "f(n - 1)") + " + " + term(m2, "f(n - $step)")
    val (code, language) = when (rng.nextInt(3)) {
        0 -> "fun f(n: Int): Long {\n    if (n <= $base) return 1\n    return $body\n}\n\nprintln(f($k))" to "kotlin"
        1 -> "def f(n):\n    if n <= $base:\n        return 1\n    return $body\n\nprint(f($k))" to "python"
        else -> "function f(n) {\n  if (n <= $base) return 1;\n  return $body;\n}\n\nconsole.log(f($k));" to "javascript"
    }
    if (rng.nextBoolean()) numeric("Сколько всего раз будет вызвана функция f при вычислении f($k), включая самый первый вызов?", calls(k), code, language)
    else numeric("Что выведет программа?", value(k).toDouble(), 0.0, code, language)
}

/** HTTP freshness: Age is subtracted from the lifetime; s-maxage applies only to shared caches (CDN, proxies). */
private fun httpCache(rng: Random): Generated {
    var maxAge: Int; var sMaxAge: Int
    do { maxAge = 60 * rng.range(2, 60); sMaxAge = 60 * rng.range(2, 120) } while (maxAge == sMaxAge)
    val age = 5 * rng.range(2, (minOf(maxAge, sMaxAge) - 10) / 5)
    val headers = "HTTP/1.1 200 OK\nCache-Control: public, max-age=$maxAge, s-maxage=$sMaxAge\nAge: $age\nETag: \"${Integer.toHexString(rng.nextInt(1 shl 24))}\""
    return if (rng.nextBoolean()) numeric(
        "Ответ пришёл в браузер через CDN с такими заголовками. Сколько секунд браузер может использовать его из своего кэша без повторной проверки на сервере?",
        maxAge - age, headers, "http", "с")
    else numeric(
        "CDN (общий кэш) получил от источника ответ с такими заголовками. Сколько секунд CDN ещё может отдавать его без повторной проверки?",
        sMaxAge - age, headers, "http", "с")
}

/** Entropy of a uniformly random password: L · log2(alphabet size). */
private fun passwordEntropy(rng: Random): Generated {
    val (alphabet, size) = rng.pick(listOf(
        "только цифры" to 10, "шестнадцатеричные символы (0–9, a–f)" to 16, "строчные латинские буквы" to 26,
        "строчные и прописные латинские буквы" to 52, "латинские буквы обоих регистров и цифры" to 62, "все 94 печатных символа ASCII" to 94,
    ))
    val bitsPerChar = kotlin.math.ln(size.toDouble()) / kotlin.math.ln(2.0)
    return if (rng.nextBoolean()) {
        val length = rng.range(6, 32)
        numeric("Пароль из $length символов генерируется случайно: каждый символ независимо и равновероятно выбирается из алфавита «$alphabet». Какова его энтропия в битах (с точностью до десятых)?",
            length * bitsPerChar, 0.06, unit = "бит")
    } else {
        val bits = rng.pick(listOf(48, 64, 72, 80, 96, 112, 128))
        numeric("Токен генерируется случайно: каждый символ независимо и равновероятно выбирается из алфавита «$alphabet». Какое минимальное число символов даёт энтропию не меньше $bits бит?",
            kotlin.math.ceil(bits / bitsPerChar - 1e-9))
    }
}

private fun httpStatus(rng: Random): Generated {
    val resource = rng.pick(listOf("/api/orders", "/api/invoices", "/api/projects", "/api/tickets", "/api/payments"))
    val id = rng.range(100, 9999)
    val suffix = " Какой код ответа точнее всего соответствует семантике HTTP (RFC 9110)?"
    return variant(rng, listOf(
        Variant("Клиент вызывает GET $resource/$id без заголовка Authorization, хотя эндпоинт доступен только после входа.$suffix",
            "401 Unauthorized", listOf("403 Forbidden", "400 Bad Request", "404 Not Found", "407 Proxy Authentication Required")),
        Variant("Пользователь вошёл в систему с ролью «читатель» и пытается выполнить PATCH $resource/$id — ресурс он видит, но изменять его роли запрещено.$suffix",
            "403 Forbidden", listOf("401 Unauthorized", "405 Method Not Allowed", "409 Conflict", "400 Bad Request")),
        Variant("Клиент запрашивает GET $resource/$id, но записи с таким идентификатором никогда не существовало.$suffix",
            "404 Not Found", listOf("400 Bad Request", "410 Gone", "204 No Content", "500 Internal Server Error")),
        Variant("POST $resource успешно создал новую запись; в ответе — её представление и заголовок Location: $resource/$id.$suffix",
            "201 Created", listOf("200 OK", "202 Accepted", "204 No Content", "302 Found")),
        Variant("Клиент превысил лимит — ${rng.range(60, 600)} запросов в минуту на ключ API.$suffix",
            "429 Too Many Requests", listOf("503 Service Unavailable", "403 Forbidden", "400 Bad Request", "408 Request Timeout")),
        Variant("API-шлюз проксирует запрос $resource/$id во внутренний сервис и не дождался его ответа за ${rng.range(5, 30)} с.$suffix",
            "504 Gateway Timeout", listOf("502 Bad Gateway", "503 Service Unavailable", "408 Request Timeout", "500 Internal Server Error")),
        Variant("DELETE $resource/$id выполнен успешно, тело ответа не требуется.$suffix",
            "204 No Content", listOf("202 Accepted", "205 Reset Content", "201 Created", "304 Not Modified")),
        Variant("Тело запроса POST $resource — синтаксически некорректный JSON: не хватает закрывающей скобки.$suffix",
            "400 Bad Request", listOf("422 Unprocessable Content", "415 Unsupported Media Type", "500 Internal Server Error", "406 Not Acceptable")),
    ))
}

private fun httpSemantics(rng: Random) = variant(rng, listOf(
    Variant("Какой из методов НЕ является идемпотентным по RFC 9110?",
        "POST", listOf("PUT", "DELETE", "GET", "HEAD", "OPTIONS")),
    Variant("Сервер ответил 304 Not Modified. Что это означает?",
        "Клиент прислал условный запрос (If-None-Match / If-Modified-Since), и его сохранённая копия всё ещё актуальна",
        listOf("Ресурс перемещён, новый адрес — в заголовке Location", "Ответ отдан прокси из кэша без обращения к серверу",
            "Сервер отказался менять ресурс, потому что запрос некорректен")),
    Variant("Что означает заголовок ответа Cache-Control: no-cache?",
        "Ответ можно сохранить, но перед каждым использованием его нужно перепроверить у сервера",
        listOf("Ответ запрещено сохранять в любом кэше", "Ответ можно кэшировать только в браузере, но не в CDN",
            "Нужно сбросить кэш всех ресурсов этого домена")),
    Variant("Что означает заголовок ответа Cache-Control: no-store?",
        "Ответ нельзя сохранять ни в одном кэше",
        listOf("Ответ можно сохранить, но перед использованием нужно перепроверять его у сервера",
            "Ответ хранится только в оперативной памяти, но не на диске", "Ответ можно кэшировать до конца сессии")),
    Variant("Клиент отправил PUT с заголовком If-Match, но ETag ресурса на сервере уже изменился. Какой код ответа корректен?",
        "412 Precondition Failed", listOf("409 Conflict", "304 Not Modified", "428 Precondition Required", "400 Bad Request")),
    Variant("Чем 502 Bad Gateway отличается от 504 Gateway Timeout?",
        "502 — шлюз получил некорректный ответ от вышестоящего сервера, 504 — не дождался ответа вовсе",
        listOf("502 — ошибка клиента, 504 — ошибка сервера", "Это синонимы, различаются только версией HTTP",
            "502 — сервер перегружен, 504 — сервер выключен")),
    Variant("Браузер отправил preflight-запрос OPTIONS перед кросс-доменным PUT с JSON. Что должен вернуть сервер, чтобы основной запрос был разрешён?",
        "Access-Control-Allow-Origin с этим источником и Access-Control-Allow-Methods, включающий PUT (а также разрешённые заголовки)",
        listOf("Заголовок Content-Security-Policy с этим доменом", "Код 401 и заголовок WWW-Authenticate",
            "Set-Cookie с атрибутом SameSite=None")),
))

private fun security(rng: Random) = variant(rng, listOf(
    Variant("Поиск по товарам строит SQL из строки, введённой пользователем. Как правильно передать ввод в запрос?",
        "Параметризованным запросом (prepared statement с плейсхолдерами)",
        listOf("Экранировать кавычки вручную перед конкатенацией", "Закодировать строку в Base64",
            "Ограничить длину строки 100 символами", "Удалять из строки слово SELECT")),
    Variant("Как хранить пароли пользователей в базе данных?",
        "Медленной адаптивной хеш-функцией с солью: bcrypt, scrypt или Argon2",
        listOf("SHA-256 без соли — он криптостойкий", "Шифровать AES, ключ хранить в конфиге приложения",
            "MD5 с общей для всех солью", "В Base64, чтобы не хранить открытым текстом")),
    Variant("Комментарии пользователей выводятся на страницу. Что защищает от хранимой XSS?",
        "Контекстное экранирование при выводе (плюс Content-Security-Policy как второй рубеж)",
        listOf("Переход на HTTPS", "Ограничение длины комментария", "Хранение комментариев в Base64", "Запрет комментариев длиннее одной строки")),
    Variant("Сессия хранится в cookie. Что защищает от CSRF при отправке форм?",
        "Атрибут SameSite у cookie и/или CSRF-токен, проверяемый сервером",
        listOf("Заголовок Access-Control-Allow-Origin: *", "Длинный идентификатор сессии", "Хранение сессии в localStorage вместо cookie",
            "Использование метода PUT вместо POST")),
    Variant("Сервис принимает JWT access-токены. Что он обязан проверить?",
        "Подпись по доверенному ключу, срок действия (exp), издателя (iss) и аудиторию (aud)",
        listOf("Только что токен декодируется из Base64", "Только поле exp", "Только наличие поля sub",
            "Что токен короче 4 КБ")),
    Variant("Секретный ключ платёжного API случайно попал в публичный репозиторий. Что сделать в первую очередь?",
        "Отозвать (ротировать) ключ у провайдера и выпустить новый",
        listOf("Удалить коммит через force-push", "Сделать репозиторий приватным", "Добавить файл с ключом в .gitignore",
            "Подождать — вероятность, что ключ заметят, мала")),
))

val generalFamilies: List<ItemFamily> = listOf(
    family("gen.complexity.basic", "Сложность простых циклов", "algorithms", TECH, JUNIOR, 1.3, -1.1, PARAMETRIC, 4, ::basicComplexity),
    family("gen.complexity.nested", "Сложность вложенных циклов", "algorithms", TECH, MIDDLE, 1.4, 0.2, PARAMETRIC, 4, ::nestedComplexity),
    family("gen.complexity.amortized", "Амортизированная сложность", "algorithms", TECH, SENIOR, 1.4, 1.3, PARAMETRIC, 4, ::amortizedComplexity),
    family("gen.bits", "Побитовые операции", "algorithms", TECH, JUNIOR, 1.2, -0.7, PARAMETRIC, 0, ::bits),
    family("gen.stack-queue", "Стек и очередь", "algorithms", TECH, JUNIOR, 1.4, -0.9, PARAMETRIC, 0, ::stackQueue),
    family("gen.recursion.calls", "Трассировка рекурсии", "algorithms", TECH, SENIOR, 1.3, 1.4, PARAMETRIC, 0, ::recursionCalls),
    family("gen.http.status", "Коды ответа HTTP", "http", TECH, JUNIOR, 1.1, -1.2, VARIANTS, 4, ::httpStatus),
    family("gen.http.semantics", "Семантика HTTP и кэширование", "http", TECH, MIDDLE, 1.2, 0.3, VARIANTS, 4, ::httpSemantics),
    family("gen.security", "Безопасность веб-приложений", "security", TECH, MIDDLE, 1.0, -0.2, VARIANTS, 4, ::security),
    family("gen.http.cache", "HTTP-кэширование: max-age, s-maxage, Age", "http", TECH, MIDDLE, 1.4, 0.5, PARAMETRIC, 0, ::httpCache),
    family("gen.security.entropy", "Энтропия паролей и токенов", "security", TECH, MIDDLE, 1.3, 0.4, PARAMETRIC, 0, ::passwordEntropy),
)
