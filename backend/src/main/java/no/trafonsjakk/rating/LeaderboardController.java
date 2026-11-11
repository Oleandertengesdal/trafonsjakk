package no.trafonsjakk.rating;

import java.util.List;
import no.trafonsjakk.game.AutoConfirmService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboard;
    private final AutoConfirmService autoConfirm;

    public LeaderboardController(LeaderboardService leaderboard, AutoConfirmService autoConfirm) {
        this.leaderboard = leaderboard;
        this.autoConfirm = autoConfirm;
    }

    @GetMapping
    List<LeaderboardService.Row> get() {
        autoConfirm.sweepIfDue();
        return leaderboard.leaderboard();
    }
}
