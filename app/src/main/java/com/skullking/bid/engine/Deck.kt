package com.skullking.bid.engine

/**
 * 74 physical cards, ids 0..73:
 *  0..41  three coloured suits (suit = id/14 in 0..2, rank = id%14+1)
 * 42..55  black suit (suit 3)
 * 56..60  white (escape) x5      61..62 loot x2      63..64 mermaid x2
 * 65..69  pirate x5              70 Skull King       71 Moby Dick
 * 72 Kraken                      73 Tigress
 */
object Deck {
    const val SIZE = 74

    // card kinds
    const val NUM = 0
    const val WHITE = 1
    const val LOOT = 2
    const val MERM = 3
    const val PIR = 4
    const val SKK = 5
    const val MOBY = 6
    const val KRAKEN = 7
    const val TIG = 8

    const val BLACK = 3
    const val WHITE_BASE = 56
    const val LOOT_BASE = 61
    const val MERM_BASE = 63
    const val PIR_BASE = 65
    const val SK_ID = 70
    const val MOBY_ID = 71
    const val KRAKEN_ID = 72
    const val TIG_ID = 73

    val kind = IntArray(SIZE)
    val suit = IntArray(SIZE)
    val rank = IntArray(SIZE)

    init {
        for (i in 0 until SIZE) {
            suit[i] = -1
            when {
                i < 56 -> {
                    kind[i] = NUM
                    suit[i] = i / 14
                    rank[i] = i % 14 + 1
                }
                i < LOOT_BASE -> kind[i] = WHITE
                i < MERM_BASE -> kind[i] = LOOT
                i < PIR_BASE -> kind[i] = MERM
                i < SK_ID -> kind[i] = PIR
                i == SK_ID -> kind[i] = SKK
                i == MOBY_ID -> kind[i] = MOBY
                i == KRAKEN_ID -> kind[i] = KRAKEN
                else -> kind[i] = TIG
            }
        }
    }

    /** id of a numbered card. suit 0..2 coloured, 3 black; rank 1..14. */
    fun numbered(suit: Int, rank: Int): Int = suit * 14 + rank - 1
}
