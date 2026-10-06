package ru.fsp.career.matching

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextAnalysisTest {
    @Test fun snowballReferenceVocabulary() {
        // Pairs from the official Snowball Russian sample vocabulary.
        mapOf(
            "вагон" to "вагон", "вагона" to "вагон", "вагонов" to "вагон", "вагоном" to "вагон", "вагоны" to "вагон",
            "важная" to "важн", "важнее" to "важн", "важнейшие" to "важн", "важнейшими" to "важн", "важничал" to "важнича",
            "важного" to "важн", "важную" to "важн", "важных" to "важн",
        ).forEach { (word, stem) -> assertEquals(stem, RussianStemmer.stem(word), word) }
    }

    @Test fun inflectedFormsShareStems() {
        listOf(
            "платежи" to "платежей", "разработчик" to "разработчиками", "сервисы" to "сервисов",
            "высоконагруженные" to "высоконагруженных", "транзакции" to "транзакций",
        ).forEach { (a, b) -> assertEquals(TextAnalysis.terms(a), TextAnalysis.terms(b), "$a / $b") }
    }

    @Test fun techTermsAreCanonicalised() {
        assertEquals(listOf("cpp"), TextAnalysis.terms("C++"))
        assertEquals(TextAnalysis.skillKey("Node.js"), TextAnalysis.skillKey("nodejs"))
        assertEquals(TextAnalysis.skillKey("Postgres"), TextAnalysis.skillKey("PostgreSQL"))
        assertEquals(TextAnalysis.skillKey("k8s"), TextAnalysis.skillKey("Kubernetes"))
        assertEquals(TextAnalysis.terms("бэкенд"), TextAnalysis.terms("backend"))
        assertEquals(TextAnalysis.terms("питон"), TextAnalysis.terms("Python"))
        assertTrue("cicd" in TextAnalysis.terms("Настроил CI/CD для сервисов"))
        assertTrue("abtest" in TextAnalysis.terms("Проводил A/B-тесты"))
    }

    @Test fun crossLanguageConceptsMatch() {
        assertEquals(TextAnalysis.terms("микросервисы"), TextAnalysis.terms("microservices"))
        assertEquals(TextAnalysis.terms("платежи"), TextAnalysis.terms("payments"))
    }

    @Test fun stopWordsAndNumbersAreDropped() {
        assertEquals(TextAnalysis.terms("команду"), TextAnalysis.terms("Ищем в команду и на 3 года"))
        assertTrue(TextAnalysis.terms("the and of").isEmpty())
    }

    @Test fun tfIdfCosineFavoursRareSharedTerms() {
        val model = TfIdfModel(listOf(listOf("kotlin", "kafka"), listOf("kotlin", "react"), listOf("kotlin", "python")))
        val doc = model.vector(listOf("kotlin", "kafka"))
        assertEquals(1.0, TfIdfModel.cosine(doc, doc), 1e-9)
        assertEquals(0.0, TfIdfModel.cosine(model.vector(listOf("python")), model.vector(listOf("react"))))
        val query = model.vector(listOf("kafka", "kotlin"))
        assertTrue(TfIdfModel.cosine(query, model.vector(listOf("kafka"))) > TfIdfModel.cosine(query, model.vector(listOf("kotlin"))))
        assertEquals("kafka", TfIdfModel.contributions(query, doc).first().first)
        assertTrue(model.vector(emptyList()).isEmpty())
    }
}
