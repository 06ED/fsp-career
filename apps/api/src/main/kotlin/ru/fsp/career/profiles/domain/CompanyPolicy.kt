package ru.fsp.career.profiles.domain

import ru.fsp.career.shared.domain.*

private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private val phonePattern = Regex("^\\+?[0-9][0-9()\\-\\s]{5,24}$")
private val telegramPattern = Regex("^@[A-Za-z0-9_]{5,32}$")

/** Validates and normalises the employer company profile (industry + contact data, spec 2.1). */
internal fun validateCompany(company: Company): Company {
    val name = requireText(company.name, "Company name", 200)
    if (company.description.length > 2000 || company.website.length > 500 || company.industry.length > 100) bad("Company field too long")

    val website = company.website.trim()
    if (website.isNotEmpty() && !Regex("^https?://\\S+$").matches(website))
        bad("Website must start with http:// or https://")

    val email = company.contactEmail.trim()
    if (email.isNotEmpty() && (email.length > 320 || !emailPattern.matches(email))) bad("Invalid contact email")

    val phone = company.contactPhone.trim()
    if (phone.isNotEmpty() && !phonePattern.matches(phone)) bad("Invalid contact phone")

    val telegram = normalizeTelegram(company.contactTelegram)
    if (company.contactNote.length > 300) bad("Contact note too long")

    return Company(
        name = name,
        description = company.description.trim(),
        website = website,
        industry = company.industry.trim(),
        contactEmail = email,
        contactPhone = phone,
        contactTelegram = telegram,
        contactNote = company.contactNote.trim(),
    )
}

/** `t.me/name`, `https://t.me/name`, `name`, `@name` → `@name`. */
internal fun normalizeTelegram(raw: String): String {
    val value = raw.trim()
    if (value.isEmpty()) return ""
    val handle = "@" + value.removePrefix("https://").removePrefix("http://").removePrefix("t.me/").removePrefix("@")
    if (!telegramPattern.matches(handle)) bad("Invalid Telegram handle")
    return handle
}

/** Default «способ связи» shown to the candidate in an invitation, built from company contacts. */
internal fun companyContactLine(company: Company): String = listOfNotNull(
    company.contactEmail.takeIf { it.isNotBlank() }?.let { "Email: $it" },
    company.contactPhone.takeIf { it.isNotBlank() }?.let { "Телефон: $it" },
    company.contactTelegram.takeIf { it.isNotBlank() }?.let { "Telegram: $it" },
    company.contactNote.takeIf { it.isNotBlank() },
).joinToString(" · ")

/** Required stack of an employer need: ≤ 15 distinct tags, 1..50 chars each (case-insensitive dedupe). */
internal fun validateNeedStack(stack: List<String>): List<String> {
    val cleaned = stack.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
    if (cleaned.size > 15 || cleaned.any { it.length > 50 }) bad("Need techStack must contain at most 15 skills of 1..50 characters")
    return cleaned
}
