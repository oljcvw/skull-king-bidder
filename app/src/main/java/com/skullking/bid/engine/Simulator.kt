package com.skullking.bid.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * @param players 1..8
 * @param round   1..10 (used for the zero-bid stake)
 * @param seat    0-based position in play order for the FIRST trick (0 = you lead trick 1)
 * @param hand    your cards (ids from [Deck]); size must equal [cardsFor]
 */
class SimConfig(
    val players: Int,
    val round: Int,
    val seat: Int,
    val hand: IntArray,
    val samples: Int,
    val seed: Long
) {
    val cards: Int get() = cardsFor(players, round)

    /** null if the setup is valid, otherwise a message. */
    fun validate(): String? {
        if (players !in 1..8) return "Players must be 1 to 8."
        if (round !in 1..10) return "Round must be 1 to 10."
        if (seat !in 0 until players) return "Seat must be 1 to $players."
        if (hand.size != cards) return "Select exactly $cards cards (you have ${hand.size})."
        if (hand.toSet().size != hand.size) return "Duplicate card in hand."
        if (hand.any { it < 0 || it >= Deck.SIZE }) return "Unknown card."
        if (samples < 100) return "Too few samples."
        return null
    }

    companion object {
        /** Cards dealt per player: the round number, capped so that players*cards <= 74 (8 players -> 9 in round 10). */
        fun cardsFor(players: Int, round: Int): Int = minOf(round, Deck.SIZE / players)
    }
}

/**
 * @param ev      expected score per candidate bid 0..cards
 * @param evSe    Monte Carlo standard error of each ev
 * @param dist    dist[b][w] = P(you win exactly w tricks | you play to hit bid b)
 * @param gap     ev[best] - ev[runnerUp];  gapSe = paired standard error of that difference
 */
class SimResult(
    val cards: Int,
    val samples: Int,
    val ev: DoubleArray,
    val evSe: DoubleArray,
    val dist: Array<DoubleArray>,
    val best: Int,
    val runnerUp: Int,
    val gap: Double,
    val gapSe: Double
) {
    /** True when the best and second-best bids are not separated by more than ~2 standard errors. */
    val closeCall: Boolean get() = runnerUp >= 0 && gap < 2.0 * gapSe
}

/** Plays one full round with fixed hands and bids. Not thread-safe: one instance per worker. */
class RoundEngine(private val n: Int, private val k: Int, private val policy: Policy) {
    private val hands = Array(n) { IntArray(k) }
    private val len = IntArray(n)
    private val cards = IntArray(n)
    private val pir = BooleanArray(n)
    private val players = IntArray(n)

    /** [dealt] hands in play order for trick 1 (player 0 leads). Fills [won] with tricks won per player. */
    fun playRound(dealt: Array<IntArray>, bids: IntArray, won: IntArray) {
        for (p in 0 until n) {
            System.arraycopy(dealt[p], 0, hands[p], 0, k)
            len[p] = k
            won[p] = 0
        }
        var leader = 0
        for (t in 0 until k) {
            var leadSuit = -1
            var escapeLed = false
            for (j in 0 until n) {
                val p = (leader + j) % n
                val enc = policy.choose(hands[p], len[p], cards, pir, j, leadSuit, bids[p] - won[p], j == 0)
                val c = enc shr 1
                val f = (enc and 1) == 1
                // remove card c from hand p, keeping order
                val h = hands[p]
                var idx = 0
                while (h[idx] != c) idx++
                for (x in idx until len[p] - 1) h[x] = h[x + 1]
                len[p]--
                cards[j] = c
                pir[j] = f
                players[j] = p
                val e = Rules.effKind(c, f)
                if (j == 0) {
                    if (e == Deck.NUM) leadSuit = Deck.suit[c]
                    else if (e == Deck.WHITE || e == Deck.LOOT) escapeLed = true
                    // a figure, Kraken or Moby lead sets no suit: everyone may play anything
                } else if (leadSuit < 0 && escapeLed && e == Deck.NUM) {
                    leadSuit = Deck.suit[c] // first number after an escape lead sets the suit
                }
            }
            val w = Rules.resolve(cards, pir, n, -1)
            if (w >= 0) {
                won[players[w]]++
                leader = players[w]
            } else {
                // Kraken / Moby-without-number: nobody wins; the player who would have won leads next
                val tk = Rules.takerIndex(cards, n)
                var w2 = Rules.resolve(cards, pir, n, tk)
                if (w2 < 0) w2 = 0
                leader = players[w2]
            }
        }
    }
}

object Simulator {
    private const val BLOCK = 200

