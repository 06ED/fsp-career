package ru.fsp.career.assessment

import kotlin.random.Random

/**
 * Computerized adaptive test ("CAT-lite") for a declared target grade.
 *
 *  1. Routing estimate: EAP with prior N(anchor(target), 1) — the first item is pitched at the declared
 *     level and the test moves up/down after every answer.
 *  2. Next family: among unused families of topics whose blueprint quota is not yet filled, take the most
 *     informative ones at the routing estimate (Fisher information, within [SIMILAR_INFORMATION] of the
 *     best) and, among those, the least-exposed family (exposure control: popular families are not shown
 *     to everyone, which slows down any leak of variant-type content).
 *  3. Final score: EAP with the population prior N(0, 1), so the declaration routes the test but never
 *     shifts the result; the decision rule is in [Grading].
 */
object Cat {
    const val TEST_LENGTH = 12
    const val EXPIRY_MINUTES = 30L
    const val SIMILAR_INFORMATION = 0.85

    fun routingEstimate(target: Grade, grid: LikelihoodGrid): Estimate = grid.posterior(target.anchor, 1.0)

    fun finalEstimate(grid: LikelihoodGrid): Estimate = grid.posterior(0.0, 1.0)

    fun nextFamily(
        pool: List<ItemFamily>,
        quotas: Map<String, Int>,
        administered: List<ItemFamily>,
        theta: Double,
        exposure: (String) -> Long,
        rng: Random,
    ): ItemFamily? {
        val usedIds = administered.map { it.id }.toSet()
        val unused = pool.filter { it.id !in usedIds }
        if (unused.isEmpty()) return null
        val usedPerTopic = administered.groupingBy { it.topic }.eachCount()
        val open = unused.filter { (quotas[it.topic] ?: 0) > (usedPerTopic[it.topic] ?: 0) }
        val eligible = open.ifEmpty { unused }
        val informative = eligible.associateWith { Irt.information(it.params, theta) }
        val best = informative.values.max()
        val similar = informative.filterValues { it >= SIMILAR_INFORMATION * best }.keys
        val leastExposed = similar.minOf { exposure(it.id) }
        return similar.filter { exposure(it.id) == leastExposed }.let { it[rng.nextInt(it.size)] }
    }
}
