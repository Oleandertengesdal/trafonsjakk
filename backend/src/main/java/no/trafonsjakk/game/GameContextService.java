package no.trafonsjakk.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.rating.Glicko2;
import no.trafonsjakk.rating.RatingHistory;
import no.trafonsjakk.rating.RatingHistoryRepository;
import no.trafonsjakk.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The "before the game" numbers on a game page: expected score, upset, and the two players' head-to-head. */
@Service
public class GameContextService {

    /** The winner was expected to score less than this: an upset. */
    static final double UPSET_BELOW = 0.35;
    static final int RECENT = 5;

    /** Expected score (0–1) for each side, from the ratings just before the game. */
    public record Expected(double white, double black) {
    }

    /** All confirmed games between the two, counted from this game's sides (whiteWins = wins by this game's white player). */
    public record HeadToHead(int games, int whiteWins, int draws, int blackWins, Instant firstPlayedAt) {
    }

    public record Meeting(long id, Instant playedAt, long whiteId, GameResult result) {
    }

    /** {@code meeting}: this is their n-th game (null until it's confirmed). */
    public record GameContext(Expected expected, boolean upset, Integer meeting, HeadToHead headToHead, List<Meeting> recent) {
    }

    private final GameRepository games;
    private final RatingHistoryRepository history;

    public GameContextService(GameRepository games, RatingHistoryRepository history) {
        this.games = games;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public GameContext context(long gameId) {
        Game g = games.findById(gameId).orElseThrow(() -> ApiException.notFound("game.not_found", "Game not found"));
        long whiteId = g.getWhite().getId();
        long blackId = g.getBlack().getId();

        Expected expected = expected(g);
        boolean confirmed = g.getStatus() == GameStatus.CONFIRMED;
        double winnerExpected = switch (g.getResult()) {
            case WHITE_WIN -> expected.white();
            case BLACK_WIN -> expected.black();
            case DRAW -> 1;
        };
        boolean upset = confirmed && winnerExpected < UPSET_BELOW;

        List<Game> between = games.findConfirmedBetween(whiteId, blackId);
        int whiteWins = 0;
        int draws = 0;
        int blackWins = 0;
        for (Game other : between) {
            if (other.getResult() == GameResult.DRAW) {
                draws++;
            } else {
                long winner = other.getResult() == GameResult.WHITE_WIN ? other.getWhite().getId() : other.getBlack().getId();
                if (winner == whiteId) {
                    whiteWins++;
                } else {
                    blackWins++;
                }
            }
        }
        Integer meeting = null;
        if (confirmed) {
            for (int i = 0; i < between.size(); i++) {
                if (between.get(i).getId().equals(g.getId())) {
                    meeting = i + 1;
                }
            }
        }
        List<Meeting> recent = new ArrayList<>();
        between.stream()
                .filter(other -> !other.getId().equals(g.getId()))
                .sorted(Comparator.comparing(Game::getPlayedAt).thenComparing(Game::getId).reversed())
                .limit(RECENT)
                .forEach(o -> recent.add(new Meeting(o.getId(), o.getPlayedAt(), o.getWhite().getId(), o.getResult())));

        HeadToHead h2h = new HeadToHead(between.size(), whiteWins, draws, blackWins,
                between.isEmpty() ? null : between.getFirst().getPlayedAt());
        return new GameContext(expected, upset, meeting, h2h, recent);
    }

    /** From the rating history for a confirmed game (the ratings it was played at); otherwise current ratings. */
    private Expected expected(Game g) {
        Glicko2.Rating white = current(g.getWhite());
        Glicko2.Rating black = current(g.getBlack());
        if (g.getStatus() == GameStatus.CONFIRMED) {
            for (RatingHistory h : history.findByGameIdIn(List.of(g.getId()))) {
                Glicko2.Rating before = new Glicko2.Rating(h.getRatingBefore(), h.getRdBefore(), Glicko2.DEFAULT_VOLATILITY);
                if (h.getUserId().equals(g.getWhite().getId())) {
                    white = before;
                } else if (h.getUserId().equals(g.getBlack().getId())) {
                    black = before;
                }
            }
        }
        double e = Glicko2.expectedScore(white, black);
        return new Expected(e, 1 - e);
    }

    private static Glicko2.Rating current(User u) {
        return new Glicko2.Rating(u.getRating(), u.getRatingDeviation(), u.getVolatility());
    }
}
