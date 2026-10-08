package ru.fsp.career.assessment.domain.content

import ru.fsp.career.assessment.domain.*
import ru.fsp.career.assessment.domain.FamilyKind.*
import ru.fsp.career.assessment.domain.Grade.*
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

/* Product design families; contrast, grid and colour families are shared with the frontend track. */

private val DS = setOf("design")
private val DS_FE = setOf("design", "frontend")

private fun hex(rgb: List<Int>) = "#" + rgb.joinToString("") { "%02X".format(it) }

/** WCAG 2.x relative luminance of an sRGB colour. */
internal fun luminance(rgb: List<Int>): Double {
    val linear = rgb.map { c ->
        val s = c / 255.0
        if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]
}

internal fun contrastRatio(a: List<Int>, b: List<Int>): Double {
    val (l1, l2) = listOf(luminance(a), luminance(b)).sortedDescending()
    return (l1 + 0.05) / (l2 + 0.05)
}

/** The level is drawn uniformly first; colours are re-drawn until the ratio lands in it, ≥ 0.3 away from 3, 4.5 and 7. */
private fun contrast(rng: Random): Generated {
    val levels = listOf("AA и AAA (≥ 7:1)", "AA, но не AAA (от 4,5:1 до 7:1)", "Только для крупного текста (от 3:1 до 4,5:1)", "Не проходит даже для крупного текста (< 3:1)")
    val target = rng.nextInt(4)
    fun levelOf(r: Double) = when { r >= 7 -> 0; r >= 4.5 -> 1; r >= 3 -> 2; else -> 3 }
    var fg: List<Int>; var bg: List<Int>; var ratio: Double
    do {
        bg = if (rng.nextBoolean()) List(3) { rng.range(225, 255) } else List(3) { rng.range(10, 45) }
        val base = rng.range(0, 255)
        fg = List(3) { (base + rng.range(-40, 40)).coerceIn(0, 255) }
        ratio = contrastRatio(fg, bg)
    } while (levelOf(ratio) != target || listOf(3.0, 4.5, 7.0).any { abs(ratio - it) < 0.3 })
    return orderedChoice(
        "Текст обычного размера (16 px, regular) цвета ${hex(fg)} на фоне ${hex(bg)}. Какому уровню WCAG 2.x соответствует контраст? Можно пользоваться формулой относительной яркости.",
        levels, target)
}

private fun gridColumn(rng: Random): Generated {
    while (true) {
        val width = rng.pick(listOf(1024, 1152, 1200, 1280, 1366, 1440, 1600, 1920)); val columns = rng.pick(listOf(4, 6, 8, 10, 12, 16))
        val gutter = rng.pick(listOf(8, 12, 16, 20, 24, 30, 32, 40)); val margin = 8 * rng.range(2, 20)
        val content = width - 2 * margin - (columns - 1) * gutter
        if (content > 0 && content % columns == 0) return numeric(
            "Макет шириной $width px: $columns колонок, gutter $gutter px, внешние поля по $margin px слева и справа. Какова ширина одной колонки, px?",
            content / columns, unit = "px")
    }
}

private fun typeScale(rng: Random): Generated {
    val base = rng.range(10, 24)
    val (name, ratio) = rng.pick(listOf("Major Second" to 1.125, "Minor Third" to 1.2, "Major Third" to 1.25, "Perfect Fourth" to 1.333,
        "Augmented Fourth" to 1.414, "Perfect Fifth" to 1.5, "Golden Ratio" to 1.618))
    val step = rng.range(1, 4)
    val up = rng.nextInt(4) != 0
    return numeric(
        "Модульная типографическая шкала: базовый размер $base px, отношение $name (${ru(ratio, 3)}). Каков размер шрифта на $step-м шаге ${if (up) "вверх" else "вниз"} от базового, px? Округлите до целого.",
        if (up) base * ratio.pow(step) else base / ratio.pow(step), 0.6, unit = "px")
}

