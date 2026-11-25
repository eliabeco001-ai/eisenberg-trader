package com.eisenberg.trader.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eisenberg.trader.data.Asset
import com.eisenberg.trader.data.PositionEntity
import com.eisenberg.trader.data.TradeEntity
import com.eisenberg.trader.domain.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Blue = Color(0xFF2563EB)
private val Amber = Color(0xFFB7791F)
private val Green = Color(0xFF0F9D58)
private val Red = Color(0xFFD93025)

fun Double?.f(d: Int = 2): String = if (this == null || this.isNaN()) "–" else String.format(Locale.getDefault(), "%.${d}f", this)
private fun sigColor(s: Signal) = when (s) { Signal.COMPRA -> Green; Signal.VENDA -> Red; else -> Color.Gray }

@Composable fun Panel(content: @Composable ColumnScope.() -> Unit) =
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content) }

@Composable fun Badge(a: Asset) = Text(
    if (a.source == "SIM") "DADOS SIMULADOS" else "DADOS REAIS (IMPORTADOS)",
    color = if (a.source == "SIM") Amber else Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)

@Composable fun LineChart(series: List<Pair<List<Double?>, Color>>, min: Double? = null, max: Double? = null, height: Int = 160) {
    Canvas(Modifier.fillMaxWidth().height(height.dp)) {
        val ss = series.map { it.first.takeLast(120) to it.second }
        val all = ss.flatMap { it.first }.filterNotNull()
        if (all.isEmpty()) return@Canvas
        val lo = min ?: all.min(); val hi = max ?: all.max(); val n = ss[0].first.size
        fun y(v: Double) = size.height - 4f - ((v - lo) / (if (hi == lo) 1.0 else hi - lo)).toFloat() * (size.height - 8f)
        ss.forEach { (d, col) ->
            val p = Path(); var started = false
            d.forEachIndexed { i, v ->
                if (v != null) {
                    val x = i.toFloat() / (n - 1).coerceAtLeast(1) * size.width
                    if (!started) { p.moveTo(x, y(v)); started = true } else p.lineTo(x, y(v))
                }
            }
            drawPath(p, col, style = Stroke(3f))
        }
    }
}

@Composable fun App(vm: AppViewModel = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    val assets by vm.assets.collectAsState()
    val trades by vm.trades.collectAsState()
    val pos by vm.positions.collectAsState()
    val cash = vm.bal + trades.sumOf { it.pnl } - pos.sumOf { it.entry * it.qty }
    val names = listOf("Painel", "Backtest", "Simulador", "Histórico", "Ajustes")
    Scaffold(bottomBar = {
        NavigationBar { names.forEachIndexed { i, n ->
            NavigationBarItem(selected = tab == i, onClick = { tab = i; vm.message = "" }, icon = { Text("•") }, label = { Text(n, fontSize = 11.sp) })
        } }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("EISENBERG TRADER", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Simulação educacional. Nenhuma operação real, nenhuma corretora conectada. Não é recomendação de investimento.", fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                assets.forEachIndexed { i, a ->
                    if (i == vm.selected) Button({ vm.selected = i }) { Text(a.symbol) } else OutlinedButton({ vm.selected = i }) { Text(a.symbol) }
                }
            }
            val a = assets.getOrNull(vm.selected)
            if (tab == 4) SettingsTab(vm, assets)
            else if (a == null) Text("Carregando…")
            else when (tab) {
                0 -> Dashboard(a)
                1 -> BacktestTab(vm, a)
                2 -> SimTab(vm, a, pos, cash)
                else -> HistoryTab(vm, trades)
            }
        }
    }
}

