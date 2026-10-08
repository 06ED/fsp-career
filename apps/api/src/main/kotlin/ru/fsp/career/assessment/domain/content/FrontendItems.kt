package ru.fsp.career.assessment.domain.content

import ru.fsp.career.assessment.domain.*
import ru.fsp.career.assessment.domain.FamilyKind.*
import ru.fsp.career.assessment.domain.Grade.*
import kotlin.random.Random

/* Frontend families; layout/accessibility families are shared with the design track. */

private val FE = setOf("frontend")
private val FE_DS = setOf("frontend", "design")

private enum class Task { SYNC, TIMEOUT, PROMISE, MICROTASK }

/**
 * Event loop ordering: synchronous logs in source order, then the microtask queue (promise
 * callbacks and queueMicrotask share it, FIFO), then zero-delay timers in registration order.
 */
private fun eventLoop(rng: Random): Generated {
    var tasks: List<Task>
    do {
        tasks = List(5) { Task.entries[rng.nextInt(4)] }
    } while (Task.SYNC !in tasks || Task.TIMEOUT !in tasks || tasks.none { it == Task.PROMISE || it == Task.MICROTASK })
    val labels = listOf("A", "B", "C", "D", "E", "F", "G", "H").shuffled(rng).take(5)
    val code = tasks.mapIndexed { i, t ->
        val log = "console.log(\"${labels[i]}\")"
        when (t) {
            Task.SYNC -> "$log;"
            Task.TIMEOUT -> "setTimeout(() => $log, 0);"
            Task.PROMISE -> "Promise.resolve().then(() => $log);"
            Task.MICROTASK -> "queueMicrotask(() => $log);"
        }
    }.joinToString("\n")
    fun order(vararg groups: List<Task>) = groups.flatMap { g -> tasks.indices.filter { tasks[it] in g }.map { labels[it] } }.joinToString(" ")
    val sync = listOf(Task.SYNC); val micro = listOf(Task.PROMISE, Task.MICROTASK); val timers = listOf(Task.TIMEOUT)
    val correct = order(sync, micro, timers)
    val candidates = mutableListOf(
        labels.joinToString(" "),
        order(sync, timers, micro),
        order(micro, sync, timers),
        order(sync, listOf(Task.MICROTASK), listOf(Task.PROMISE), timers),
        order(sync, listOf(Task.PROMISE), timers, listOf(Task.MICROTASK)),
    )
    while (candidates.toSet().minus(correct).size < 3) candidates += labels.shuffled(rng).joinToString(" ")
    return choice(rng, "В каком порядке буквы появятся в консоли браузера?", correct, candidates, code, "javascript")
}

private fun jsArray(rng: Random): Generated {
    val xs = List(rng.range(6, 8)) { rng.range(1, 20) }
    val literal = "[${xs.joinToString(", ")}]"
    return when (rng.nextInt(3)) {
        0 -> {
            val t = rng.range(4, 12); val k = rng.range(2, 4)
            numeric("Что выведет код?", xs.filter { it > t }.sumOf { it * k },
                "const xs = $literal;\nconst r = xs.filter((x) => x > $t).map((x) => x * $k).reduce((acc, x) => acc + x, 0);\nconsole.log(r);", "javascript")
        }
        1 -> {
            val i = rng.range(0, 2); val j = rng.range(i + 2, xs.size - 1)
            numeric("Что выведет код?", xs.subList(i, j).sum(),
                "const xs = $literal;\nconsole.log(xs.slice($i, $j).reduce((a, b) => a + b, 0));", "javascript")
        }
        else -> {
            val withDups = xs + xs.shuffled(rng).take(rng.range(1, 3))
            numeric("Что выведет код?", withDups.toSet().size * withDups.size,
                "const xs = [${withDups.joinToString(", ")}];\nconst unique = new Set(xs);\nconsole.log(unique.size * xs.length);", "javascript")
        }
    }
}

