@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.skullking.bid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skullking.bid.engine.Deck
import com.skullking.bid.engine.SimResult

private val suitNames = listOf("Green", "Purple", "Yellow", "Black")
private val suitColors = listOf(Color(0xFF2E7D32), Color(0xFF6A1B9A), Color(0xFFB8860B), Color(0xFF212121))

@Composable
fun BidScreen(vm: BidViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    Scaffold { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Skull King bid calculator", style = MaterialTheme.typography.headlineSmall)
            SetupSection(s, vm)
            HandSection(s, vm)
            RunSection(s, vm)
            val res = s.result
            if (res != null) ResultSection(res, s, vm)
            Text(
                "Monte Carlo with sensible-play opponents (each aims at its own bid). Base scoring only, no bonus points. " +
                    "Colours are only labels; any three suits work.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ChipRow(title: String, values: IntRange, selected: Int, label: (Int) -> String, onPick: (Int) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (v in values) {
            FilterChip(selected = v == selected, onClick = { onPick(v) }, label = { Text(label(v)) })
        }
    }
}

@Composable
private fun SetupSection(s: UiState, vm: BidViewModel) {
    ChipRow("Players", 1..8, s.players, { it.toString() }, vm::setPlayers)
    ChipRow("Round", 1..10, s.round, { it.toString() }, vm::setRound)
    ChipRow(
        "Your playing order (1 = you lead the first trick)",
        1..s.players, s.seat, { if (it == 1) "1 (lead)" else it.toString() }, vm::setSeat
    )
    Text(
        "Cards dealt: ${s.cards}" + if (s.cards < s.round) "  (deck limit with ${s.players} players)" else "",
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun Tile(label: String, selected: Boolean, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (selected) tint else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun CounterRow(label: String, value: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange(value - 1) }, enabled = value > 0) { Text("-") }
        Text(value.toString(), modifier = Modifier.padding(horizontal = 16.dp))
        OutlinedButton(onClick = { onChange(value + 1) }, enabled = value < max) { Text("+") }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun HandSection(s: UiState, vm: BidViewModel) {
    Text(
        "Your hand: ${s.selected} / ${s.cards} cards",
        style = MaterialTheme.typography.titleMedium
    )
    for (suit in 0..3) {
        Text(suitNames[suit], style = MaterialTheme.typography.titleSmall)
        for (row in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (col in 0 until 7) {
                    val rank = row * 7 + col + 1
                    val id = Deck.numbered(suit, rank)
                    Tile(rank.toString(), id in s.numbered, suitColors[suit], Modifier.weight(1f)) {
                        vm.toggleNumbered(id)
                    }
                }
            }
        }
    }
    Text("Special cards", style = MaterialTheme.typography.titleSmall)
    CounterRow("White (escape)", s.whites, 5, vm::setWhites)
    CounterRow("Loot", s.loots, 2, vm::setLoots)
    CounterRow("Mermaid", s.mermaids, 2, vm::setMermaids)
    CounterRow("Pirate", s.pirates, 5, vm::setPirates)
    ToggleRow("Skull King", s.sk, vm::setSk)
    ToggleRow("Moby Dick", s.moby, vm::setMoby)
    ToggleRow("Kraken", s.kraken, vm::setKraken)
    ToggleRow("Tigress", s.tigress, vm::setTigress)
    OutlinedButton(onClick = { vm.clearHand() }) { Text("Clear hand") }
}

@Composable
private fun RunSection(s: UiState, vm: BidViewModel) {
    Text("Simulated deals", style = MaterialTheme.typography.titleSmall)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (n in listOf(5000, 20000, 50000)) {
            FilterChip(
                selected = s.samples == n,
                onClick = { vm.setSamples(n) },
                enabled = !s.running,
                label = { Text(n.toString()) }
            )
        }
    }
    if (s.running) {
        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { vm.cancel() }) { Text("Cancel") }
    } else {
        Button(onClick = { vm.calculate() }, enabled = s.selected == s.cards, modifier = Modifier.fillMaxWidth()) {
            Text("Calculate optimal bid")
        }
    }
    val err = s.error
    if (err != null) Text(err, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun Bar(frac: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.15f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(frac.coerceIn(0f, 1f))
                .height(10.dp)
                .background(color)
        )
    }
}

private fun fmt(x: Double): String = "%.1f".format(x)
private fun pct(x: Double): String = "%.0f%%".format(x * 100.0)

@Composable
private fun ResultSection(res: SimResult, s: UiState, vm: BidViewModel) {
    val b = res.best
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Best bid: $b", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Expected score ${fmt(res.ev[b])} (+/- ${fmt(res.evSe[b])}), chance to hit exactly ${pct(res.dist[b][b])}")
            if (res.closeCall) {
                Text(
                    "Close call: bid ${res.runnerUp} is within noise of bid $b " +
                        "(gap ${fmt(res.gap)} +/- ${fmt(res.gapSe)}). Run more deals or use your read of the table.",
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Text("${res.samples} deals", style = MaterialTheme.typography.bodySmall)
        }
    }

    Text("Expected score per bid (tap a row for its win distribution)", style = MaterialTheme.typography.titleSmall)
    var maxAbs = 1.0
    for (v in res.ev) if (kotlin.math.abs(v) > maxAbs) maxAbs = kotlin.math.abs(v)
    for (bid in 0..res.cards) {
        val ev = res.ev[bid]
        val color = if (ev >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { vm.showBid(bid) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Bid $bid",
                modifier = Modifier.width(56.dp),
                fontWeight = if (bid == b) FontWeight.Bold else FontWeight.Normal
            )
            Bar((kotlin.math.abs(ev) / maxAbs).toFloat(), color, Modifier.weight(1f))
            Text(fmt(ev), modifier = Modifier.width(64.dp).padding(start = 8.dp))
        }
    }

    val shown = s.shownBid.coerceIn(0, res.cards)
    Text("Tricks won when playing for a bid of $shown", style = MaterialTheme.typography.titleSmall)
    for (w in 0..res.cards) {
        val p = res.dist[shown][w]
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$w",
                modifier = Modifier.width(28.dp),
                fontWeight = if (w == shown) FontWeight.Bold else FontWeight.Normal
            )
            Bar(p.toFloat(), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            Text(pct(p), modifier = Modifier.width(56.dp).padding(start = 8.dp))
        }
    }
}
