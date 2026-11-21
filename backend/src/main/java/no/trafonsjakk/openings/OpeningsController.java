package no.trafonsjakk.openings;

import java.time.Instant;
import java.util.List;
import no.trafonsjakk.analysis.MoveText;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.game.Game;
import no.trafonsjakk.game.GameRepository;
import no.trafonsjakk.game.GameResult;
import no.trafonsjakk.user.PlayerRef;
import no.trafonsjakk.user.User;
import no.trafonsjakk.user.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The opening explorer's data: confirmed games with their first moves and the opening name someone typed.
 * The website builds the move tree and finds the opening names itself (it already has chess.js and the
 * names), so this only sends what it needs: at most {@link #PLIES} half-moves per game.
 */
@RestController
public class OpeningsController {

    /** The explorer goes this deep: 15 moves each. */
    static final int PLIES = 30;

    public record OpeningGame(long id, PlayerRef white, PlayerRef black, GameResult result, Instant playedAt,
                              String opening, String moves) {
    }

    private final GameRepository games;
    private final UserRepository users;

    public OpeningsController(GameRepository games, UserRepository users) {
        this.games = games;
        this.users = users;
    }

    @GetMapping("/api/openings/games")
    @Transactional(readOnly = true)
    public List<OpeningGame> games(@RequestParam(required = false) String player) {
        Long id = null;
        if (player != null && !player.isBlank()) {
            id = users.findByUsernameIgnoreCase(player.trim()).map(User::getId)
                    .orElseThrow(() -> ApiException.notFound("player.not_found", "Player not found"));
        }
        final Long only = id;
        return games.findConfirmedWithOpenings().stream()
                .filter(g -> only == null || g.getWhite().getId() == only || g.getBlack().getId() == only)
                .map(OpeningsController::toDto)
                .toList();
    }

    static OpeningGame toDto(Game g) {
        List<String> tokens = MoveText.tokens(g.getMoves());
        String moves = tokens.isEmpty() ? null : String.join(" ", tokens.subList(0, Math.min(PLIES, tokens.size())));
        String opening = g.getOpening() == null || g.getOpening().isBlank() ? null : g.getOpening().trim();
        return new OpeningGame(g.getId(), PlayerRef.of(g.getWhite()), PlayerRef.of(g.getBlack()), g.getResult(), g.getPlayedAt(),
                opening, moves);
    }
}
