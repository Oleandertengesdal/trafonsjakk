package no.trafonsjakk.rating;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import no.trafonsjakk.game.Game;
import no.trafonsjakk.game.GameRepository;
import no.trafonsjakk.user.PlayerRef;
import no.trafonsjakk.user.User;
import no.trafonsjakk.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leaderboard. Established players (RD ≤ 110) are ranked by rating; provisional players follow, unranked.
 * Aggregates in memory, which is plenty for a friend group.
 */
@Service
public class LeaderboardService {

    public record Row(Integer rank, PlayerRef player, int games, int wins, int draws, int losses,
                      Instant lastPlayedAt, int trend) {
    }

    private static final int TREND_GAMES = 5;

    private final UserRepository users;
    private final GameRepository games;
    private final RatingHistoryRepository history;

    public LeaderboardService(UserRepository users, GameRepository games, RatingHistoryRepository history) {
        this.users = users;
        this.games = games;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public List<Row> leaderboard() {
        Map<Long, int[]> wdl = new HashMap<>();
        for (Game g : games.findAllConfirmed()) {
            long w = g.getWhite().getId();
            long b = g.getBlack().getId();
            switch (g.getResult()) {
                case WHITE_WIN -> {
                    wdl.computeIfAbsent(w, k -> new int[3])[0]++;
                    wdl.computeIfAbsent(b, k -> new int[3])[2]++;
                }
                case BLACK_WIN -> {
                    wdl.computeIfAbsent(b, k -> new int[3])[0]++;
                    wdl.computeIfAbsent(w, k -> new int[3])[2]++;
                }
                case DRAW -> {
                    wdl.computeIfAbsent(w, k -> new int[3])[1]++;
                    wdl.computeIfAbsent(b, k -> new int[3])[1]++;
                }
            }
        }

        Map<Long, List<RatingHistory>> byUser = new HashMap<>();
        for (RatingHistory h : history.findAllOrdered()) {
            byUser.computeIfAbsent(h.getUserId(), k -> new ArrayList<>()).add(h);
        }

        List<User> all = new ArrayList<>(users.findByEnabledTrueOrderByRatingDesc());
        all.sort(Comparator.comparing(User::isProvisional).thenComparing(Comparator.comparingDouble(User::getRating).reversed()));

        List<Row> rows = new ArrayList<>(all.size());
        int rank = 0;
        for (User u : all) {
            int[] r = wdl.getOrDefault(u.getId(), new int[3]);
            List<RatingHistory> hs = byUser.getOrDefault(u.getId(), List.of());
            int trend = 0;
            if (!hs.isEmpty()) {
                RatingHistory last = hs.getLast();
                RatingHistory from = hs.get(Math.max(0, hs.size() - TREND_GAMES));
                trend = (int) Math.round(last.getRatingAfter() - from.getRatingBefore());
            }
            Integer rk = u.isProvisional() ? null : ++rank;
            rows.add(new Row(rk, PlayerRef.of(u), r[0] + r[1] + r[2], r[0], r[1], r[2], u.getLastGameAt(), trend));
        }
        return rows;
    }

    /** Rank among established players, or null if provisional/disabled. */
    @Transactional(readOnly = true)
    public Integer rankOf(long userId) {
        int rank = 0;
        for (User u : users.findByEnabledTrueOrderByRatingDesc()) {
            if (u.isProvisional()) {
                continue;
            }
            rank++;
            if (u.getId() == userId) {
                return rank;
            }
        }
        return null;
    }
}
