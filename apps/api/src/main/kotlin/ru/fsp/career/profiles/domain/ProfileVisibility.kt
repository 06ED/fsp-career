package ru.fsp.career.profiles.domain

private val emailPattern = Regex("[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}.-]+\\.[\\p{L}]{2,}")
private val linkPattern = Regex("(?i)(?:https?://|www\\.|(?:t|telegram|vk)\\.me/)[^\\s<>]+|\\b[\\w.-]+\\.(?:ru|com|org|net|online|local|io)(?:/[^\\s<>]*)?")
private val handlePattern = Regex("(?<![\\p{L}\\p{N}])@[\\p{L}\\p{N}_]{3,}")
private val phonePattern = Regex("(?<!\\d)\\+?\\d(?:[ ()\\t.-]*\\d){9,14}(?!\\d)")

/** Apply at the employer boundary as well as import, so old drafts cannot leak contacts. */
internal fun employerText(text: String, email: String = "", phone: String = "", hiddenName: String = ""): String {
    var visible = text
    for (contact in listOf(email, phone).filter { it.isNotBlank() })
        visible = visible.replace(contact, "[контакт скрыт]", ignoreCase = true)
    visible = emailPattern.replace(visible, "[контакт скрыт]")
    visible = linkPattern.replace(visible, "[ссылка скрыта]")
    visible = handlePattern.replace(visible, "[контакт скрыт]")
    // shortcut: catches conventional contacts, add moderation if deliberately obfuscated text must be blocked.
    visible = phonePattern.replace(visible, "[контакт скрыт]")
    for (name in hiddenName.split(Regex("\\s+")).filter { it.length >= 2 })
        visible = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(name)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
            .replace(visible, "[имя скрыто]")
    return visible
}
