package com.skullking.bid

import com.skullking.bid.engine.Deck
import com.skullking.bid.engine.Rules
import com.skullking.bid.engine.SimConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class RulesTest {
    private fun n(suit: Int, rank: Int) = Pair(Deck.numbered(suit, rank), false)
    private val white = Pair(Deck.WHITE_BASE, false)
    private val loot = Pair(Deck.LOOT_BASE, false)
    private val mermaid = Pair(Deck.MERM_BASE, false)
    private val pirate = Pair(Deck.PIR_BASE, false)
    private val sk = Pair(Deck.SK_ID, false)
    private val moby = Pair(Deck.MOBY_ID, false)
    private val kraken = Pair(Deck.KRAKEN_ID, false)
    private val tigWhite = Pair(Deck.TIG_ID, false)
    private val tigPirate = Pair(Deck.TIG_ID, true)

    private fun win(vararg plays: Pair<Int, Boolean>): Int {
        val cards = IntArray(plays.size) { plays[it].first }
        val pir = BooleanArray(plays.size) { plays[it].second }
        return Rules.resolve(cards, pir, plays.size, -1)
    }

    @Test fun highestOfLeadSuitWins() = assertEquals(1, win(n(0, 5), n(0, 9), n(1, 14)))
    @Test fun blackTrumpsColour() = assertEquals(1, win(n(0, 5), n(3, 2), n(0, 14)))
    @Test fun firstNumberAfterEscapeSetsSuit() = assertEquals(1, win(white, n(1, 3), n(0, 9)))
    @Test fun lootBeatsWhite() = assertEquals(1, win(white, loot, white))
    @Test fun allWhiteFirstWins() = assertEquals(0, win(white, white))
    @Test fun mermaidBeatsSkullKingEvenWithPirate() = assertEquals(1, win(pirate, mermaid, sk))
    @Test fun skullKingBeatsPirate() = assertEquals(1, win(pirate, sk))
    @Test fun pirateBeatsMermaid() = assertEquals(1, win(mermaid, pirate))
    @Test fun firstMermaidWins() = assertEquals(0, win(mermaid, mermaid))
    @Test fun pirateBeatsBothMermaids() = assertEquals(2, win(mermaid, mermaid, pirate))
    @Test fun krakenVoidsTrick() = assertEquals(-1, win(n(0, 5), kraken, sk))
    @Test fun mobyMakesFiguresLose() = assertEquals(0, win(n(0, 5), moby, sk, n(2, 3)))
    @Test fun mobyWithoutNumbersNobodyWins() = assertEquals(-1, win(moby, sk, pirate))
    @Test fun tigressAsPirateWins() = assertEquals(1, win(n(0, 5), tigPirate, n(0, 3)))
    @Test fun tigressAsWhiteLoses() = assertEquals(0, win(n(0, 5), tigWhite, n(0, 3)))
    @Test fun krakenAndMobyNobody() = assertEquals(-1, win(moby, kraken))
    @Test fun laterOfKrakenAndMobyTakesEffect() = assertEquals(2, win(kraken, moby, n(0, 4)))

    @Test fun scoring() {
        assertEquals(60, Rules.score(3, 3, 7))
        assertEquals(-20, Rules.score(2, 4, 7))
        assertEquals(-10, Rules.score(3, 2, 7))
        assertEquals(70, Rules.score(0, 0, 7))
        assertEquals(-100, Rules.score(0, 1, 10))
        assertEquals(-90, Rules.score(0, 2, 9))
    }

    @Test fun cardsDealt() {
        assertEquals(9, SimConfig.cardsFor(8, 10))
        assertEquals(10, SimConfig.cardsFor(7, 10))
        assertEquals(8, SimConfig.cardsFor(8, 8))
        assertEquals(10, SimConfig.cardsFor(1, 10))
    }
}