@Composable fun Dashboard(a: Asset) {
    val i = a.closes.lastIndex
    if (i < 1) { Text("Dados insuficientes."); return }
    val an = remember(a) { Analysis(a.closes) }
    val s = an.signal(i); val (t, sl) = Trend.of(a.closes); val ch = (a.closes[i] / a.closes[i - 1] - 1) * 100
    val tx = MaterialTheme.colorScheme.onSurface
    Panel {
        Badge(a); Text("${a.symbol} · ${a.closes.size} candles", fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(a.closes[i].f(), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("${ch.f()}%", color = if (ch >= 0) Green else Red)
            Text(s.name, color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(sigColor(s), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp))
        }
        LineChart(listOf(a.closes to tx, an.s20 to Blue, an.s50 to Amber), height = 190)
        Text("Preço · SMA 20 (azul) · SMA 50 (âmbar)", fontSize = 12.sp)
    }
    Panel {
        Text("RSI 14: ${an.rsi[i].f()}"); Text("Tendência (20 candles): $t (${sl.f()}%)")
        Text("SMA 20 / SMA 50: ${an.s20[i].f()} / ${an.s50[i].f()}")
        Text("MACD / sinal: ${an.macd.line[i].f(3)} / ${an.macd.signal[i].f(3)}")
    }
    Panel {
        Text("RSI 14", fontSize = 12.sp); LineChart(listOf(an.rsi to Blue), 0.0, 100.0, 80)
        Text("MACD 12/26/9 (linha, sinal, histograma)", fontSize = 12.sp)
        LineChart(listOf(an.macd.line to Blue, an.macd.signal to Amber, an.macd.hist to Color.Gray), height = 100)
    }
}

@Composable fun BacktestTab(vm: AppViewModel, a: Asset) {
    val r = remember(a, vm.sl, vm.tp) { Backtest.run(a.closes, vm.sl, vm.tp) }
    Panel {
        Badge(a); Text("Backtest · ${a.symbol}", fontWeight = FontWeight.Bold)
        Text("Compra no sinal COMPRA; saída por VENDA, stop-loss ${vm.sl}% ou take-profit ${vm.tp}%. Só posições compradas, sem custos.", fontSize = 12.sp)
        Text("Operações: ${r.trades}"); Text("Taxa de acerto: ${r.winRate.f(1)}%")
        Text("Retorno total: ${r.returnPct.f()}%", color = if (r.returnPct >= 0) Green else Red)
        Text("Drawdown máximo: ${r.maxDrawdownPct.f()}%")
        Text("Fator de lucro: ${if (r.profitFactor.isInfinite()) "∞" else r.profitFactor.f()}")
        if (r.curve.isNotEmpty()) { LineChart(listOf(r.curve to Green), height = 150); Text("Curva de capital (base 1,0)", fontSize = 12.sp) }
    }
}

@Composable fun SimTab(vm: AppViewModel, a: Asset, pos: List<PositionEntity>, cash: Double) {
    val s = remember(a) { Analysis(a.closes).signal(a.closes.lastIndex) }
    Panel {
        Badge(a); Text("Saldo simulado: ${cash.f()}", fontWeight = FontWeight.Bold)
        Text("${a.symbol} a ${a.closes.last().f()} · risco ${vm.risk}% · SL ${vm.sl}% · TP ${vm.tp}% · sinal: ${s.name}", fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ vm.buy() }) { Text("Comprar (simulado)") }
            OutlinedButton({ vm.step() }) { Text("Avançar candle") }
        }
        if (vm.message.isNotEmpty()) Text(vm.message, fontSize = 13.sp)
    }
    Panel {
        Text("Posições abertas", fontWeight = FontWeight.Bold)
        if (pos.isEmpty()) Text("Nenhuma")
        pos.forEach { p ->
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("${p.symbol} @ ${p.entry.f()} · SL ${p.sl.f()} · TP ${p.tp.f()}", Modifier.weight(1f), fontSize = 13.sp)
                TextButton({ vm.closeManual(p) }) { Text("Fechar") }
            }
        }
    }
}

@Composable fun HistoryTab(vm: AppViewModel, trades: List<TradeEntity>) {
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    Panel {
        Text("Histórico de operações (total P/L: ${trades.sumOf { it.pnl }.f()})", fontWeight = FontWeight.Bold)
        if (trades.isEmpty()) Text("Sem operações")
        trades.forEach { t ->
            Text("${fmt.format(Date(t.time))} · ${t.symbol}${if (t.source == "SIM") " (sim)" else ""} · ${t.entry.f()} → ${t.exit.f()} · " +
                "P/L ${t.pnl.f()} · ${t.reason}", fontSize = 13.sp, color = if (t.pnl >= 0) Green else Red)
        }
        if (trades.isNotEmpty()) OutlinedButton({ vm.clearTrades() }) { Text("Limpar histórico") }
    }
}

@Composable fun SettingsTab(vm: AppViewModel, assets: List<Asset>) {
    var b by remember { mutableStateOf(vm.bal.toString()) }; var r by remember { mutableStateOf(vm.risk.toString()) }
    var s by remember { mutableStateOf(vm.sl.toString()) }; var t by remember { mutableStateOf(vm.tp.toString()) }
    var sym by remember { mutableStateOf("") }; var raw by remember { mutableStateOf("") }
    Panel {
        Text("Gestão de risco", fontWeight = FontWeight.Bold)
        OutlinedTextField(b, { b = it }, label = { Text("Saldo inicial simulado") }, singleLine = true)
        OutlinedTextField(r, { r = it }, label = { Text("Risco por operação (% do saldo)") }, singleLine = true)
        OutlinedTextField(s, { s = it }, label = { Text("Stop-loss (%)") }, singleLine = true)
        OutlinedTextField(t, { t = it }, label = { Text("Take-profit (%)") }, singleLine = true)
        Button({
            val v = listOf(b, r, s, t).map { it.replace(',', '.').toDoubleOrNull() }
            if (v.any { it == null || it <= 0 }) vm.message = "Valores inválidos." else vm.saveCfg(v[0]!!, v[1]!!, v[2]!!, v[3]!!)
        }) { Text("Salvar e zerar saldo simulado") }
        if (vm.message.isNotEmpty()) Text(vm.message, fontSize = 13.sp)
    }
    Panel {
        Text("Ativos", fontWeight = FontWeight.Bold)
        assets.forEach { a ->
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column { Text(a.symbol); Badge(a) }
                TextButton({ vm.delAsset(a.symbol) }) { Text("Remover") }
            }
        }
    }
    Panel {
        Text("Adicionar ativo", fontWeight = FontWeight.Bold)
        OutlinedTextField(sym, { sym = it }, label = { Text("Símbolo") }, singleLine = true)
        OutlinedTextField(raw, { raw = it }, label = { Text("Fechamentos reais (mín. 60; vazio = simulado)") }, minLines = 3)
        Button({ vm.addAsset(sym, raw) }) { Text("Adicionar") }
        Text("O app não busca cotações. Dados reais só entram se você os importar. MetaTrader 5 e APIs entram no futuro pela interface MarketDataProvider.", fontSize = 12.sp)
    }
}
