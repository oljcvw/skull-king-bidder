# Skull King bid calculator (Android 13+, Kotlin, Jetpack Compose)

Pick players (1-8), round (1-10), your playing order and your hand. The app simulates the round on-device
and shows the bid with the highest expected score, plus the expected score of every bid and the
distribution of tricks you end up winning.

## Build
* Android Studio (Koala or newer): open this folder, let it sync, run `app`.
* No Android Studio: push to GitHub; `.github/workflows/build-apk.yml` builds `app-debug.apk`
  (Actions -> run -> Artifacts). `gradlew` is not included; the workflow installs Gradle 8.7 itself.
* Engine checks: `gradle :app:testDebugUnitTest` (rule cases + simulation vs hand-computed probabilities).

## How it works (package `engine`)
* `Deck`      74 cards, ids 0..73.
* `Rules`     trick resolution, follow-suit rule, scoring.
* `Estimator` cheap analytic model that gives each simulated opponent a bid from its own hand only.
* `Policy`    "hit my bid" card play (win when tricks are still needed, dump/lose otherwise).
* `Simulator` deals opponents random hands from the unseen cards, plays the round once for every
              candidate bid 0..k on the same deal, averages the scores. Multi-threaded, deterministic per seed.
* Cards per player = min(round, 74 / players), so 8 players get 9 cards in round 10.

## Model assumptions
* A figure/Kraken/Moby lead sets no suit (followers play anything); after an escape lead the first number sets the suit.
* Kraken or Moby-without-number: nobody wins; the player who would have won leads next
* Opponents play sensible bid-hitting cards, do not count cards, and bid without seeing anything but their own hand.