private fun closures(rng: Random): Generated {
    val n = rng.range(2, 9); val m = rng.range(2, 9); val p = rng.range(1, 5); val q = rng.range(1, 5)
    val varFirst = rng.nextBoolean()
    fun times(name: String, k: Int) = if (k == 1) name else "$name * $k"
    val varLoop = "for (var i = 0; i < $n; i++) a.push(() => ${times("i", p)});"
    val letLoop = "for (let j = 0; j < $m; j++) b.push(() => ${times("j", q)});"
    // var: one shared binding, every closure sees its final value n; let: a fresh binding per iteration.
    val answer = p * n * n + q * m * (m - 1) / 2
    val loops = if (varFirst) "$varLoop\n$letLoop" else "$letLoop\n$varLoop"
    return numeric("Что выведет код?", answer,
        "const a = [];\nconst b = [];\n$loops\nconst sum = [...a, ...b].reduce((acc, f) => acc + f(), 0);\nconsole.log(sum);", "javascript")
}

private fun coercion(rng: Random): Generated {
    fun s(v: Any) = "\"$v\""
    var a: Int; var b: Int; var c: Int
    do { a = rng.range(1, 9); b = rng.range(1, 9); c = rng.range(1, 9) } while (a == b || b == c || a == c || a <= b)
    val (expr, correct, wrong) = when (rng.nextInt(5)) {
        0 -> Triple("\"$a\" + $b - $c", "${a * 10 + b - c}", listOf("${a + b - c}", s(a * 10 + b - c), "NaN", s("$a$b$c")))
        1 -> Triple("$a + $b + \"$c\"", s("${a + b}$c"), listOf(s("$a$b$c"), "${a + b + c}", "${a + b}$c", s(a + b + c)))
        2 -> Triple("\"$a\" * \"$b\"", "${a * b}", listOf(s("$a$b"), "NaN", s(a * b), "${a + b}"))
        3 -> Triple("$a - \"$b\" + \"$c\"", s("${a - b}$c"), listOf("${a - b + c}", s("$a$b$c"), "NaN", "${a - b}$c"))
        else -> Triple("[$a] + [$b]", s("$a$b"), listOf("${a + b}", "[$a, $b]", s("[$a][$b]"), "NaN"))
    }
    return choice(rng, "Что выведет код? (строки в вариантах показаны в кавычках)", correct, wrong, "console.log($expr);", "javascript")
}

private data class Specificity(val ids: Int, val classes: Int, val types: Int) : Comparable<Specificity> {
    override fun compareTo(other: Specificity) = compareValuesBy(this, other, { it.ids }, { it.classes }, { it.types })
}

private fun specificity(rng: Random): Generated {
    val id = rng.pick(listOf("buy", "promo", "cta", "main-link", "signup"))
    val (c1, c2) = listOf("btn", "link", "accent", "primary", "large", "active").shuffled(rng).take(2)
    val navClass = rng.pick(listOf("menu", "header", "toolbar"))
    val itemClass = rng.pick(listOf("item", "entry", "cell"))
    // (selector, specificity) pairs that all match <a id class> inside nav.navClass > ul > li.itemClass
    val targets = listOf(
        "a" to Specificity(0, 0, 1), "a.$c1" to Specificity(0, 1, 1), ".$c1.$c2" to Specificity(0, 2, 0),
        "#$id" to Specificity(1, 0, 0), "a#$id" to Specificity(1, 0, 1), ".$c2" to Specificity(0, 1, 0),
        "a[href]" to Specificity(0, 1, 1), "a.$c1.$c2" to Specificity(0, 2, 1),
    )
    val ancestors = listOf(
        "" to Specificity(0, 0, 0), "nav " to Specificity(0, 0, 1), ".$navClass " to Specificity(0, 1, 0),
        "li " to Specificity(0, 0, 1), ".$itemClass " to Specificity(0, 1, 0), "nav .$itemClass " to Specificity(0, 1, 1),
        "ul li " to Specificity(0, 0, 2), ".$navClass li " to Specificity(0, 1, 1),
    )
    var rules: List<Pair<String, Specificity>>
    do {
        rules = List(4) {
            val (anc, s1) = rng.pick(ancestors); val (t, s2) = rng.pick(targets)
            anc + t to Specificity(s1.ids + s2.ids, s1.classes + s2.classes, s1.types + s2.types)
        }
        val best = rules.maxOf { it.second }
    } while (rules.map { it.first }.toSet().size < 4 || rules.count { it.second == best } != 1)
    val colors = listOf("red", "blue", "green", "orange", "purple", "teal", "gray", "brown").shuffled(rng).take(4)
    val winner = colors[rules.indices.maxBy { rules[it].second }]
    val html = "<nav class=\"$navClass\">\n  <ul>\n    <li class=\"$itemClass\">\n      <a id=\"$id\" class=\"$c1 $c2\" href=\"/go\">Ссылка</a>\n    </li>\n  </ul>\n</nav>"
    val css = rules.mapIndexed { i, (selector, _) -> "$selector { color: ${colors[i]}; }" }.joinToString("\n")
    return choice(rng, "Какого цвета будет текст ссылки? Других стилей нет.", winner, colors - winner, "$html\n\n<style>\n$css\n</style>", "html")
}