private fun buttonPadding(rng: Random): Generated {
    val height = 2 * rng.range(14, 36); val lineHeight = rng.pick(listOf(14, 16, 18, 20, 22, 24, 28).filter { it <= height - 8 }); val border = rng.range(0, 2)
    val fontSize = rng.pick(listOf(12, 13, 14, 15, 16, 18).filter { it <= lineHeight })
    return numeric(
        "Кнопка должна иметь высоту ровно $height px (box-sizing: border-box). Текст в одну строку: font-size $fontSize px, line-height $lineHeight px; рамка $border px. Какой вертикальный padding (одинаковый сверху и снизу) нужен, px?",
        (height - lineHeight - 2 * border) / 2.0, unit = "px")
}

private fun hexChannel(rng: Random): Generated {
    val rgb = List(3) { rng.range(0, 255) }
    val channel = rng.nextInt(3)
    val names = listOf("красного (R)", "зелёного (G)", "синего (B)")
    return numeric("Цвет ${hex(rgb)}. Чему равно значение ${names[channel]} канала в десятичной записи (0–255)?", rgb[channel])
}

private fun heuristics(rng: Random): Generated {
    val all = listOf(
        "Видимость состояния системы", "Соответствие между системой и реальным миром", "Пользовательский контроль и свобода",
        "Согласованность и стандарты", "Предотвращение ошибок", "Узнавание вместо припоминания", "Гибкость и эффективность использования",
        "Эстетичный и минималистичный дизайн", "Помощь в распознавании, диагностике и исправлении ошибок", "Справка и документация",
    )
    fun v(text: String, correct: Int) = Variant("$text Какая эвристика Нильсена нарушена в первую очередь?", all[correct], all - all[correct])
    val seconds = rng.range(3, 9)
    return variant(rng, listOf(
        v("После нажатия «Оплатить» кнопка не меняется, индикатора загрузки нет — ответ приходит через $seconds с, и пользователи нажимают повторно.", 0),
        v("Банковское приложение показывает ошибку «Error 0x80070057: invalid parameter» без пояснений.", 8),
        v("Удалённое письмо нельзя восстановить: нет ни отмены, ни корзины.", 2),
        v("На одном экране «Сохранить» — синяя кнопка справа, на другом — серая ссылка слева.", 3),
        v("Кнопка «Удалить проект» стоит вплотную к «Сохранить» и срабатывает без подтверждения.", 4),
        v("Чтобы применить фильтр, нужно помнить его код из справки и ввести вручную.", 5),
        v("В приложении для покупателей вместо «Корзина» написано «Буфер позиций заказа».", 1),
    ))
}

private fun researchMethods(rng: Random): Generated {
    val methods = listOf("Открытая карточная сортировка", "Tree testing (тестирование дерева навигации)", "Модерируемое юзабилити-тестирование",
        "A/B-тест", "Контекстное интервью (наблюдение на рабочем месте)", "Опрос на репрезентативной выборке", "Фокус-группа", "Eye-tracking")
    fun v(text: String, correct: Int) = Variant("$text Какой метод подходит лучше всего?", methods[correct], methods - methods[correct])
    return variant(rng, listOf(
        v("Нужно понять, как пользователи сами группируют ${rng.range(40, 90)} разделов справки, чтобы спроектировать навигацию.", 0),
        v("Новая структура меню готова; нужно проверить, находят ли люди нужные разделы, без влияния визуального дизайна.", 1),
        v("Готов кликабельный прототип оформления заказа; нужно найти, где пользователи спотыкаются и почему.", 2),
        v("Есть две версии лендинга; нужно понять, какая приносит больше заявок. Трафик — ${rng.range(20, 90)} тыс. визитов в неделю.", 3),
        v("Делаем приложение для кладовщиков; нужно понять, в каких условиях и как они работают со сканером и накладными.", 4),
        v("Нужно измерить, какая доля всех клиентов довольна новым тарифом.", 5),
    ))
}

