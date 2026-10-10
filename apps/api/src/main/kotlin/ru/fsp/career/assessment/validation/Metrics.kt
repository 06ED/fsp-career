package ru.fsp.career.assessment.validation

import ru.fsp.career.assessment.domain.Grade
import kotlin.math.sqrt

/** Classification and reliability statistics used by the validation report. */
object Metrics {
    fun accuracy(reference: List<Grade?>, predicted: List<Grade?>): Double =
        reference.indices.count { reference[it] == predicted[it] }.toDouble() / reference.size

    /** Unweighted mean of per-class F1 (classes with no support and no predictions are skipped). */
    fun macroF1(reference: List<Grade?>, predicted: List<Grade?>): Double = Grade.entries.mapNotNull { c ->
        val tp = reference.indices.count { reference[it] == c && predicted[it] == c }
        val fp = reference.indices.count { reference[it] != c && predicted[it] == c }
        val fn = reference.indices.count { reference[it] == c && predicted[it] != c }
        if (tp + fp + fn == 0) null else 2.0 * tp / (2.0 * tp + fp + fn)
    }.average()

    /** Rows: grades; columns: grades, then unconfirmed. */
    fun confusion(reference: List<Grade?>, predicted: List<Grade?>): List<List<Int>> =
        Grade.entries.map { r -> (Grade.entries + null).map { p -> reference.indices.count { reference[it] == r && predicted[it] == p } } }

    /** Cohen's kappa with quadratic (or linear) disagreement weights for ordered grades. */
    fun weightedKappa(a: List<Grade?>, b: List<Grade?>, quadratic: Boolean = true): Double {
        val k = Grade.entries.size + 1
        val n = a.size.toDouble()
        val observed = Array(k) { DoubleArray(k) }
        a.indices.forEach { observed[(a[it]?.ordinal ?: -1) + 1][(b[it]?.ordinal ?: -1) + 1] += 1.0 }
        val rows = DoubleArray(k) { i -> observed[i].sum() }
        val cols = DoubleArray(k) { j -> (0 until k).sumOf { observed[it][j] } }
        var num = 0.0; var den = 0.0
        for (i in 0 until k) for (j in 0 until k) {
            val d = (i - j).toDouble() / (k - 1)
            val w = if (quadratic) d * d else kotlin.math.abs(d)
            num += w * observed[i][j]
            den += w * rows[i] * cols[j] / n
        }
        return if (den == 0.0) 0.0 else 1.0 - num / den
    }

    fun pearson(x: List<Double>, y: List<Double>): Double {
        val mx = x.average(); val my = y.average()
        var sxy = 0.0; var sxx = 0.0; var syy = 0.0
        for (i in x.indices) { val dx = x[i] - mx; val dy = y[i] - my; sxy += dx * dy; sxx += dx * dx; syy += dy * dy }
        return if (sxx == 0.0 || syy == 0.0) 0.0 else sxy / sqrt(sxx * syy)
    }

    fun sd(x: List<Double>): Double { val m = x.average(); return sqrt(x.sumOf { (it - m) * (it - m) } / (x.size - 1).coerceAtLeast(1)) }

    fun rate(flags: List<Boolean>): Double = flags.count { it }.toDouble() / flags.size.coerceAtLeast(1)
}
