package com.skullking.bid.engine

/**
 * "Hit my bid" card-play policy used by every simulated player (including you, with the bid being tested).
 *
 * need = bid - tricks won so far. need > 0 -> try to win; need <= 0 -> try to lose.
 *
 * Leading (choose the suit/card):
 *   want win  : play the card with the highest win probability (table from [Estimator.cardProbs]), cheapest on ties.
 *   want lose : play the card with the lowest win probability; among sure losers use white, loot, Moby, Kraken in turn.
 * Following (uses the cards already on the table):
 *   want win  : cheapest legal card that currently wins; if none, dump the least useful loser (low numbers first,
 *               then escapes, figures last).
 *   want lose : most dangerous legal card that currently loses (gets rid of high cards safely); if every card wins,
 *               the weakest winner.
 * The Tigress is offered both as a white (escape) and as a pirate.
 *
 * Return value encodes the choice: cardId * 2 + (1 if the Tigress is played as a pirate).
 */
class Policy(private val wp: DoubleArray) {

    fun choose(
        hand: IntArray, handLen: Int,
        cards: IntArray, pir: BooleanArray, pos: Int,
        leadSuit: Int, need: Int, isLeader: Boolean
    ): Int {
        val wantWin = need > 0
        if (isLeader) return chooseLead(hand, handLen, wantWin)

        var bestWin = -1
        var bestWinD = 0.0
        var bestLoseDump = -1
        var bestLoseDumpK = 0.0
        var bestLoseHi = -1
        var bestLoseHiD = 0.0
        for (h in 0 until handLen) {
            val c = hand[h]
            if (!Rules.isLegal(c, hand, handLen, leadSuit)) continue
            val variants = if (Deck.kind[c] == Deck.TIG) 2 else 1
            for (v in 0 until variants) {
                val f = v == 1
                cards[pos] = c
                pir[pos] = f
                val r = Rules.resolve(cards, pir, pos + 1, -1)
                val enc = c * 2 + v
                val d = danger(c, f)
                if (r == pos) {
                    if (bestWin < 0 || d < bestWinD) {
                        bestWin = enc
                        bestWinD = d
                    }
                } else {
                    val dk = dumpKey(c, f)
                    if (bestLoseDump < 0 || dk < bestLoseDumpK) {
                        bestLoseDump = enc
                        bestLoseDumpK = dk
                    }
                    if (bestLoseHi < 0 || d > bestLoseHiD) {
                        bestLoseHi = enc
                        bestLoseHiD = d
                    }
                }
            }
        }
        return if (wantWin) {
            if (bestWin >= 0) bestWin else bestLoseDump
        } else {
            if (bestLoseHi >= 0) bestLoseHi else bestWin
        }
    }

    private fun chooseLead(hand: IntArray, handLen: Int, wantWin: Boolean): Int {
        var best = -1
        var bw = 0.0
        var bt = 0
        var bd = 0.0
        for (h in 0 until handLen) {
            val c = hand[h]
            val variants = if (Deck.kind[c] == Deck.TIG) 2 else 1
            for (v in 0 until variants) {
                val f = v == 1
                val w = if (Deck.kind[c] == Deck.TIG) (if (f) wp[c] else 0.0) else wp[c]
                val d = danger(c, f)
                val t = toolOf(Rules.effKind(c, f))
                val better = if (best < 0) {
                    true
                } else if (wantWin) {
                    w > bw || (w == bw && d < bd)
                } else {
                    w < bw || (w == bw && (t < bt || (t == bt && d < bd)))
                }
                if (better) {
                    best = c * 2 + v
                    bw = w
                    bt = t
                    bd = d
                }
            }
        }
        return best
    }

    private fun toolOf(effKind: Int): Int = when (effKind) {
        Deck.WHITE -> 0
        Deck.LOOT -> 1
        Deck.MOBY -> 3
        Deck.KRAKEN -> 4
        else -> 9
    }

    /** How much winning potential a card carries; used to decide which card to spend or keep. */
    private fun danger(c: Int, f: Boolean): Double {
        val k = Rules.effKind(c, f)
        if (k == Deck.NUM) {
            return if (Deck.suit[c] < Deck.BLACK) Deck.rank[c].toDouble() else 15.0 + Deck.rank[c]
        }
        if (Deck.kind[c] == Deck.TIG) return if (f) 45.0 else 0.5
        return when (k) {
            Deck.WHITE -> 0.0
            Deck.LOOT -> 1.0
            Deck.MOBY -> 2.0
            Deck.KRAKEN -> 3.0
            Deck.MERM -> 35.0
            Deck.PIR -> 40.0
            Deck.SKK -> 50.0
            else -> 0.0
        }
    }

    /** Order in which to throw away a card that cannot win: low numbers, then escape-type cards, then figures. */
    private fun dumpKey(c: Int, f: Boolean): Double {
        val k = Rules.effKind(c, f)
        val d = danger(c, f)
        return when (k) {
            Deck.NUM -> d
            Deck.WHITE, Deck.LOOT, Deck.MOBY, Deck.KRAKEN -> 100.0 + d
            else -> 200.0 + d
        }
    }
}