private fun boxModel(rng: Random): Generated {
    val width = 10 * rng.range(15, 40); val py = rng.range(4, 16); val px = rng.range(8, 32); val border = rng.range(1, 4); val margin = rng.range(8, 24)
    val borderBox = rng.nextBoolean()
    val answer = if (borderBox) width else width + 2 * px + 2 * border
    return numeric(
        "Какой будет ширина элемента .card от внешнего края рамки до внешнего края рамки (без margin), px?", answer,
        ".card {\n  box-sizing: ${if (borderBox) "border-box" else "content-box"};\n  width: ${width}px;\n  padding: ${py}px ${px}px;\n  border: ${border}px solid #ccc;\n  margin: ${margin}px;\n}",
        "css", "px")
}

private fun flexGrow(rng: Random): Generated {
    val bases = List(3) { 10 * rng.range(5, 20) }
    var grows: List<Int>
    do { grows = List(3) { rng.range(0, 3) } } while (grows.sum() == 0)
    val gap = rng.pick(listOf(0, 8, 16, 24))
    val free = grows.sum() * rng.range(5, 40)
    val width = bases.sum() + 2 * gap + free
    val target = rng.nextInt(3)
    val names = listOf("a", "b", "c")
    val answer = bases[target] + free * grows[target] / grows.sum()
    val css = ".row { display: flex; width: ${width}px; gap: ${gap}px; }\n" +
        names.indices.joinToString("\n") { ".${names[it]} { flex: ${grows[it]} 1 ${bases[it]}px; }" } +
        "\n/* внутри .row по порядку: .a, .b, .c — без padding и border */"
    return numeric("Какой будет ширина элемента .${names[target]}, px?", answer, css, "css", "px")
}

/**
 * An effect runs after mount and after every render in which a dependency changed; without a dependency
 * array it runs after every render. Clicks change `count`, keystrokes change `text`.
 */
private fun reactEffects(rng: Random): Generated {
    val clicks = rng.range(2, 7); val keys = rng.range(1, 6)
    val deps = listOf("", ", []", ", [count]", ", [text]", ", [count, text]")
    fun runs(d: String) = 1 + (if (d.isEmpty() || "count" in d) clicks else 0) + (if (d.isEmpty() || "text" in d) keys else 0)
    val (first, second) = deps.shuffled(rng).take(2)
    val (logA, logB) = listOf("A", "B", "C", "D").shuffled(rng).take(2)
    val askBoth = rng.nextBoolean()
    val code = """
        function Search() {
          const [count, setCount] = useState(0);
          const [text, setText] = useState("");
          useEffect(() => {
            console.log("$logA");
          }$first);
          useEffect(() => {
            console.log("$logB");
          }$second);
          return (
            <>
              <button onClick={() => setCount((c) => c + 1)}>{count}</button>
              <input value={text} onChange={(e) => setText(e.target.value)} />
            </>
          );
        }""".trimIndent()
    val action = "Компонент смонтировали, пользователь нажал на кнопку $clicks раз, затем ввёл в поле $keys ${plural(keys, "символ", "символа", "символов")} (каждый символ — отдельное изменение)."
    return if (askBoth) numeric("$action Сколько всего строк выведется в консоль? (production-сборка, без StrictMode)", runs(first) + runs(second), code, "jsx")
    else numeric("$action Сколько раз в консоли появится «$logB»? (production-сборка, без StrictMode)", runs(second), code, "jsx")
}

