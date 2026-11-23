package no.trafonsjakk.analysis;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import no.trafonsjakk.analysis.AnalysisRepository.GameRow;
import no.trafonsjakk.analysis.GameAccuracy.Score;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.game.Game;
import no.trafonsjakk.game.GameRepository;
import no.trafonsjakk.user.PlayerRef;
import no.trafonsjakk.user.User;
import no.trafonsjakk.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Engine analysis kept on the server. Stockfish runs in the browser; the first browser to review a game
 * saves the evaluation of every position here, and everyone after that gets it straight away. The server
 * checks the shape of what it's sent and computes the accuracies itself; it can't re-run the engine, so it
 * trusts the evaluations (see SECURITY.md). Statistics only use confirmed games, and only players with at
 * least {@link #MIN_MOVES} moves in a game.
 */
@Service
public class AnalysisService {

    /** Fewer moves than this and one slip decides everything: left out of averages, awards and calibration. */
    public static final int MIN_MOVES = 10;
    /** "Played like" is fitted to the group's own ratings from this many player-games. */
    public static final int CALIBRATION_MIN = 30;
    static final int RECENT = 30;
    private static final int MAX_CP = 100_000;
    private static final int MAX_MATE = 500;
    private static final Pattern UCI = Pattern.compile("^[a-h][1-8][a-h][1-8][qrbn]?$");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** One position: a score from White's side ({@code cp}, or {@code mate} moves for {@code by}) and the engine's move. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScoreDto(Integer cp, Integer mate, String by) {
    }

    public record EvalDto(ScoreDto score, String best) {
    }

    public record SaveRequest(Integer depth, List<EvalDto> evals) {
    }

    public record SideDto(int moves, double accuracy) {
    }

    public record AnalysisDto(long gameId, int depth, List<EvalDto> evals, SideDto white, SideDto black, Instant analysedAt) {
    }

    public record GameAccuracyDto(long gameId, double accuracy, String colour, String result, PlayerRef opponent, Instant playedAt) {
    }

    /** {@code average} is null without analysed games. {@code recent} is oldest first. */
    public record PlayerAccuracyDto(int games, Double average, GameAccuracyDto best, List<GameAccuracyDto> recent) {
    }

    /**
     * Rating ≈ intercept + slope × accuracy, fitted to the group's analysed games (each player's rating before
     * the game): the rating that players here who play that accurately usually have. Kept between {@code low}
     * and {@code high}, the lowest and highest rating in the samples, so a near-perfect game never reads as
     * stronger than anyone in the group. All four are null until there are {@code minSamples} samples and
     * accuracy actually goes with rating.
     */
    public record CalibrationDto(int samples, int minSamples, Double intercept, Double slope, Double low, Double high) {
        static CalibrationDto none(int samples) {
            return new CalibrationDto(samples, CALIBRATION_MIN, null, null, null, null);
        }
    }

    private final AnalysisRepository analyses;
    private final GameRepository games;
    private final UserRepository users;

    public AnalysisService(AnalysisRepository analyses, GameRepository games, UserRepository users) {
        this.analyses = analyses;
        this.games = games;
        this.users = users;
    }

    @Transactional
    public AnalysisDto get(long gameId) {
        Game game = game(gameId);
        return current(game).map(this::toDto)
                .orElseThrow(() -> ApiException.notFound("analysis.not_found", "This game hasn't been analysed yet"));
    }

    /**
     * Saves the analysis unless the game already has one; either way returns the one that's kept. The server
     * computes the accuracies from the evaluations.
     */
    @Transactional
    public AnalysisDto save(long gameId, SaveRequest req, CurrentUser actor) {
        Game game = game(gameId);
        Optional<AnalysisRepository.Row> existing = current(game);
        if (existing.isPresent()) {
            return toDto(existing.get());
        }
        int plies = MoveText.plies(game.getMoves());
        if (plies == 0) {
            throw ApiException.conflict("analysis.no_moves", "This game has no moves to analyse");
        }
        if (req == null || req.depth() == null || req.depth() < 1 || req.depth() > 99) {
            throw ApiException.badRequest("analysis.invalid", "depth must be 1–99");
        }
        List<EvalDto> evals = req.evals();
        if (evals == null || evals.size() != plies + 1) {
            throw ApiException.badRequest("analysis.wrong_length", "Need one evaluation per position: " + (plies + 1));
        }
        List<Score> scores = new ArrayList<>(evals.size());
        List<EvalDto> clean = new ArrayList<>(evals.size());
        for (EvalDto e : evals) {
            Score s = validScore(e);
            String best = e.best();
            if (best != null && !UCI.matcher(best).matches()) {
                throw ApiException.badRequest("analysis.invalid", "best must be a move like e2e4");
            }
            scores.add(s);
            clean.add(new EvalDto(s.cp() != null ? new ScoreDto(s.cp(), null, null) : new ScoreDto(null, s.mate(), String.valueOf(s.mateBy())), best));
        }
        GameAccuracy.Result r = GameAccuracy.compute(scores);
        analyses.insert(gameId, hash(game.getMoves()), req.depth(), plies, JSON.writeValueAsString(clean),
                r.white().accuracy(), r.black().accuracy(), actor.id(), Instant.now());
        return toDto(analyses.find(gameId).orElseThrow());
    }

    /** Admin: forget a game's analysis (the next person to open it analyses it again). */
    @Transactional
    public void delete(long gameId) {
        game(gameId);
        analyses.delete(gameId);
    }

    @Transactional(readOnly = true)
    public PlayerAccuracyDto forPlayer(String username) {
        User user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> ApiException.notFound("player.not_found", "Player not found"));
        long id = user.getId();
        List<GameRow> rows = analyses.forPlayer(id).stream()
                .filter(g -> (g.whiteId() == id ? g.whiteMoves() : g.blackMoves()) >= MIN_MOVES)
                .toList();
        if (rows.isEmpty()) {
            return new PlayerAccuracyDto(0, null, null, List.of());
        }
        Map<Long, User> people = new HashMap<>();
        users.findAllById(rows.stream().map(g -> g.whiteId() == id ? g.blackId() : g.whiteId()).distinct().toList())
                .forEach(u -> people.put(u.getId(), u));
        List<GameAccuracyDto> all = rows.stream().map(g -> {
            boolean white = g.whiteId() == id;
            return new GameAccuracyDto(g.gameId(), white ? g.whiteAccuracy() : g.blackAccuracy(), white ? "WHITE" : "BLACK",
                    g.result(), PlayerRef.of(people.get(white ? g.blackId() : g.whiteId())), g.playedAt());
        }).toList();
        double average = all.stream().mapToDouble(GameAccuracyDto::accuracy).average().orElse(0);
        GameAccuracyDto best = all.stream().max(Comparator.comparingDouble(GameAccuracyDto::accuracy)
                .thenComparing(GameAccuracyDto::playedAt)).orElseThrow();
        List<GameAccuracyDto> recent = new ArrayList<>(all.subList(0, Math.min(RECENT, all.size())));
        Collections.reverse(recent);
        return new PlayerAccuracyDto(all.size(), average, best, recent);
    }

    @Transactional(readOnly = true)
    public CalibrationDto calibration() {
        List<double[]> samples = new ArrayList<>();
        for (GameRow g : analyses.allConfirmed()) {
            if (g.whiteRatingBefore() != null && g.whiteMoves() >= MIN_MOVES) {
                samples.add(new double[] {g.whiteAccuracy(), g.whiteRatingBefore()});
            }
            if (g.blackRatingBefore() != null && g.blackMoves() >= MIN_MOVES) {
                samples.add(new double[] {g.blackAccuracy(), g.blackRatingBefore()});
            }
        }
        return calibrate(samples);
    }

    /**
     * Least squares of rating on accuracy, the rating that goes with an accuracy (not the other way round: one
     * game's accuracy says little, and this fit knows it, so its estimates stay near the group's middle).
     */
    static CalibrationDto calibrate(List<double[]> samples) {
        int n = samples.size();
        if (n < CALIBRATION_MIN) {
            return CalibrationDto.none(n);
        }
        double mx = 0;
        double my = 0;
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        for (double[] s : samples) {
            mx += s[0];
            my += s[1];
            low = Math.min(low, s[1]);
            high = Math.max(high, s[1]);
        }
        mx /= n;
        my /= n;
        double sxx = 0;
        double sxy = 0;
        for (double[] s : samples) {
            sxx += (s[0] - mx) * (s[0] - mx);
            sxy += (s[0] - mx) * (s[1] - my);
        }
        if (sxx < 1e-9 || sxy <= 0) {
            return CalibrationDto.none(n);
        }
        double slope = sxy / sxx;
        return new CalibrationDto(n, CALIBRATION_MIN, my - slope * mx, slope, low, high);
    }

    private Game game(long id) {
        return games.findById(id).orElseThrow(() -> ApiException.notFound("game.not_found", "Game not found"));
    }

    /** The saved analysis, if it still belongs to the game's moves (a stale one is removed). */
    private Optional<AnalysisRepository.Row> current(Game game) {
        Optional<AnalysisRepository.Row> row = analyses.find(game.getId());
        if (row.isPresent() && (game.getMoves() == null || !row.get().movesHash().equals(hash(game.getMoves())))) {
            analyses.delete(game.getId());
            return Optional.empty();
        }
        return row;
    }

    private static Score validScore(EvalDto e) {
        ScoreDto s = e == null ? null : e.score();
        if (s == null || (s.cp() == null) == (s.mate() == null)) {
            throw ApiException.badRequest("analysis.invalid", "Every position needs a score: cp, or mate and by");
        }
        if (s.cp() != null) {
            if (Math.abs(s.cp()) > MAX_CP || s.by() != null) {
                throw ApiException.badRequest("analysis.invalid", "cp out of range");
            }
            return Score.cp(s.cp());
        }
        if (s.mate() < 0 || s.mate() > MAX_MATE || !("w".equals(s.by()) || "b".equals(s.by()))) {
            throw ApiException.badRequest("analysis.invalid", "mate needs 0–500 moves and by 'w' or 'b'");
        }
        return Score.mate(s.mate(), s.by().charAt(0));
    }

    private AnalysisDto toDto(AnalysisRepository.Row row) {
        List<EvalDto> evals = JSON.readValue(row.evals(), new TypeReference<List<EvalDto>>() {
        });
        return new AnalysisDto(row.gameId(), row.depth(), evals, new SideDto((row.plies() + 1) / 2, row.whiteAccuracy()),
                new SideDto(row.plies() / 2, row.blackAccuracy()), row.createdAt());
    }

    static String hash(String moves) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(moves.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
