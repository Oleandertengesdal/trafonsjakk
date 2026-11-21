package no.trafonsjakk.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import no.trafonsjakk.analysis.GameAccuracy.Score;
import org.junit.jupiter.api.Test;

class GameAccuracyTest {

    /** The same game and scores as run through the website's reviewGame(), with its answers. */
    @Test
    void matchesTheWebsiteExactly() {
        int[] cps = {20, 30, 25, 60, 40, -210, 100, 80, 120, 150, 140, 160, 200, 180, 250, 300, 280, 400, 350, 600, 550,
                700, 650, 900, 1500, 1400, 2000, 1800, 2500, 3000};
        List<Score> scores = new ArrayList<>();
        for (int cp : cps) {
            scores.add(Score.cp(cp));
        }
        for (int mate = 3; mate >= 0; mate--) {
            scores.add(Score.mate(mate, 'w'));
        }
        GameAccuracy.Result r = GameAccuracy.compute(scores);
        assertThat(r.white().moves()).isEqualTo(17);
        assertThat(r.black().moves()).isEqualTo(16);
        assertThat(r.white().accuracy()).isCloseTo(87.21023569222663, within(1e-9));
        assertThat(r.black().accuracy()).isCloseTo(78.94367476153106, within(1e-9));
    }

    @Test
    void aGameWithoutMovesIsPerfectForBoth() {
        GameAccuracy.Result r = GameAccuracy.compute(List.of(Score.cp(20)));
        assertThat(r.white()).isEqualTo(new GameAccuracy.Side(0, 100));
        assertThat(r.black()).isEqualTo(new GameAccuracy.Side(0, 100));
    }

    @Test
    void readsMoveTextLikeTheWebsite() {
        assertThat(MoveText.tokens("[Event \"x\"]\n1.e4 e5 {good} 2. Nf3!? (2. f4 exf4) Nc6 $1 3... a6?? 1-0"))
                .containsExactly("e4", "e5", "Nf3", "Nc6", "a6");
        assertThat(MoveText.plies("1. e4 e5 2. Nf3 ; a comment\n Nc6")).isEqualTo(4);
        assertThat(MoveText.plies(null)).isZero();
        assertThat(MoveText.plies("  ")).isZero();
    }
}
