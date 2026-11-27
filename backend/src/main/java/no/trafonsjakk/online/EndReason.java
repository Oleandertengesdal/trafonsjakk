package no.trafonsjakk.online;

/** Why an online game ended. */
public enum EndReason {
    CHECKMATE, STALEMATE, REPETITION, FIFTY_MOVES, INSUFFICIENT_MATERIAL,
    RESIGNATION, AGREEMENT,
    TIMEOUT,
    /** Out of time, but the opponent couldn't have mated: a draw. */
    TIMEOUT_VS_INSUFFICIENT
}
