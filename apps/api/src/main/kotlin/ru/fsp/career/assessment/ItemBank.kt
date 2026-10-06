package ru.fsp.career.assessment

import ru.fsp.career.assessment.content.backendFamilies
import ru.fsp.career.assessment.content.dataFamilies
import ru.fsp.career.assessment.content.designFamilies
import ru.fsp.career.assessment.content.frontendFamilies
import ru.fsp.career.assessment.content.generalFamilies
import ru.fsp.career.assessment.content.productFamilies

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