/** Network budget: time = RTT + size·8 / bandwidth (1 КБ = 1000 байт, 1 Мбит/с = 1000 кбит/с). */
private fun transferTime(rng: Random): Generated {
    val rtt = 10 * rng.range(2, 20); val bandwidth = rng.pick(listOf(2, 4, 5, 8, 10, 16, 20, 40))
    val intro = "Соединение уже установлено, ответ приходит за один запрос: время = RTT + время передачи (TCP slow start не учитывайте). Канал $bandwidth Мбит/с, RTT $rtt мс, 1 КБ = 1000 байт."
    return if (rng.nextBoolean()) {
        val size = 10 * rng.range(5, 120)
        numeric("$intro Сколько миллисекунд займёт загрузка главного изображения весом $size КБ? Округлите до целого.", rtt + size * 8.0 / bandwidth, 0.51, unit = "мс")
    } else {
        val budget = 100 * rng.range(4, 25)
        numeric("$intro Бюджет на загрузку LCP-изображения — $budget мс. Каким может быть максимальный вес изображения, КБ? Округлите до целого.", (budget - rtt) * bandwidth / 8.0, 0.51, unit = "КБ")
    }
}

/** React state updates in one handler are queued: setN(n + a) replaces with the render-time snapshot n + a, setN(p => p + b) applies to the queued value. */
private fun reactBatching(rng: Random): Generated {
    val start = rng.range(0, 10)
    var updates: List<Pair<Boolean, Int>>
    var result: Int
    do {
        updates = List(rng.range(3, 4)) { rng.nextBoolean() to rng.range(1, 5) }
        result = updates.fold(start) { state, (functional, v) -> if (functional) state + v else start + v }
    } while (updates.none { it.first } || updates.all { it.first } || result == start + updates.sumOf { it.second })
    val body = updates.joinToString("\n") { (functional, v) -> if (functional) "    setN((prev) => prev + $v);" else "    setN(n + $v);" }
    val code = "function Counter() {\n  const [n, setN] = useState($start);\n  function handleClick() {\n$body\n  }\n  return <button onClick={handleClick}>{n}</button>;\n}"
    return numeric("Какое число будет на кнопке после одного нажатия? (React 18+)", result, code, "jsx")
}

private fun tsTypes(rng: Random): Generated {
    val (k1, k2) = listOf("id", "name", "email", "price", "title", "status").shuffled(rng).take(2)
    val mode = rng.pick(listOf("dark", "light", "auto"))
    val letters = listOf("\"a\"", "\"b\"", "\"c\"")
    val excluded = rng.pick(letters)
    val rest = (letters - excluded).joinToString(" | ")
    val n = rng.range(1, 99)
    return variant(rng, listOf(
        Variant("Какой тип TypeScript выведет для pair?", "(string | number)[]",
            listOf("[number, string]", "readonly [number, string]", "any[]", "unknown[]"), "const pair = [$n, \"$k1\"];", "typescript"),
        Variant("Чему равен тип Keys?", "\"$k1\" | \"$k2\"",
            listOf("string", "number | string", "{ $k1: number; $k2: string }", "\"$k1\""), "type Keys = keyof { $k1: number; $k2: string };", "typescript"),
        Variant("Чему равен тип Mode?", "\"$mode\"",
            listOf("string", "readonly string", "{ mode: string }", "\"$mode\" | undefined"), "const cfg = { mode: \"$mode\" } as const;\ntype Mode = typeof cfg.mode;", "typescript"),
        Variant("Чему равен тип R?", rest,
            listOf(excluded, letters.joinToString(" | "), "never", "string"), "type R = Exclude<${letters.joinToString(" | ")}, $excluded>;", "typescript"),
        Variant("Какой тип возвращает функция f?", "number",
            listOf("string | number", "any", "number | undefined", "string"), "function f(x: string | number) {\n  if (typeof x === \"string\") return x.length;\n  return x;\n}", "typescript"),
        Variant("Чему эквивалентен тип P?", "{ $k1?: number }",
            listOf("{ $k1: number | undefined }", "{ $k1?: number | null }", "Record<string, number>", "{ readonly $k1: number }"), "type P = Partial<{ $k1: number }>;", "typescript"),
    ))
}

