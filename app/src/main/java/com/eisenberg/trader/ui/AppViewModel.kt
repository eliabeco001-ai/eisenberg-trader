package com.eisenberg.trader.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eisenberg.trader.data.*
import com.eisenberg.trader.domain.Risk
import com.eisenberg.trader.provider.SimulatedProvider
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    private val prefs = app.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    private fun pd(k: String, d: Double) = prefs.getString(k, null)?.toDoubleOrNull() ?: d

    val assets = dao.assets().map { l -> l.map { it.toAsset() }.sortedBy { it.symbol } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val trades = dao.trades().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val positions = dao.positions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var selected by mutableIntStateOf(0)
    var message by mutableStateOf("")
    var bal by mutableDoubleStateOf(pd("bal", 10000.0))
    var risk by mutableDoubleStateOf(pd("risk", 1.0))
    var sl by mutableDoubleStateOf(pd("sl", 2.0))
    var tp by mutableDoubleStateOf(pd("tp", 4.0))

    init {
        viewModelScope.launch {
            runCatching {
                if (dao.count() == 0) listOf("SIM-ALFA", "SIM-BETA", "SIM-GAMA").forEach { dao.upsert(SimulatedProvider.asset(it).toEntity()) }
            }.onFailure { message = "Erro ao iniciar o banco: ${it.message}" }
        }
    }

    private fun current(): Asset? = assets.value.getOrNull(selected) ?: assets.value.firstOrNull()
    private fun cash() = bal + trades.value.sumOf { it.pnl } - positions.value.sumOf { it.entry * it.qty }

    fun buy() {
        val a = current() ?: return
        if (positions.value.any { it.symbol == a.symbol }) { message = "Já existe posição simulada neste ativo."; return }
        val px = a.closes.last()
        val q = Risk.size(cash(), risk, px, sl)
        if (q <= 0) { message = "Saldo simulado insuficiente."; return }
        viewModelScope.launch {
            dao.upsertPos(PositionEntity(a.symbol, px, q, px * (1 - sl / 100), px * (1 + tp / 100)))
            message = "Compra simulada executada."
        }
    }

    private suspend fun close(p: PositionEntity, px: Double, src: String, why: String) {
        dao.insertTrade(TradeEntity(symbol = p.symbol, source = src, entry = p.entry, exit = px, qty = p.qty,
            pnl = (px - p.entry) * p.qty, reason = why, time = System.currentTimeMillis()))
        dao.delPos(p.symbol)
    }

    fun closeManual(p: PositionEntity) {
        val a = assets.value.find { it.symbol == p.symbol } ?: return
        viewModelScope.launch { close(p, a.closes.last(), a.source, "Manual") }
    }

    fun step() {
        val a = current() ?: return
        if (a.source != "SIM") { message = "Avançar candle só existe para ativos simulados."; return }
        viewModelScope.launch {
            val px = SimulatedProvider.next(a.closes.last())
            dao.upsert(a.copy(closes = a.closes + px).toEntity())
            positions.value.filter { it.symbol == a.symbol }.forEach {
                if (px <= it.sl) close(it, px, a.source, "Stop-loss") else if (px >= it.tp) close(it, px, a.source, "Take-profit")
            }
        }
    }

    fun saveCfg(b: Double, r: Double, s: Double, t: Double) {
        bal = b; risk = r; sl = s; tp = t
        prefs.edit().putString("bal", "$b").putString("risk", "$r").putString("sl", "$s").putString("tp", "$t").apply()
        viewModelScope.launch { dao.clearPositions(); dao.clearTrades(); message = "Configuração salva e saldo zerado." }
    }

    fun addAsset(sym: String, raw: String) {
        val s = sym.trim().uppercase()
        if (s.isEmpty()) { message = "Informe o símbolo."; return }
        if (assets.value.any { it.symbol == s }) { message = "Símbolo já existe."; return }
        viewModelScope.launch {
            if (raw.isBlank()) dao.upsert(SimulatedProvider.asset(s).toEntity())
            else {
                val c = raw.split(Regex("[\\s,;]+")).mapNotNull { it.toDoubleOrNull() }.filter { it > 0 }
                if (c.size < 60) { message = "Dados insuficientes (mínimo 60 valores, ponto como decimal)."; return@launch }
                dao.upsert(Asset(s, "REAL", c).toEntity())
            }
            message = "Ativo adicionado."
        }
    }

    fun delAsset(sym: String) {
        if (assets.value.size < 2) { message = "Mantenha ao menos um ativo."; return }
        selected = 0
        viewModelScope.launch { dao.delPos(sym); dao.delAsset(sym) }
    }

    fun clearTrades() { viewModelScope.launch { dao.clearTrades() } }
}
