package no.trafonsjakk.game;

/** See the lifecycle diagram in docs/ARCHITECTURE.md §3.3. Only CONFIRMED games count. */
public enum GameStatus {
    PENDING,
    CONFIRMED,
    DISPUTED,
    VOIDED
}
