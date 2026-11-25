package com.eisenberg.trader.domain

/** Indicadores técnicos puros (sem Android), testáveis em JVM. */
object Indicators {
    data class Macd(val line: List<Double>, val signal: List<Double>, val hist: List<Double>)

    fun sma(a: List<Double>, n: Int): List<Double?> =
        a.indices.map { i -> if (i < n - 1) null else a.subList(i - n + 1, i + 1).average() }

    fun ema(a: List<Double>, n: Int): List<Double> {
        val k = 2.0 / (n + 1)
        val o = ArrayList<Double>(a.size)
        a.forEachIndexed { i, v -> o.add(if (i == 0) v else v * k + o[i - 1] * (1 - k)) }
        return o
    }

    /** RSI de Wilder. */
    fun rsi(a: List<Double>, n: Int = 14): List<Double?> {
        val o = MutableList<Double?>(a.size) { null }
        if (a.size <= n) return o
        var g = 0.0
        var l = 0.0
        for (i in 1..n) { val d = a[i] - a[i - 1]; if (d > 0) g += d else l -= d }
        g /= n; l /= n
        o[n] = 100 - 100 / (1 + g / (if (l == 0.0) 1e-9 else l))
        for (i in n + 1 until a.size) {
            val d = a[i] - a[i - 1]
            g = (g * (n - 1) + maxOf(d, 0.0)) / n
            l = (l * (n - 1) + maxOf(-d, 0.0)) / n
            o[i] = 100 - 100 / (1 + g / (if (l == 0.0) 1e-9 else l))
        }
        return o
    }

    fun macd(a: List<Double>, fast: Int = 12, slow: Int = 26, sig: Int = 9): Macd {
        val ef = ema(a, fast)
        val es = ema(a, slow)
        val line = ef.indices.map { ef[it] - es[it] }
        val s = ema(line, sig)
        return Macd(line, s, line.indices.map { line[it] - s[it] })
    }
}
