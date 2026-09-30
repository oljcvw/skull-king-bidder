package com.skullking.bid

import com.skullking.bid.engine.Deck
import com.skullking.bid.engine.SimConfig
import com.skullking.bid.engine.Simulator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the simulation against probabilities that can be computed by hand. */
class SimulatorTest {

    private fun simulate(players: Int, round: Int, seat: Int, hand: IntArray, samples: Int, seed: Long) =
        runBlocking { Simulator.run(SimConfig(players, round, seat, hand, samples, seed)) }

    /** 1 card, 4 players, holding the Skull King: loses only to a mermaid (2), Kraken or Moby among the 3 other cards
     *  drawn from 73 unseen -> C(69,3)/C(73,3) = 0.8424. */
    @Test fun skullKingRoundOne() {
        val res = simulate(4, 1, 0, intArrayOf(Deck.SK_ID), 40000, 7L)
        assertEquals(0.8424, res.dist[1][1], 0.01)
    }

    /** Holding the Kraken can never win a trick. */
    @Test fun krakenNeverWins() {
        val res = simulate(4, 1, 2, intArrayOf(Deck.KRAKEN_ID), 5000, 1L)
        assertEquals(1.0, res.dist[0][0], 1e-9)
        assertEquals(1.0, res.dist[1][0], 1e-9)
        assertEquals(0, res.best)
    }

    /** Black 14 leading against 2 opponents in round 1 wins about 75% (killers: figures, Kraken; tigress sometimes). */
    @Test fun blackFourteenLeading() {
        val res = simulate(3, 1, 0, intArrayOf(Deck.numbered(Deck.BLACK, 14)), 40000, 5L)
        assertEquals(0.75, res.dist[1][1], 0.02)
    }

    /** Alone at the table you win every trick. */
    @Test fun soloWinsEverything() {
        val hand = intArrayOf(Deck.SK_ID, Deck.PIR_BASE, Deck.numbered(Deck.BLACK, 10))
        val res = simulate(1, 3, 0, hand, 1000, 3L)
        assertEquals(1.0, res.dist[3][3], 1e-9)
        assertEquals(3, res.best)
    }

    /** A hand of escapes and low cards in round 8 should bid 0 (stake 80). */
    @Test fun junkHandBidsZero() {
        val hand = intArrayOf(
            Deck.WHITE_BASE, Deck.WHITE_BASE + 1, Deck.LOOT_BASE,
            Deck.numbered(0, 1), Deck.numbered(1, 1), Deck.numbered(2, 1), Deck.numbered(0, 2), Deck.numbered(1, 2)
        )
        val res = simulate(4, 8, 1, hand, 3000, 11L)
        assertEquals(0, res.best)
        assertTrue(res.ev[0] > 40.0)
    }

    /** Same seed -> identical results, whatever the thread scheduling. */
    @Test fun deterministicForSeed() {
        val hand = intArrayOf(Deck.SK_ID, Deck.numbered(0, 7), Deck.numbered(3, 5), Deck.WHITE_BASE)
        val a = simulate(5, 4, 2, hand, 2000, 99L)
        val b = simulate(5, 4, 2, hand, 2000, 99L)
        for (i in a.ev.indices) assertEquals(a.ev[i], b.ev[i], 0.0)
    }

    /** 8 players in round 10 are dealt 9 cards; the deck is exactly enough (8*9=72 <= 74). */
    @Test fun eightPlayersRoundTenRuns() {
        val hand = IntArray(9) { Deck.numbered(Deck.BLACK, 6 + it) }
        val res = simulate(8, 10, 7, hand, 500, 2L)
        assertEquals(9, res.cards)
        assertEquals(10, res.ev.size)
    }
}
