package ru.fsp.career.matching.validation

import java.util.Random
import kotlin.math.ln

/** Standard top-k IR metrics over graded labels 0..3; «relevant» = label ≥ RELEVANT. */
object RankingMetrics {
    const val RELEVANT = 2

    fun precisionAt(k: Int, ranked: List<Int>): Double = ranked.take(k).count { it >= RELEVANT } / k.toDouble()

    /** nDCG@k with gain 2^rel − 1 and log2(i + 1) discount; ideal ranking built from ALL labels of the need. */
    fun ndcgAt(k: Int, ranked: List<Int>, allLabels: Collection<Int>): Double {
        fun dcg(labels: List<Int>) = labels.take(k).mapIndexed { i, rel -> ((1 shl rel) - 1) / log2(i + 2.0) }.sum()
        val ideal = dcg(allLabels.sortedDescending())
        return if (ideal == 0.0) 0.0 else dcg(ranked) / ideal
    }

    /** 1 / rank of the first relevant candidate in the whole returned list; 0 if none returned. */
    fun reciprocalRank(ranked: List<Int>): Double {
        val index = ranked.indexOfFirst { it >= RELEVANT }
        return if (index < 0) 0.0 else 1.0 / (index + 1)
    }

    private fun log2(x: Double) = ln(x) / ln(2.0)

    data class Interval(val mean: Double, val low: Double, val high: Double)

    /** Percentile bootstrap over needs (the unit of analysis), deterministic seed. */
    fun bootstrap(values: List<Double>, iterations: Int = 2000, seed: Long = 7): Interval {
        val rng = Random(seed)
        val means = DoubleArray(iterations) { values.indices.sumOf { values[rng.nextInt(values.size)] } / values.size }.sorted()
        return Interval(values.average(), means[(0.025 * iterations).toInt()], means[(0.975 * iterations).toInt() - 1])
    }

    /** Paired bootstrap of the mean difference a − b (same resampled needs for both systems). */
    fun pairedDifference(a: List<Double>, b: List<Double>, iterations: Int = 2000, seed: Long = 11): Interval {
        require(a.size == b.size)
        val diffs = a.indices.map { a[it] - b[it] }
        return bootstrap(diffs, iterations, seed)
    }
}
