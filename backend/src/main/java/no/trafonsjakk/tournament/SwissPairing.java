package no.trafonsjakk.tournament;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pairs one round of a Swiss tournament. Pure logic, small groups (up to 32 players).
 * <p>
 * Players are ranked by score, then by seed (rating at the start). With an odd number, the lowest-ranked
 * player who hasn't had a bye sits out with a bye (worth a win). Then, from the top, each player is paired
 * with the opponent in their score group that the classic "top half against bottom half" rule suggests, or
 * the nearest one after that, never someone they've already played; if that leaves the rest unpairable, the
 * next choice is tried (backtracking). Players who can't be paired in their group float down to the next.
 * Colours: whoever has had White less often gets White; if equal, whoever had Black last; if still equal,
 * the higher-ranked player alternates by board.
 */
public final class SwissPairing {

    /** A player before the round. {@code lastWhite} is null before their first game. */
    public record Player(long id, double score, int seed, Set<Long> played, int whites, int blacks, Boolean lastWhite,
                         boolean hadBye) {
    }

    public record Pairing(int board, long white, long black) {
    }

    /** {@code bye} is null with an even number of players. Null result: no pairing without a rematch exists. */
    public record Round(List<Pairing> pairings, Long bye) {
    }

    private SwissPairing() {
    }

    public static Round pair(List<Player> players) {
        List<Player> ranked = new ArrayList<>(players);
        ranked.sort(Comparator.comparingDouble((Player p) -> -p.score()).thenComparingInt(Player::seed));
        if (ranked.size() % 2 == 0) {
            List<long[]> pairs = match(ranked);
            return pairs == null ? null : new Round(colour(pairs, ranked), null);
        }
        // The bye: lowest-ranked first, preferring those who haven't had one
        List<Player> byeOrder = new ArrayList<>(ranked.reversed());
        byeOrder.sort(Comparator.comparing(Player::hadBye));
        for (Player bye : byeOrder) {
            List<Player> rest = new ArrayList<>(ranked);
            rest.remove(bye);
            List<long[]> pairs = match(rest);
            if (pairs != null) {
                return new Round(colour(pairs, ranked), bye.id());
            }
        }
        return null;
    }

    /** Gives up after this many tries (a late round of a long tournament can have no pairing at all). */
    static final int MAX_STEPS = 200_000;

    /** Pairs everyone in {@code ranked} (best first) without rematches; null if impossible. */
    private static List<long[]> match(List<Player> ranked) {
        List<long[]> out = new ArrayList<>();
        return search(ranked, new HashSet<>(), out, new int[1]) ? out : null;
    }

    private static boolean search(List<Player> ranked, Set<Long> used, List<long[]> out, int[] steps) {
        if (++steps[0] > MAX_STEPS) {
            return false;
        }
        Player top = null;
        int topIndex = -1;
        for (int i = 0; i < ranked.size(); i++) {
            if (!used.contains(ranked.get(i).id())) {
                top = ranked.get(i);
                topIndex = i;
                break;
            }
        }
        if (top == null) {
            return true;
        }
        used.add(top.id());
        for (Player opp : candidates(ranked, used, top, topIndex)) {
            if (top.played().contains(opp.id())) {
                continue;
            }
            used.add(opp.id());
            out.add(new long[] {top.id(), opp.id()});
            if (search(ranked, used, out, steps)) {
                return true;
            }
            out.removeLast();
            used.remove(opp.id());
        }
        used.remove(top.id());
        return false;
    }

    /**
     * Opponents for {@code top} in order of preference: in its score group, the one half a group further down
     * first, then by distance from there; then everyone below, in ranking order.
     */
    private static List<Player> candidates(List<Player> ranked, Set<Long> used, Player top, int topIndex) {
        List<Player> group = new ArrayList<>();
        List<Player> below = new ArrayList<>();
        for (int i = topIndex + 1; i < ranked.size(); i++) {
            Player p = ranked.get(i);
            if (used.contains(p.id())) {
                continue;
            }
            (p.score() == top.score() ? group : below).add(p);
        }
        // Within the group (top included), the classic partner is half the group further down
        int size = group.size() + 1;
        int target = Math.max(0, size / 2 - 1);
        List<Player> ordered = new ArrayList<>(group);
        ordered.sort(Comparator.comparingInt(p -> Math.abs(group.indexOf(p) - target)));
        ordered.addAll(below);
        return ordered;
    }

    private static List<Pairing> colour(List<long[]> pairs, List<Player> ranked) {
        List<Pairing> out = new ArrayList<>();
        for (int board = 0; board < pairs.size(); board++) {
            Player a = find(ranked, pairs.get(board)[0]);
            Player b = find(ranked, pairs.get(board)[1]);
            boolean aWhite;
            int balanceA = a.whites() - a.blacks();
            int balanceB = b.whites() - b.blacks();
            if (balanceA != balanceB) {
                aWhite = balanceA < balanceB;
            } else if (a.lastWhite() != null && b.lastWhite() != null && !a.lastWhite().equals(b.lastWhite())) {
                aWhite = !a.lastWhite();
            } else {
                aWhite = board % 2 == 0;
            }
            out.add(aWhite ? new Pairing(board, a.id(), b.id()) : new Pairing(board, b.id(), a.id()));
        }
        return out;
    }

    private static Player find(List<Player> players, long id) {
        return players.stream().filter(p -> p.id() == id).findFirst().orElseThrow();
    }

    /** At most this many rounds, and at most players − 1 (beyond that it's a round-robin). */
    public static final int MAX_ROUNDS = 15;

    /** The usual number of rounds: enough to separate the field (log2 of the players, plus one). */
    public static int defaultRounds(int players) {
        return maxRounds(players) == 0 ? 0 : Math.max(1, Math.min((int) Math.ceil(Math.log(Math.max(players, 2)) / Math.log(2)) + 1, maxRounds(players)));
    }

    public static int maxRounds(int players) {
        return Math.max(0, Math.min(players - 1, MAX_ROUNDS));
    }
}