/** Fitts' law, Shannon formulation: ID = log2(D / W + 1); D is chosen so that ID is an integer. */
private fun fitts(rng: Random): Generated {
    val width = rng.pick(listOf(12, 16, 20, 24, 32, 40, 48, 56)); val id = rng.range(1, 5)
    val distance = width * ((1 shl id) - 1)
    val a = rng.pick(listOf(0, 50, 80, 100, 150, 200)); val b = rng.pick(listOf(80, 100, 120, 150, 180, 200, 250))
    return numeric(
        "Время наведения MT = a + b · ID, где ID — индекс сложности по закону Фиттса в формулировке Шеннона; a = $a мс, b = $b мс/бит. Кнопка шириной $width px находится на расстоянии $distance px (до центра) от курсора. Чему равно MT, мс?",
        a + b * id, unit = "мс")
}

/** System Usability Scale: odd items contribute (x − 1), even items (5 − x); the sum is multiplied by 2.5. */
private fun sus(rng: Random): Generated {
    val answers = List(10) { i -> if (i % 2 == 0) rng.range(2, 5) else rng.range(1, 4) }
    val score = 2.5 * answers.mapIndexed { i, x -> if (i % 2 == 0) x - 1 else 5 - x }.sum()
    return numeric(
        "Респондент заполнил опросник System Usability Scale (10 утверждений, шкала 1–5). Ответы по порядку: ${answers.joinToString(", ")}. Чему равен его балл SUS?",
        score, 0.0)
}

private fun tokens(rng: Random): Generated {
    val shade = rng.pick(listOf(500, 600, 700))
    return variant(rng, listOf(
        Variant("Как задать цвет текста ошибки, чтобы светлая и тёмная темы переключались без правки компонентов?",
            "Семантическим токеном вроде color.text.danger, который в каждой теме ссылается на свой примитив (например, red-$shade)",
            listOf("Использовать в компонентах напрямую red-$shade", "Назвать токен по HEX-значению: color-D93025", "Сделать отдельный компонент «Красный текст»")),
        Variant("Дизайнеры часто отвязывают (detach) инстансы кнопки, чтобы поменять отступы. Что лучше сделать в дизайн-системе?",
            "Добавить нужные варианты/свойства в компонент и зафиксировать отступы токенами",
            listOf("Разрешить отвязывать — так быстрее", "Запретить кнопки в макетах", "Сделать отдельную копию библиотеки для каждой команды")),
        Variant("Какой набор состояний обязателен для интерактивной кнопки в дизайн-системе?",
            "default, hover, focus-visible, pressed и disabled (плюс loading, если нужно)",
            listOf("Только default и disabled", "default и hover — фокус браузер нарисует сам", "Свой цвет для каждого раздела продукта")),
        Variant("Высота поля ввода в дизайн-системе меняется с 40 на 44 px. Как выпускать такое изменение?",
            "Как мажорную версию с описанием миграции: оно ломает существующие макеты и вёрстку",
            listOf("Патч-версией без уведомления", "Тихо обновить библиотеку в Figma", "Создать новый компонент и оставить старый без пометок")),
        Variant("Зачем нужна шкала отступов (4, 8, 12, 16, 24, 32 …) вместо произвольных значений?",
            "Единый ритм и меньше решений «на глаз» — проще поддерживать консистентность макетов и кода",
            listOf("Так требует Figma", "Чтобы страницы быстрее загружались", "Чтобы файлы макетов весили меньше")),
        Variant("Команда хочет добавить в дизайн-систему третий вариант кнопки «почти как основная, но с иконкой». Как поступить?",
            "Проверить, решает ли задачу свойство существующего компонента (иконка как опция), и только при новой роли заводить вариант",
            listOf("Сразу создать отдельный компонент — так быстрее", "Разрешить каждой команде рисовать свою кнопку", "Запретить иконки в кнопках")),
        Variant("Как убедиться, что компонент из дизайн-системы и его реализация в коде не расходятся?",
            "Общие токены как единый источник правды (экспорт в код) и визуальные регрессионные тесты/Storybook",
            listOf("Раз в год сравнивать скриншоты вручную", "Передавать разработчикам PNG-макеты", "Хранить значения цветов только в Figma")),
        Variant("Какой токен выбрать для отступа между полями формы в системе с шагом 4 px, если в макете «примерно 15 px»?",
            "Ближайшее значение шкалы (16 px, space-4), а не произвольные 15 px",
            listOf("15 px — как в макете", "Любое значение — отступы не важны", "Создать новый токен space-15")),
    ))
}

