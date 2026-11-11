package no.trafonsjakk.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import no.trafonsjakk.user.Role;
import no.trafonsjakk.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Who may confirm/dispute a result: the rule that keeps results honest. */
class GameTest {

    private static User user(long id, Role role) {
        User u = new User("u" + id, "User " + id, "hash", role);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private final User alice = user(1, Role.USER);
    private final User bob = user(2, Role.USER);
    private final User carol = user(3, Role.USER);
    private final User admin = user(9, Role.ADMIN);

    @Test
    void whenAPlayerReportsOnlyTheOpponentMayConfirm() {
        Game g = new Game(alice, bob, GameResult.WHITE_WIN, Instant.now(), alice);
        assertThat(g.canBeConfirmedBy(bob.getId())).isTrue();
        assertThat(g.canBeConfirmedBy(alice.getId())).as("reporter can't confirm own result").isFalse();
        assertThat(g.canBeConfirmedBy(carol.getId())).as("outsider").isFalse();
        assertThat(g.canBeConfirmedBy(admin.getId())).as("admins resolve, they don't confirm").isFalse();
    }

    @Test
    void whenAnAdminReportsForOthersEitherPlayerMayConfirm() {
        Game g = new Game(alice, bob, GameResult.DRAW, Instant.now(), admin);
        assertThat(g.canBeConfirmedBy(alice.getId())).isTrue();
        assertThat(g.canBeConfirmedBy(bob.getId())).isTrue();
        assertThat(g.canBeConfirmedBy(admin.getId())).isFalse();
    }

    @Test
    void whenAnAdminReportsTheirOwnGameTheOpponentConfirms() {
        Game g = new Game(admin, bob, GameResult.BLACK_WIN, Instant.now(), admin);
        assertThat(g.canBeConfirmedBy(bob.getId())).isTrue();
        assertThat(g.canBeConfirmedBy(admin.getId())).isFalse();
    }

    @Test
    void winnerFollowsTheResult() {
        assertThat(new Game(alice, bob, GameResult.WHITE_WIN, Instant.now(), alice).winner()).isSameAs(alice);
        assertThat(new Game(alice, bob, GameResult.BLACK_WIN, Instant.now(), alice).winner()).isSameAs(bob);
        assertThat(new Game(alice, bob, GameResult.DRAW, Instant.now(), alice).winner()).isNull();
    }

    @Test
    void statusTransitions() {
        Game g = new Game(alice, bob, GameResult.WHITE_WIN, Instant.now(), alice);
        assertThat(g.getStatus()).isEqualTo(GameStatus.PENDING);
        g.dispute("wrong");
        assertThat(g.getStatus()).isEqualTo(GameStatus.DISPUTED);
        assertThat(g.getDisputeReason()).isEqualTo("wrong");
        Instant at = Instant.now();
        g.confirm(null, at);
        assertThat(g.getStatus()).isEqualTo(GameStatus.CONFIRMED);
        assertThat(g.getConfirmedBy()).as("auto-confirmed").isNull();
        assertThat(g.getConfirmedAt()).isEqualTo(at);
    }

    @Test
    void scoresAreFromWhitesPointOfView() {
        assertThat(GameResult.WHITE_WIN.whiteScore()).isEqualTo(1.0);
        assertThat(GameResult.DRAW.whiteScore()).isEqualTo(0.5);
        assertThat(GameResult.BLACK_WIN.whiteScore()).isEqualTo(0.0);
        assertThat(GameResult.DRAW.isDecisive()).isFalse();
    }
}
