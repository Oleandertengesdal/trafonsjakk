package no.trafonsjakk.game;

public enum GameResult {
    WHITE_WIN(1.0),
    BLACK_WIN(0.0),
    DRAW(0.5);

    private final double whiteScore;

    GameResult(double whiteScore) {
        this.whiteScore = whiteScore;
    }

    public double whiteScore() {
        return whiteScore;
    }

    public boolean isDecisive() {
        return this != DRAW;
    }
}