private fun forms(rng: Random): Generated {
    val field = rng.pick(listOf("email", "номер телефона", "ИНН", "дату рождения"))
    return variant(rng, listOf(
        Variant("Поле «$field» подписано только плейсхолдером внутри поля. Как лучше?",
            "Постоянная подпись над полем, плейсхолдер — только пример формата",
            listOf("Оставить как есть — это экономит место", "Сделать плейсхолдер жирным", "Показывать подпись только при ошибке")),
        Variant("Пользователь ввёл некорректный $field. Какое сообщение об ошибке лучше?",
            "Конкретное, рядом с полем: что не так и как исправить, с примером правильного формата",
            listOf("«Ошибка ввода» вверху страницы", "Красная рамка поля без текста", "Всплывающее окно «Неверные данные» после отправки формы")),
        Variant("Когда лучше показывать ошибку валидации поля «$field»?",
            "После ухода из поля (on blur) или при отправке, а не на каждый введённый символ",
            listOf("С первого введённого символа", "Только после перезагрузки страницы", "Никогда — пусть проверяет сервер")),
        Variant("Как расположить поля короткой формы регистрации?",
            "В одну колонку в логичном порядке",
            listOf("В две-три колонки, чтобы форма была короче", "В случайном порядке, чтобы избежать автозаполнения", "Каждое поле на отдельной странице")),
        Variant("Как подписать основную кнопку формы оформления заказа?",
            "Глаголом, описывающим результат: «Оформить заказ»",
            listOf("«ОК»", "«Отправить»", "«Далее»")),
        Variant("Как подсказать формат поля «$field», чтобы пользователь не ошибся?",
            "Короткая подсказка с примером формата под полем и маска/автоформатирование ввода",
            listOf("Подробная инструкция в отдельном окне справки", "Только сообщение об ошибке после отправки", "Красная звёздочка рядом с полем")),
        Variant("Форма регистрации просит 12 полей, конверсия низкая. Что сделать в первую очередь?",
            "Убрать необязательные поля и отложить сбор лишних данных до момента, когда они понадобятся",
            listOf("Разбить форму на 12 шагов по одному полю", "Сделать кнопку отправки ярче", "Добавить капчу на каждое поле")),
        Variant("Пользователь нажал «Отправить», в форме 3 ошибки. Как их показать?",
            "Сводку ошибок вверху со ссылками на поля и пояснения у каждого поля, фокус — на первое ошибочное",
            listOf("Одно всплывающее окно «Есть ошибки»", "Очистить форму, чтобы заполнить заново", "Подсветить только первую ошибку")),
    ))
}

/** Android density buckets: mdpi ×1, hdpi ×1.5, xhdpi ×2, xxhdpi ×3, xxxhdpi ×4 (dp → px). */
private fun touchTargets(rng: Random): Generated {
    val (name, density) = rng.pick(listOf("mdpi" to 1.0, "hdpi" to 1.5, "xhdpi" to 2.0, "xxhdpi" to 3.0, "xxxhdpi" to 4.0))
    val target = rng.pick(listOf(40, 44, 48, 52, 56, 60, 64, 72, 80))
    val icon = rng.pick(listOf(16, 18, 20, 22, 24, 28, 32, 36).filter { it < target })
    return if (rng.nextBoolean()) numeric(
        "Зона касания кнопки — $target dp. Сколько физических пикселей по ширине она займёт на Android-экране плотности $name?", target * density, unit = "px")
    else numeric(
        "Иконка $icon dp стоит по центру зоны касания $target dp. Каков отступ от края зоны до иконки с каждой стороны в физических пикселях на экране плотности $name?",
        (target - icon) / 2.0 * density, unit = "px")
}