    /**
     * Monte Carlo: each sample deals random opponent hands from the unseen cards, gives opponents a bid from their own
     * hand, then plays the round once per candidate bid 0..k for you (same deal for every bid = common random numbers).
     * Results are deterministic for a given [SimConfig.seed] regardless of the number of threads.
     */
    suspend fun run(cfg: SimConfig, onProgress: (Int) -> Unit = {}): SimResult = coroutineScope {
        cfg.validate()?.let { throw IllegalArgumentException(it) }
        val n = cfg.players
        val k = cfg.cards
        val total = cfg.samples
        val wp = Estimator.cardProbs(n, k)
        val mine = cfg.hand.copyOf()
        mine.sort()

        val inHand = BooleanArray(Deck.SIZE)
        for (c in mine) inHand[c] = true
        val baseUnseen = IntArray(Deck.SIZE - k)
        var fill = 0
        for (c in 0 until Deck.SIZE) {
            if (!inHand[c]) {
                baseUnseen[fill] = c
                fill++
            }
        }

        val wins = Array(k + 1) { ByteArray(total) }
        val nBlocks = (total + BLOCK - 1) / BLOCK
        val nextBlock = AtomicInteger(0)
        val doneCount = AtomicInteger(0)
        val workers = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)

        val jobs = (0 until workers).map {
            async(Dispatchers.Default) {
                worker(cfg, n, k, wp, mine, baseUnseen, wins, nBlocks, nextBlock, doneCount, onProgress)
            }
        }
        jobs.awaitAll()

        aggregate(cfg, k, wins)
    }

    private fun CoroutineScope.worker(
        cfg: SimConfig, n: Int, k: Int, wp: DoubleArray, mine: IntArray, baseUnseen: IntArray,
        wins: Array<ByteArray>, nBlocks: Int, nextBlock: AtomicInteger, doneCount: AtomicInteger,
        onProgress: (Int) -> Unit
    ) {
        val engine = RoundEngine(n, k, Policy(wp))
        val unseen = IntArray(baseUnseen.size)
        val hands = Array(n) { IntArray(k) }
        val bids = IntArray(n)
        val won = IntArray(n)
        val dealCount = (n - 1) * k
        while (true) {
            ensureActive()
            val block = nextBlock.getAndIncrement()
            if (block >= nBlocks) break
            val rng = Random(cfg.seed * 1_000_003L + block)
            System.arraycopy(baseUnseen, 0, unseen, 0, baseUnseen.size)
            val from = block * BLOCK
            val to = minOf(cfg.samples, from + BLOCK)
            for (s in from until to) {
                // partial Fisher-Yates: the first (n-1)*k entries become the opponents' cards
                for (x in 0 until dealCount) {
                    val j = x + rng.nextInt(unseen.size - x)
                    val tmp = unseen[x]
                    unseen[x] = unseen[j]
                    unseen[j] = tmp
                }
                var slot = 0
                for (p in 0 until n) {
                    if (p == cfg.seat) {
                        System.arraycopy(mine, 0, hands[p], 0, k)
                    } else {
                        System.arraycopy(unseen, slot * k, hands[p], 0, k)
                        hands[p].sort()
                        slot++
                    }
                }
                for (p in 0 until n) {
                    if (p != cfg.seat) bids[p] = Estimator.estimateBid(hands[p], k, n, k, cfg.round, wp)
                }
                for (bid in 0..k) {
                    bids[cfg.seat] = bid
                    engine.playRound(hands, bids, won)
                    wins[bid][s] = won[cfg.seat].toByte()
                }
            }
            onProgress(doneCount.addAndGet(to - from))
        }
    }

    private fun aggregate(cfg: SimConfig, k: Int, wins: Array<ByteArray>): SimResult {
        val total = cfg.samples
        val ev = DoubleArray(k + 1)
        val se = DoubleArray(k + 1)
        val dist = Array(k + 1) { DoubleArray(k + 1) }
        for (b in 0..k) {
            var sum = 0.0
            var sq = 0.0
            val arr = wins[b]
            for (i in 0 until total) {
                val w = arr[i].toInt()
                dist[b][w] += 1.0
                val sc = Rules.score(b, w, cfg.round).toDouble()
                sum += sc
                sq += sc * sc
            }
            val mean = sum / total
            ev[b] = mean
            se[b] = sqrt(max(0.0, sq / total - mean * mean) / total)
            for (w in 0..k) dist[b][w] = dist[b][w] / total
        }
        var best = 0
        for (b in 1..k) if (ev[b] > ev[best]) best = b
        var runner = -1
        for (b in 0..k) if (b != best && (runner < 0 || ev[b] > ev[runner])) runner = b
        var gap = 0.0
        var gapSe = 0.0
        if (runner >= 0) {
            gap = ev[best] - ev[runner]
            var sd = 0.0
            var sdq = 0.0
            for (i in 0 until total) {
                val a = Rules.score(best, wins[best][i].toInt(), cfg.round)
                val c = Rules.score(runner, wins[runner][i].toInt(), cfg.round)
                val d = (a - c).toDouble()
                sd += d
                sdq += d * d
            }
            val m = sd / total
            gapSe = sqrt(max(0.0, sdq / total - m * m) / total)
        }
        return SimResult(k, total, ev, se, dist, best, runner, gap, gapSe)
    }
}