private fun accessibility(rng: Random) = variant(rng, listOf(
    Variant("Кнопка «закрыть» содержит только SVG-иконку крестика без текста. Как сделать её понятной для скринридера?",
        "Добавить кнопке aria-label=\"Закрыть\" (иконку скрыть через aria-hidden)",
        listOf("Добавить title к SVG и tabindex=\"-1\" к кнопке", "Сделать иконку крупнее", "Заменить <button> на <div> с onclick", "Добавить role=\"img\" к кнопке")),
    Variant("Декоративная картинка-разделитель не несёт смысла. Какой alt ей указать?",
        "Пустой alt=\"\", чтобы скринридер её пропустил",
        listOf("Не указывать alt вовсе", "alt=\"image\"", "alt с подробным описанием картинки", "alt=\"декоративная картинка\" для всех таких изображений")),
    Variant("У поля ввода email нет видимой подписи, только placeholder. Что исправить в первую очередь?",
        "Добавить видимый <label for=…>, связанный с полем",
        listOf("Сделать placeholder темнее", "Добавить поле в tabindex", "Перенести подсказку в title", "Убрать placeholder")),
    Variant("Карточка товара кликабельна: <div onclick=\"open()\">. Чем это плохо?",
        "Элемент недоступен с клавиатуры и не объявляется как интерактивный; нужен <button> или <a>",
        listOf("Ничем: onclick работает на любом элементе", "Только тем, что курсор не меняется на pointer", "Тем, что div нельзя стилизовать", "Тем, что события не всплывают")),
    Variant("Дизайнер просит убрать обводку фокуса: «outline: none» для всех элементов. Как поступить?",
        "Оставить видимый индикатор фокуса, например стилизовать :focus-visible",
        listOf("Убрать — фокус нужен только разработчикам", "Убрать и добавить tabindex=\"0\" всем элементам", "Заменить outline на изменение курсора")),
    Variant("Ошибка в форме показывается только красной рамкой поля. Что нарушено и как исправить?",
        "Информация передаётся только цветом; нужен текст ошибки рядом с полем, связанный через aria-describedby",
        listOf("Ничего — красный цвет общепринят", "Нужно сделать рамку толще", "Нужно добавить звуковой сигнал", "Нужно показать alert() с ошибкой")),
))

private fun webVitals(rng: Random) = variant(rng, listOf(
    Variant("Главный баннер на лендинге загружается последним, LCP = ${rng.range(35, 60) / 10.0} с. Что даст наибольший эффект?",
        "Загрузить LCP-изображение приоритетно (preload / fetchpriority=\"high\") и отдать оптимальный размер",
        listOf("Добавить loading=\"lazy\" баннеру", "Перенести все скрипты в <head>", "Увеличить TTL кэша CSS", "Сжать шрифты в WOFF")),
    Variant("При загрузке рекламного блока контент страницы «прыгает» вниз. Какая метрика страдает и как исправить?",
        "CLS; зарезервировать место под блок (размеры или aspect-ratio)",
        listOf("LCP; включить HTTP/2", "INP; добавить debounce на скролл", "TTFB; кэшировать HTML", "FCP; инлайнить критический CSS")),
    Variant("После клика по фильтру интерфейс «замирает» на ${rng.range(300, 900)} мс из-за долгой задачи в главном потоке. Какая метрика ухудшается и что делать?",
        "INP; разбить долгую задачу на части и уступать главный поток (yield), тяжёлое — в Web Worker",
        listOf("CLS; задать размеры изображениям", "LCP; предзагрузить шрифты", "TTFB; включить CDN", "FCP; удалить неиспользуемый CSS")),
    Variant("Бандл SPA весит ${rng.range(2, 6)} МБ, первые экраны используют малую часть кода. Что сделать?",
        "Code splitting: разбить по маршрутам и загружать лениво через dynamic import()",
        listOf("Минифицировать CSS", "Перейти с HTTP/2 на HTTP/1.1", "Объединить все модули в один файл", "Отключить gzip")),
    Variant("Поле поиска отправляет запрос на сервер при каждом нажатии клавиши, сервер перегружен. Как исправить на фронтенде?",
        "Debounce ввода (например, 300 мс) и отмена устаревших запросов (AbortController)",
        listOf("Throttle скролла", "Увеличить таймаут fetch", "Кэшировать ответы в cookies", "Отправлять запросы синхронным XHR")),
    Variant("Список из ${rng.range(5, 20)} тыс. строк рендерится целиком и тормозит. Что поможет сильнее всего?",
        "Виртуализация списка: рендерить только видимые строки",
        listOf("Добавить key={index} каждой строке", "Обернуть список в React.StrictMode", "Перенести стили в inline style", "Добавить will-change: transform всем строкам")),
))