/** Spacing tokens: space-N = N × base; card width = paddings + borders + tiles + gaps. */
private fun tokenLayout(rng: Random): Generated {
    val base = rng.pick(listOf(4, 8))
    val padding = rng.range(2, 6); val gap = rng.range(1, 4); val count = rng.range(2, 5); val tile = 8 * rng.range(8, 30); val border = rng.range(0, 2)
    val width = 2 * padding * base + 2 * border + count * tile + (count - 1) * gap * base
    return numeric(
        "В дизайн-системе отступы задаются токенами space-N = N × $base px. Карточка: внутренние поля space-$padding слева и справа, рамка $border px, внутри в ряд $count ${plural(count, "плитка", "плитки", "плиток")} шириной $tile px с промежутками space-$gap. Какова полная ширина карточки, px?",
        width, unit = "px")
}

/** Probability that at least one of n participants hits a problem with per-user probability p: 1 − (1 − p)^n. */
private fun problemDiscovery(rng: Random): Generated {
    val p = rng.range(8, 45)
    return if (rng.nextBoolean()) {
        val n = rng.range(2, 12)
        numeric("В юзабилити-тесте конкретную проблему встречает в среднем $p % участников (независимо друг от друга). Какова вероятность (в %), что её обнаружит хотя бы один из $n участников? Округлите до целого.",
            100 * (1 - Math.pow(1 - p / 100.0, n.toDouble())), 0.51, unit = "%")
    } else {
        val goal = rng.pick(listOf(80, 85, 90, 95))
        numeric("В юзабилити-тесте конкретную проблему встречает в среднем $p % участников (независимо друг от друга). Сколько участников минимум нужно, чтобы обнаружить её с вероятностью не ниже $goal %?",
            kotlin.math.ceil(kotlin.math.ln(1 - goal / 100.0) / kotlin.math.ln(1 - p / 100.0) - 1e-9))
    }
}

val designFamilies: List<ItemFamily> = listOf(
    family("ds.contrast", "Контраст по WCAG", "accessibility", DS_FE, MIDDLE, 1.4, 0.5, PARAMETRIC, 4, ::contrast),
    family("ds.grid", "Модульная сетка", "ui-layout", DS_FE, JUNIOR, 1.5, -0.6, PARAMETRIC, 0, ::gridColumn),
    family("ds.typescale", "Типографическая шкала", "typography", DS, JUNIOR, 1.3, -0.3, PARAMETRIC, 0, ::typeScale),
    family("ds.button.padding", "Размеры компонентов", "ui-layout", DS, JUNIOR, 1.3, -1.3, PARAMETRIC, 0, ::buttonPadding),
    family("ds.hex", "Цвет в HEX и RGB", "typography", DS_FE, JUNIOR, 1.4, -1.0, PARAMETRIC, 0, ::hexChannel),
    family("ds.touch", "Плотность экранов и зоны касания", "ui-layout", DS, JUNIOR, 1.3, -0.4, PARAMETRIC, 0, ::touchTargets),
    family("ds.heuristics", "Эвристики Нильсена", "usability", DS, MIDDLE, 1.0, 0.0, VARIANTS, 4, ::heuristics),
    family("ds.research.methods", "Выбор метода исследования", "research", DS, MIDDLE, 1.1, 0.3, VARIANTS, 4, ::researchMethods),
    family("ds.fitts", "Закон Фиттса", "usability", DS, SENIOR, 1.2, 1.2, PARAMETRIC, 0, ::fitts),
    family("ds.sus", "Расчёт SUS", "usability", DS, SENIOR, 1.3, 1.1, PARAMETRIC, 0, ::sus),
    family("ds.tokens", "Дизайн-токены и компоненты", "design-systems", DS, MIDDLE, 1.0, 0.6, VARIANTS, 4, ::tokens),
    family("ds.tokens.layout", "Раскладка на токенах отступов", "design-systems", DS, MIDDLE, 1.3, -0.1, PARAMETRIC, 0, ::tokenLayout),
    family("ds.forms", "Проектирование форм", "usability", DS, JUNIOR, 1.0, -0.8, VARIANTS, 4, ::forms),
    family("ds.research.discovery", "Охват юзабилити-теста", "research", setOf("design", "product"), SENIOR, 1.2, 1.1, PARAMETRIC, 0, ::problemDiscovery),
)
