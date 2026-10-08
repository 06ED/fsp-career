package ru.fsp.career

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class ArchitectureTest {
    @Test fun domainAndApplicationAreIndependentOfTransportAndPersistenceFrameworks() {
        val root = Path.of("src/main/kotlin/ru/fsp/career")
        val persistence = Regex("org\\.jetbrains\\.exposed|org\\.apache\\.pdfbox|java\\.sql")
        val outerLayers = Regex("ru\\.fsp\\.career\\.[\\w.]+\\.(application|infrastructure|http)\\.")
        Files.walk(root).use { files ->
            files.filter { it.toString().endsWith(".kt") }.forEach { path ->
                val name = root.relativize(path).toString().replace('\\', '/')
                val code = path.readText()
                if ("/domain/" in "/$name" || "/application/" in "/$name") {
                    assertTrue("io.ktor" !in code && !persistence.containsMatchIn(code), "Framework leaked into $name")
                }
                if ("/domain/" in "/$name") assertTrue(!outerLayers.containsMatchIn(code), "Outer layer leaked into domain $name")
                if ("/application/" in "/$name") {
                    assertTrue(!Regex("ru\\.fsp\\.career\\.[\\w.]+\\.(infrastructure|http)\\.").containsMatchIn(code), "Adapter leaked into application $name")
                }
                if ("/http/" in "/$name") assertTrue(!persistence.containsMatchIn(code), "SQL/PDF logic leaked into HTTP $name")
                if ("/infrastructure/" in "/$name") assertTrue("io.ktor.server" !in code, "HTTP adapter leaked into infrastructure $name")
            }
        }
    }
}
