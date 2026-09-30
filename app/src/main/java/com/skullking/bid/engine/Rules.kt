package com.skullking.bid.engine

import kotlin.math.abs

object Rules {

    /** Kind a card counts as when played. The Tigress is a pirate or a white (escape) at the player's choice. */
    fun effKind(card: Int, asPirate: Boolean): Int {
        val k = Deck.kind[card]
        return if (k == Deck.TIG) (if (asPirate) Deck.PIR else Deck.WHITE) else k
    }

    /**
     * Follow-suit rule. Only numbered cards are restricted: when a suit has been led you must play it if you
     * hold it. Black is an ordinary suit for this purpose (so it cannot be played on a coloured lead while you
     * still hold that colour). Everything else can always be played.
     */
    fun isLegal(card: Int, hand: IntArray, handLen: Int, leadSuit: Int): Boolean {
        if (Deck.kind[card] != Deck.NUM || leadSuit < 0) return true
        if (Deck.suit[card] == leadSuit) return true
        for (i in 0 until handLen) {
            val h = hand[i]
            if (Deck.kind[h] == Deck.NUM && Deck.suit[h] == leadSuit) return false
        }
        return true
    }

    /** Index (in play order) of the last Kraken/Moby Dick in the trick, or -1. The later one takes effect. */
    fun takerIndex(cards: IntArray, len: Int): Int {
        var t = -1
        for (i in 0 until len) {
            val k = Deck.kind[cards[i]]
            if (k == Deck.MOBY || k == Deck.KRAKEN) t = i
        }
        return t
    }

    /**
     * Winner of a trick as an index into [cards] (play order), or -1 when nobody wins.
     * [neutral] >= 0 makes that card behave as a white card (used to find who leads after a Kraken/Moby trick).
     *
     * 1. Kraken takes effect: nobody wins.
     * 2. Moby Dick takes effect: every figure counts as an escape; highest number of any suit wins
     *    (first played on equal rank); no number = nobody wins.
     * 3. Figures: mermaid+Skull King -> the mermaid wins; Skull King beats pirates; pirate beats mermaid;
     *    among equals the first played wins.
     * 4. Numbers: highest black wins, else highest card of the suit of the first numbered card played.
     * 5. Only escapes/loot: first loot wins, else the first card played.
     */
    fun resolve(cards: IntArray, pir: BooleanArray, len: Int, neutral: Int): Int {
        var taker = -1
        var takerKind = 0
        var fm = -1
        var fp = -1
        var fs = -1
        var fl = -1
        for (i in 0 until len) {
            val k = if (i == neutral) Deck.WHITE else effKind(cards[i], pir[i])
            if (k == Deck.MOBY || k == Deck.KRAKEN) {
                taker = i
                takerKind = k
            } else if (k == Deck.MERM) {
                if (fm < 0) fm = i
            } else if (k == Deck.PIR) {
                if (fp < 0) fp = i
            } else if (k == Deck.SKK) {
                if (fs < 0) fs = i
            } else if (k == Deck.LOOT) {
                if (fl < 0) fl = i
            }
        }
        if (taker >= 0) {
            if (takerKind == Deck.KRAKEN) return -1
            var best = -1
            var bv = -1
            for (i in 0 until len) {
                val c = cards[i]
                if (i != neutral && Deck.kind[c] == Deck.NUM && Deck.rank[c] > bv) {
                    bv = Deck.rank[c]
                    best = i
                }
            }
            return best
        }
        if (fs >= 0) return if (fm >= 0) fm else fs
        if (fp >= 0) return fp
        if (fm >= 0) return fm

        var bestBlack = -1
        var firstNum = -1
        for (i in 0 until len) {
            val c = cards[i]
            if (Deck.kind[c] == Deck.NUM) {
                if (firstNum < 0) firstNum = i
                if (Deck.suit[c] == Deck.BLACK && (bestBlack < 0 || Deck.rank[c] > Deck.rank[cards[bestBlack]])) {
                    bestBlack = i
                }
            }
        }
        if (bestBlack >= 0) return bestBlack
        if (firstNum >= 0) {
            val s = Deck.suit[cards[firstNum]]
            var b = firstNum
            for (i in 0 until len) {
                val c = cards[i]
                if (Deck.kind[c] == Deck.NUM && Deck.suit[c] == s && Deck.rank[c] > Deck.rank[cards[b]]) b = i
            }
            return b
        }
        return if (fl >= 0) fl else 0
    }

    /** Round score for one player. [round] is the round number (not the card count) for zero bids. */
    fun score(bid: Int, won: Int, round: Int): Int {
        if (bid == 0) return if (won == 0) 10 * round else -10 * round
        return if (won == bid) 20 * bid else -10 * abs(won - bid)
    }
}
