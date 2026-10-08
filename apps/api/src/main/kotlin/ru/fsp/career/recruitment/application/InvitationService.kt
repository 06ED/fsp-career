package ru.fsp.career.recruitment.application

import ru.fsp.career.recruitment.domain.*
import ru.fsp.career.shared.domain.*
import java.util.UUID

internal class InvitationService(private val invitations: InvitationRepository) {
    suspend fun send(subject: String, input: InviteRequest): Invitation {
        val invitation = validateInvitation(input)
        return invitations.send(subject, invitation)
    }

    suspend fun list(subject: String) = invitations.list(subject)

    suspend fun change(subject: String, id: UUID, target: String) =
        invitations.change(subject, id, target)
}