/** Fluid type: clamp(min, preferred, max) with 1vw = 1 % of the viewport width. */
private fun fluidClamp(rng: Random): Generated {
    val min = rng.pick(listOf(14, 16, 18)); val max = rng.pick(listOf(28, 32, 40, 48))
    val vw = rng.pick(listOf(1.0, 1.5, 2.0, 2.5, 3.0)); val base = rng.pick(listOf(8, 10, 12, 14))
    val viewport = rng.pick(listOf(360, 414, 768, 1024, 1280, 1440, 1920))
    val preferred = vw * viewport / 100.0 + base
    val vwText = if (vw == vw.toInt().toDouble()) vw.toInt().toString() else vw.toString()
    return numeric(
        "Ширина окна браузера — $viewport px. Какой будет вычисленный размер шрифта заголовка, px? Ответ с точностью до 0,5 px.",
        preferred.coerceIn(min.toDouble(), max.toDouble()), 0.5,
        "h1 {\n  font-size: clamp(${min}px, ${vwText}vw + ${base}px, ${max}px);\n}", "css", "px")
}

val frontendFamilies: List<ItemFamily> = listOf(
    family("fe.js.eventloop", "JS: event loop", "javascript", FE, MIDDLE, 1.4, 0.5, PARAMETRIC, 4, ::eventLoop),
    family("fe.js.array", "JS: методы массивов", "javascript", FE, JUNIOR, 1.5, -0.9, PARAMETRIC, 0, ::jsArray),
    family("fe.js.closures", "JS: замыкания, var и let", "javascript", FE, MIDDLE, 1.3, 0.2, PARAMETRIC, 0, ::closures),
    family("fe.js.coercion", "JS: приведение типов", "javascript", FE, MIDDLE, 1.1, 0.3, PARAMETRIC, 4, ::coercion),
    family("fe.css.specificity", "CSS: специфичность", "css", FE, MIDDLE, 1.3, 0.3, PARAMETRIC, 4, ::specificity),
    family("fe.css.boxmodel", "CSS: блочная модель", "css", FE_DS, JUNIOR, 1.4, -0.6, PARAMETRIC, 0, ::boxModel),
    family("fe.css.flex", "CSS: flex-grow", "css", FE_DS, SENIOR, 1.3, 0.8, PARAMETRIC, 0, ::flexGrow),
    family("fe.css.clamp", "CSS: адаптивная типографика clamp()", "css", FE_DS, MIDDLE, 1.3, 0.4, PARAMETRIC, 0, ::fluidClamp),
    family("fe.react.effects", "React: useEffect и зависимости", "react", FE, MIDDLE, 1.3, 0.2, PARAMETRIC, 0, ::reactEffects),
    family("fe.react.batching", "React: очередь обновлений состояния", "react", FE, SENIOR, 1.4, 1.0, PARAMETRIC, 0, ::reactBatching),
    family("fe.ts.types", "TypeScript: вывод типов", "typescript", FE, MIDDLE, 1.1, 0.5, VARIANTS, 4, ::tsTypes),
    family("fe.a11y", "Доступность интерфейсов", "accessibility", FE_DS, JUNIOR, 1.1, -0.7, VARIANTS, 4, ::accessibility),
    family("fe.perf.vitals", "Web Vitals и производительность", "web-performance", FE, MIDDLE, 1.1, 0.6, VARIANTS, 4, ::webVitals),
    family("fe.perf.transfer", "Сетевой бюджет загрузки", "web-performance", FE, MIDDLE, 1.3, 0.3, PARAMETRIC, 0, ::transferTime),
)
