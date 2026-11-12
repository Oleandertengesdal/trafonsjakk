package no.trafonsjakk.review;

import java.time.Instant;
import java.util.List;
import no.trafonsjakk.award.AwardRepository;
import no.trafonsjakk.game.GameResult;
import no.trafonsjakk.user.PlayerRef;

/** "Årets sjakk" payloads. Ratings are rounded to whole numbers; {@code expected} is 0–1. */
public final class YearReviewDtos {

    private YearReviewDtos() {
    }

    public record GameRef(long id, PlayerRef white, PlayerRef black, GameResult result, Instant playedAt) {
    }

    public record MonthRating(String month, int rating) {
    }

    public record BestMonth(String month, int games, int wins, int draws, int losses, double overExpected, int ratingChange) {
    }

    public record Upset(long gameId, Instant playedAt, PlayerRef winner, PlayerRef loser, double expected,
                        int winnerRating, int loserRating) {
    }

    public record Rival(PlayerRef opponent, int games, int wins, int draws, int losses) {
    }

    public record Streak(PlayerRef player, int length, Instant from, Instant to) {
    }

    public record Opening(String name, int games, double scorePct) {
    }

    /** {@code day}: 1 = Monday … 7 = Sunday. */
    public record Weekday(int day, int games) {
    }

    public record BuzzGame(GameRef game, int kudos, int comments) {
    }

    public record YearAward(String month, AwardRepository.Kind kind) {
    }

    /** Everything but {@code year}, {@code complete}, {@code player} and {@code games} is empty when games is 0. */
    public record PlayerYear(int year, boolean complete, PlayerRef player, int games, int wins, int draws, int losses,
                             double points, Integer ratingStart, Integer ratingEnd, Integer ratingPeak,
                             List<MonthRating> ratingByMonth, BestMonth bestMonth, Upset biggestUpset, Rival rival,
                             Streak longestWinStreak, Opening favoriteOpening, Weekday busiestWeekday, int monthsActive,
                             BuzzGame mostKudos, int gamesWithMoves, Integer rankByGames, int players,
                             List<YearAward> awards) {
    }

    public record MonthCount(String month, int games) {
    }

    public record AwardLeader(PlayerRef player, int playerOfMonth, int mostActive) {
    }

    public record PlayerCount(PlayerRef player, int games) {
    }

    public record Climber(PlayerRef player, int change, int start, int end) {
    }

    public record Rivalry(PlayerRef a, PlayerRef b, int games, int aWins, int draws, int bWins) {
    }

    public record GroupYear(int year, boolean complete, int games, int players, int whiteWins, int draws, int blackWins,
                            int gamesWithMoves, int kudos, int comments, MonthCount busiestMonth, Weekday busiestWeekday,
                            BuzzGame gameOfTheYear, Upset giantKiller, List<AwardLeader> awardLeaders,
                            List<PlayerCount> mostGames, List<Climber> climbers, Streak longestWinStreak, Rivalry topRivalry) {
    }
}
