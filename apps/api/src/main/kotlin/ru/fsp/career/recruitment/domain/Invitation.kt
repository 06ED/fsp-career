package ru.fsp.career.recruitment.domain

import kotlinx.serialization.Serializable
import ru.fsp.career.shared.domain.*

@Serializable
data class InviteRequest(
    val candidateId: String,
    val description: String,
    val salaryMinRub: Int,
    val salaryMaxRub: Int,
    val contactMethod: String = ""
)

@Serializable
data class Invitation(
    val id: String,
    val candidateId: String,
    val employerId: String,
    val description: String,
    val salaryMinRub: Int,
    val salaryMaxRub: Int,
    val status: String,
    val createdAt: String,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val companyName: String = "",
    val candidateName: String = "",
    val contactMethod: String = ""
)

@Serializable
data class Invitations(val invitations: List<Invitation>)

internal fun validateInvitation(input: InviteRequest): InviteRequest {
    val description = requireText(input.description, "Description", 4000)
    if (input.salaryMinRub <= 0 || input.salaryMaxRub < input.salaryMinRub) bad("Salary range in RUB must be positive and min <= max")
    if (input.contactMethod.length > 300) bad("contactMethod must contain at most 300 characters")
    return input.copy(description = description)
}

internal fun invitationTransitionAllowed(from: String, to: String): Boolean =
    if (to == "viewed") from == "sent" else to in listOf("accepted", "rejected") && from in listOf("sent", "viewed")
