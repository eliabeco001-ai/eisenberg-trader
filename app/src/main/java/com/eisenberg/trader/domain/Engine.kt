package com.eisenberg.trader.domain

enum class Signal { COMPRA, VENDA, ESPERA }

/** Conjunto de indicadores e regra de sinal (SMA20/50 + RSI14 + histograma MACD). */
class Analysis(val c: List<Double>) {
    val s20 = Indicators.sma(c, 20)
    val s50 = Indicators.sma(c, 50)
    val rsi = Indicators.rsi(c)
    val macd = Indicators.macd(c)

    fun signal(i: Int): Signal {
        val a = s20.getOrNull(i); val b = s50.getOrNull(i); val r = rsi.getOrNull(i)
        if (a == null || b == null || r == null) return Signal.ESPERA
        var s = if (a > b) 1 else -1
        s += if (r < 30) 1 else if (r > 70) -1 else 0
        s += if (macd.hist[i] > 0) 1 else -1
        return if (s >= 2) Signal.COMPRA else if (s <= -2) Signal.VENDA else Signal.ESPERA
    }
}

object Trend {
    /** Regressão linear dos últimos 20 candles: devolve rótulo e inclinação em %. */
    fun of(c: List<Double>): Pair<String, Double> {
        val y = c.takeLast(20)
        val n = y.size
        if (n < 2) return "Lateral" to 0.0
        val m = y.average()
        var nu = 0.0; var de = 0.0
        y.forEachIndexed { i, v -> val x = i - (n - 1) / 2.0; nu += x * (v - m); de += x * x }
        val sl = if (de == 0.0 || m == 0.0) 0.0 else nu / de / m * 100 * n
        return (if (sl > 2) "Alta" else if (sl < -2) "Baixa" else "Lateral") to sl
    }
}

object Risk {
    /** Tamanho da posição: arrisca riskPct% do saldo até o stop-loss, limitado ao saldo. */
    fun size(cash: Double, riskPct: Double, price: Double, slPct: Double): Double {
        if (cash <= 0 || price <= 0 || slPct <= 0) return 0.0
        return minOf(cash * riskPct / 100 / (price * slPct / 100), cash / price)
    }
}

data class BtResult(val trades: Int, val winRate: Double, val returnPct: Double,
                    val maxDrawdownPct: Double, val profitFactor: Double, val curve: List<Double>)

object Backtest {
    /** Long-only, sem custos. Entra em COMPRA; sai por VENDA, stop-loss ou take-profit. */
    fun run(c: List<Double>, slPct: Double, tpPct: Double): BtResult {
        val a = Analysis(c)
        var eq = 1.0; var pk = 1.0; var dd = 0.0
        var pos: Double? = null
        val tr = ArrayList<Double>(); val cv = ArrayList<Double>()
        for (i in 50 until c.size) {
            val p = c[i]; val e = pos
            if (e != null) {
                if (p <= e * (1 - slPct / 100) || p >= e * (1 + tpPct / 100) || a.signal(i) == Signal.VENDA) {
                    val r = p / e - 1; tr.add(r); eq *= 1 + r; pos = null
                }
            } else if (a.signal(i) == Signal.COMPRA) pos = p
            pk = maxOf(pk, eq); dd = maxOf(dd, 1 - eq / pk); cv.add(eq)
        }
        val gw = tr.filter { it > 0 }.sum(); val gl = -tr.filter { it <= 0 }.sum()
        return BtResult(
            tr.size, if (tr.isEmpty()) 0.0 else tr.count { it > 0 } * 100.0 / tr.size,
            (eq - 1) * 100, dd * 100,
            if (gl > 0) gw / gl else if (gw > 0) Double.POSITIVE_INFINITY else 0.0, cv
        )
    }
}
