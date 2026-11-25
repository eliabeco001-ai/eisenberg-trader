package com.eisenberg.trader

import com.eisenberg.trader.domain.*
import org.junit.Assert.*
import org.junit.Test

class EngineTest {
    private val rising = List(200) { 100.0 + it }

    @Test fun smaBasic() = assertEquals(listOf(null, null, 2.0, 3.0, 4.0), Indicators.sma(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 3))

    @Test fun rsiRisingIsHigh() = assertTrue(Indicators.rsi(rising).last()!! > 99.0)

    @Test fun macdSizes() {
        val m = Indicators.macd(rising)
        assertEquals(rising.size, m.line.size); assertEquals(rising.size, m.signal.size); assertEquals(rising.size, m.hist.size)
    }

    @Test fun riskSize() = assertEquals(50.0, Risk.size(10000.0, 1.0, 100.0, 2.0), 1e-9)

    @Test fun shortSeriesWaits() = assertEquals(Signal.ESPERA, Analysis(listOf(1.0, 2.0)).signal(1))

    @Test fun backtestRuns() {
        val r = Backtest.run(rising, 2.0, 4.0)
        assertTrue(r.maxDrawdownPct >= 0.0); assertEquals(rising.size - 50, r.curve.size)
    }

    @Test fun trendUp() = assertEquals("Alta", Trend.of(rising).first)
}
