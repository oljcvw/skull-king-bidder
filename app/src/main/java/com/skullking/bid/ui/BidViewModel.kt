package com.skullking.bid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skullking.bid.engine.Deck
import com.skullking.bid.engine.SimConfig
import com.skullking.bid.engine.SimResult
import com.skullking.bid.engine.Simulator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val players: Int = 4,
    val round: Int = 5,
    val seat: Int = 1,                       // 1 = leads the first trick
    val numbered: Set<Int> = emptySet(),     // card ids 0..55
    val whites: Int = 0,
    val loots: Int = 0,
    val mermaids: Int = 0,
    val pirates: Int = 0,
    val sk: Boolean = false,
    val moby: Boolean = false,
    val kraken: Boolean = false,
    val tigress: Boolean = false,
    val samples: Int = 20000,
    val running: Boolean = false,
    val progress: Float = 0f,
    val result: SimResult? = null,
    val shownBid: Int = 0,
    val error: String? = null
) {
    val cards: Int get() = SimConfig.cardsFor(players, round)

    val selected: Int
        get() = numbered.size + whites + loots + mermaids + pirates +
            (if (sk) 1 else 0) + (if (moby) 1 else 0) + (if (kraken) 1 else 0) + (if (tigress) 1 else 0)

    fun handIds(): IntArray {
        val list = ArrayList<Int>()
        list.addAll(numbered)
        for (i in 0 until whites) list.add(Deck.WHITE_BASE + i)
        for (i in 0 until loots) list.add(Deck.LOOT_BASE + i)
        for (i in 0 until mermaids) list.add(Deck.MERM_BASE + i)
        for (i in 0 until pirates) list.add(Deck.PIR_BASE + i)
        if (sk) list.add(Deck.SK_ID)
        if (moby) list.add(Deck.MOBY_ID)
        if (kraken) list.add(Deck.KRAKEN_ID)
        if (tigress) list.add(Deck.TIG_ID)
        return list.toIntArray()
    }
}

class BidViewModel : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var job: Job? = null

    private fun edit(block: (UiState) -> UiState) {
        _state.update { block(it).copy(result = null, error = null) }
    }

    fun setPlayers(n: Int) = edit { it.copy(players = n, seat = minOf(it.seat, n)) }
    fun setRound(r: Int) = edit { it.copy(round = r) }
    fun setSeat(s: Int) = edit { it.copy(seat = s) }
    fun setSamples(n: Int) = _state.update { it.copy(samples = n) }

    fun toggleNumbered(id: Int) = edit {
        it.copy(numbered = if (id in it.numbered) it.numbered - id else it.numbered + id)
    }
    fun setWhites(v: Int) = edit { it.copy(whites = v.coerceIn(0, 5)) }
    fun setLoots(v: Int) = edit { it.copy(loots = v.coerceIn(0, 2)) }
    fun setMermaids(v: Int) = edit { it.copy(mermaids = v.coerceIn(0, 2)) }
    fun setPirates(v: Int) = edit { it.copy(pirates = v.coerceIn(0, 5)) }
    fun setSk(v: Boolean) = edit { it.copy(sk = v) }
    fun setMoby(v: Boolean) = edit { it.copy(moby = v) }
    fun setKraken(v: Boolean) = edit { it.copy(kraken = v) }
    fun setTigress(v: Boolean) = edit { it.copy(tigress = v) }

    fun clearHand() = edit {
        it.copy(
            numbered = emptySet(), whites = 0, loots = 0, mermaids = 0, pirates = 0,
            sk = false, moby = false, kraken = false, tigress = false
        )
    }

    fun showBid(b: Int) = _state.update { it.copy(shownBid = b) }

    fun cancel() {
        job?.cancel()
    }

    fun calculate() {
        val s = _state.value
        if (s.running) return
        val cfg = SimConfig(s.players, s.round, s.seat - 1, s.handIds(), s.samples, System.nanoTime())
        val problem = cfg.validate()
        if (problem != null) {
            _state.update { it.copy(error = problem) }
            return
        }
        job = viewModelScope.launch {
            _state.update { it.copy(running = true, progress = 0f, error = null, result = null) }
            try {
                val res = Simulator.run(cfg) { done ->
                    _state.update { st -> st.copy(progress = done.toFloat() / cfg.samples) }
                }
                _state.update { it.copy(result = res, shownBid = res.best, running = false, progress = 1f) }
            } catch (e: CancellationException) {
                _state.update { it.copy(running = false) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(running = false, error = e.message ?: "Simulation failed") }
            }
        }
    }
}
