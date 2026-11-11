package no.trafonsjakk.tournament;

/**
 * WAITING: players not yet known (later knockout rounds). READY: playable.
 * COMPLETED: decided. BYE: one player advanced without playing.
 */
public enum MatchStatus {
    WAITING,
    READY,
    COMPLETED,
    BYE
}
