package com.skullking.bid.engine

/**
 * Cheap analytic model used only to give the simulated OPPONENTS a bid (from their own hand alone,
 * as in the real game where bids are simultaneous).
 *
 * Each card gets a rough probability of winning a trick, assuming the other n-1 cards of a trick are a random
 * draw from the U = 74-k cards the holder cannot see. "absent(m)" = chance none of m specific killer cards
 * appears among those n-1 cards ~ ((U-m)/U)^(n-1). Tricks are treated as independent (Poisson-binomial) and the
 * opponent bids whatever maximises expected score under that distribution.
 */
object Estimator {

    private fun absent(m: Double, u: Int, opp: Int): Double {
        if (opp <= 0) return 1.0
        if (u <= m) return 0.0
        return Math.pow((u - m) / u, opp.toDouble())
    }

    /** Per-card win probability table for n players / k cards each (also used for choosing what to lead). */
    fun cardProbs(n: Int, k: Int): DoubleArray {
        val u = Deck.SIZE - k
        val opp = n - 1
        val voidP = Math.pow((u - 14.0) / u, k.toDouble())
        val intrude = voidP * (1.0 - voidP)          // an opponent is void in my suit AND holds a black card
        val absBlack = Math.pow(1.0 - intrude, opp.toDouble())
        val pPirate = absent(5.25, u, opp)           // Skull King, Kraken, Moby + half of the other 4.5 pirate-like cards
        val wp = DoubleArray(Deck.SIZE)
        for (c in 0 until Deck.SIZE) {
            when (Deck.kind[c]) {
                Deck.NUM -> {
                    val higher = 14 - Deck.rank[c]
                    val base = absent(9.5 + higher, u, opp) // figures (8.5) + Kraken (1) + higher cards of the suit
                    wp[c] = if (Deck.suit[c] == Deck.BLACK) base else base * absBlack
                }
                Deck.MERM -> wp[c] = absent(7.5, u, opp)     // pirates 5, tigress .5, Kraken, Moby
                Deck.PIR -> wp[c] = pPirate
                Deck.SKK -> wp[c] = absent(4.0, u, opp)      // 2 mermaids, Kraken, Moby
                Deck.TIG -> wp[c] = pPirate
                else -> wp[c] = 0.0
            }
        }
        return wp
    }

    /** Bid that maximises expected score for this hand under the analytic model. */
    fun estimateBid(hand: IntArray, handLen: Int, n: Int, k: Int, round: Int, wp: DoubleArray): Int {
        val pos = 1.0 / n + (1.0 - 1.0 / n) / 3.0   // chance a coloured card's suit gets led (or I lead it)
        val dist = DoubleArray(handLen + 2)
        dist[0] = 1.0
        var m = 0
        for (h in 0 until handLen) {
            val c = hand[h]
            var p = wp[c]
            if (Deck.kind[c] == Deck.NUM && Deck.suit[c] < Deck.BLACK) p *= pos
            if (Deck.kind[c] == Deck.TIG) p *= 0.6
            for (w in m + 1 downTo 1) dist[w] = dist[w] * (1.0 - p) + dist[w - 1] * p
            dist[0] = dist[0] * (1.0 - p)
            m++
        }
        var bestBid = 0
        var bestEv = -1e9
        for (b in 0..k) {
            var ev = 0.0
            for (w in 0..handLen) ev += dist[w] * Rules.score(b, w, round)
            if (ev > bestEv + 1e-9) {
                bestEv = ev
                bestBid = b
            }
        }
        return bestBid
    }
}
