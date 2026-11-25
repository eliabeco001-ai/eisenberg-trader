package com.eisenberg.trader.provider

import com.eisenberg.trader.data.Asset
import java.util.Random

/**
 * Ponto de extensão para dados de mercado. Futuras implementações (MetaTrader 5, APIs HTTP)
 * devem implementar esta interface e marcar os ativos com source = "REAL".
 */
interface MarketDataProvider {
    suspend fun closes(symbol: String): Result<List<Double>>
}

/** Dados FICTÍCIOS (passeio aleatório). Nunca representam cotações reais. */
object SimulatedProvider {
    fun asset(symbol: String): Asset {
        val r = Random(symbol.hashCode().toLong())
        var p = 100.0
        val c = List(300) { p *= 1 + (r.nextDouble() - 0.495) * 0.03; Math.round(p * 100) / 100.0 }
        return Asset(symbol, "SIM", c)
    }
    fun next(last: Double): Double = Math.round(last * (1 + (Random().nextDouble() - 0.495) * 0.03) * 100) / 100.0
}
