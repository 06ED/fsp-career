package ru.fsp.career.identity.infrastructure

import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fsp.career.identity.domain.VerificationMailer
import java.util.Properties

internal class SmtpVerificationMailer(private val config: AuthConfig) : VerificationMailer {
    override suspend fun send(email: String, token: String): Unit = withContext(Dispatchers.IO) {
        val host = System.getenv("SMTP_HOST")?.takeIf { it.isNotBlank() } ?: error("SMTP_HOST is required")
        val user = System.getenv("SMTP_USER")?.takeIf { it.isNotBlank() }
        val port = System.getenv("SMTP_PORT") ?: "587"
        val ssl = port == "465"
        val startTls = !ssl && (System.getenv("SMTP_STARTTLS") ?: "true").toBooleanStrict()
        require(!config.secure || user == null || ssl || startTls) { "Authenticated production SMTP requires TLS" }
        val properties = Properties().apply {
            setProperty("mail.smtp.host", host)
            setProperty("mail.smtp.port", port)
            setProperty("mail.smtp.auth", (user != null).toString())
            setProperty("mail.smtp.ssl.enable", ssl.toString())
            setProperty("mail.smtp.ssl.checkserveridentity", "true")
            setProperty("mail.smtp.starttls.enable", startTls.toString())
            setProperty("mail.smtp.starttls.required", startTls.toString())
            setProperty("mail.smtp.connectiontimeout", "5000")
            setProperty("mail.smtp.timeout", "10000")
            setProperty("mail.smtp.writetimeout", "10000")
        }
        val session = Session.getInstance(properties)
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(System.getenv("SMTP_FROM") ?: "noreply@career.local"))
            setRecipient(Message.RecipientType.TO, InternetAddress(email, true))
            setSubject("Подтвердите почту — ФСП Карьера", "UTF-8")
            setText("Подтвердите регистрацию: ${config.frontendOrigin}/login#verify=$token\n\nСсылка действует 24 часа. Если вы не регистрировались, проигнорируйте письмо.", "UTF-8")
        }
        session.getTransport("smtp").use { transport ->
            transport.connect(host, user, System.getenv("SMTP_PASSWORD"))
            transport.sendMessage(message, message.allRecipients)
        }
    }
}
