package no.trafonsjakk.game;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import no.trafonsjakk.common.CurrentUser;
import no.trafonsjakk.config.AppProperties;
import no.trafonsjakk.game.GameDtos.GameDto;
import no.trafonsjakk.game.GameDtos.RatingChange;
import no.trafonsjakk.game.GameDtos.RatingDelta;
import no.trafonsjakk.game.GameDtos.TournamentRef;
import no.trafonsjakk.rating.RatingHistory;
import no.trafonsjakk.rating.RatingHistoryRepository;
import no.trafonsjakk.tournament.TournamentMatch;
import no.trafonsjakk.user.PlayerRef;
import org.springframework.stereotype.Component;

/** Maps games to DTOs. Call inside a transaction (lazy associations). */
@Component
public class GameMapper {

    private final RatingHistoryRepository history;
    private final AppProperties props;

    public GameMapper(RatingHistoryRepository history, AppProperties props) {
        this.history = history;
        this.props = props;
    }

    public GameDto toDto(Game game, CurrentUser actor) {
        return toDtos(List.of(game), actor).getFirst();
    }

    public List<GameDto> toDtos(Collection<Game> games, CurrentUser actor) {
        List<Long> confirmedIds = games.stream()
                .filter(g -> g.getStatus() == GameStatus.CONFIRMED)
                .map(Game::getId)
                .toList();
        Map<Long, List<RatingHistory>> byGame = confirmedIds.isEmpty() ? Map.of()
                : history.findByGameIdIn(confirmedIds).stream().collect(Collectors.groupingBy(RatingHistory::getGameId));
        return games.stream().map(g -> map(g, actor, byGame.getOrDefault(g.getId(), List.of()))).toList();
    }

    private GameDto map(Game g, CurrentUser actor, List<RatingHistory> rows) {
        boolean pending = g.getStatus() == GameStatus.PENDING;
        boolean canConfirm = pending && g.canBeConfirmedBy(actor.id());
        boolean canWithdraw = pending && (g.getReportedBy().getId() == actor.id() || actor.admin());
        Instant autoConfirmAt = pending ? g.getCreatedAt().plus(props.games().autoConfirmAfter()) : null;

        TournamentRef tournament = null;
        TournamentMatch m = g.getMatch();
        if (m != null) {
            tournament = new TournamentRef(m.getTournament().getId(), m.getTournament().getName(), m.getId(),
                    m.getRoundNumber());
        }

        return new GameDto(g.getId(), PlayerRef.of(g.getWhite()), PlayerRef.of(g.getBlack()), g.getResult(),
                g.getStatus(), g.getPlayedAt(), g.getCreatedAt(), PlayerRef.of(g.getReportedBy()),
                PlayerRef.of(g.getConfirmedBy()), g.getConfirmedAt(), autoConfirmAt, g.getOpening(), g.getEco(),
                g.getNotes(), g.getMoves(), g.getDisputeReason(), tournament, ratingChange(g, rows),
                canConfirm, canConfirm, canWithdraw);
    }

    private static RatingChange ratingChange(Game g, List<RatingHistory> rows) {
        RatingDelta white = null;
        RatingDelta black = null;
        for (RatingHistory h : rows) {
            RatingDelta d = new RatingDelta((int) Math.round(h.getRatingBefore()), (int) Math.round(h.getRatingAfter()));
            if (h.getUserId().equals(g.getWhite().getId())) {
                white = d;
            } else if (h.getUserId().equals(g.getBlack().getId())) {
                black = d;
            }
        }
        return white == null || black == null ? null : new RatingChange(white, black);
    }
}
