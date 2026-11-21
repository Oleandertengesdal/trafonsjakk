package no.trafonsjakk.rivalry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import no.trafonsjakk.analysis.AnalysisRepository;
import no.trafonsjakk.analysis.AnalysisService;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.game.Game;
import no.trafonsjakk.game.GameDtos.GameDto;
import no.trafonsjakk.game.GameMapper;
import no.trafonsjakk.game.GameRepository;
import no.trafonsjakk.user.PlayerRef;
import no.trafonsjakk.user.User;
import no.trafonsjakk.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Two players' rivalry: their record against each other, streaks, the biggest upset, their best games, all games. */
@RestController
public class RivalryController {

    public record RivalryDto(PlayerRef a, PlayerRef b, RivalryCalculator.Record overall, RivalryCalculator.Record aAsWhite,
                             RivalryCalculator.Record aAsBlack, RivalryCalculator.Streak currentStreak,
                             RivalryCalculator.Streak longestStreak, RivalryCalculator.Upset biggestUpset,
                             List<RivalryCalculator.BestGame> bestGames, List<GameDto> games) {
    }

    private final UserRepository users;
    private final GameRepository games;
    private final GameMapper mapper;
    private final AnalysisRepository analyses;
    private final JdbcTemplate jdbc;

    public RivalryController(UserRepository users, GameRepository games, GameMapper mapper, AnalysisRepository analyses, JdbcTemplate jdbc) {
        this.users = users;
        this.games = games;
        this.mapper = mapper;
        this.analyses = analyses;
        this.jdbc = jdbc;
    }

    /** Confirmed games only. {@code games} is newest first; the rest is from {@code a}'s side. */
    @GetMapping("/api/rivalry/{a}/{b}")
    @Transactional(readOnly = true)
    public RivalryDto rivalry(@PathVariable String a, @PathVariable String b, @AuthenticationPrincipal Jwt jwt) {
        User ua = user(a);
        User ub = user(b);
        if (ua.getId().equals(ub.getId())) {
            throw ApiException.badRequest("rivalry.same_player", "A rivalry needs two different players");
        }
        List<Game> between = games.findConfirmedBetween(ua.getId(), ub.getId());
        Map<String, Double> before = new HashMap<>();
        if (!between.isEmpty()) {
            jdbc.query("select game_id, user_id, rating_before from rating_history where user_id in (?, ?)",
                    rs -> {
                        before.put(rs.getLong("game_id") + ":" + rs.getLong("user_id"), rs.getDouble("rating_before"));
                    }, ua.getId(), ub.getId());
        }
        List<RivalryCalculator.Game> rows = between.stream().map(g -> {
            boolean aWhite = g.getWhite().getId().equals(ua.getId());
            Double aAcc = null;
            Double bAcc = null;
            var analysis = analyses.find(g.getId());
            if (analysis.isPresent() && analysis.get().plies() / 2 >= AnalysisService.MIN_MOVES) {
                aAcc = aWhite ? analysis.get().whiteAccuracy() : analysis.get().blackAccuracy();
                bAcc = aWhite ? analysis.get().blackAccuracy() : analysis.get().whiteAccuracy();
            }
            return new RivalryCalculator.Game(g.getId(), g.getPlayedAt(), aWhite, g.getResult(),
                    before.get(g.getId() + ":" + ua.getId()), before.get(g.getId() + ":" + ub.getId()), aAcc, bAcc);
        }).toList();
        RivalryCalculator.Result r = RivalryCalculator.compute(rows);
        List<GameDto> dtos = new java.util.ArrayList<>(mapper.toDtos(between, CurrentUser.of(jwt)));
        java.util.Collections.reverse(dtos);
        return new RivalryDto(PlayerRef.of(ua), PlayerRef.of(ub), r.overall(), r.aAsWhite(), r.aAsBlack(), r.current(),
                r.longest(), r.upset(), r.best(), dtos);
    }

    private User user(String username) {
        return users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> ApiException.notFound("player.not_found", "Player not found"));
    }
}
