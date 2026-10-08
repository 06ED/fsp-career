package ru.fsp.career.assessment.domain

import ru.fsp.career.assessment.domain.content.*

/** All item families. Content lives in Kotlin (not in the DB), so answer keys never ship in public seed data. */
object ItemBank {
    val families: List<ItemFamily> = generalFamilies + backendFamilies + frontendFamilies + dataFamilies + productFamilies + designFamilies

    private val byId: Map<String, ItemFamily> = families.associateBy { it.id }

    init {
        require(byId.size == families.size) { "Duplicate item family id" }
        require(families.all { it.topic in Topics.labels }) { "Unknown topic in item bank" }
    }

    fun family(id: String): ItemFamily? = byId[id]

    /** Families a candidate of this specialization can receive (own track + shared families). */
    fun pool(specialization: String): List<ItemFamily> = families.filter { specialization in it.specializations }
}
