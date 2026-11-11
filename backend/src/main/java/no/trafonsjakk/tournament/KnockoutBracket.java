package no.trafonsjakk.tournament;

import java.util.ArrayList;
import java.util.List;

/**
 * Seeded single-elimination bracket maths. Pure logic, no framework dependencies.
 * <p>
 * Seeds follow the standard order (1 v 8, 4 v 5, 2 v 7, 3 v 6 for eight players), so the top two
 * seeds can only meet in the final. Missing players become byes, which always fall to the top seeds.
 */
public final class KnockoutBracket {

    /** A first-round slot. {@code white}/{@code black} are null for a bye. Higher seed takes white. */
    public record Slot(int board, Long white, Long black) {
        public boolean isBye() {
            return white == null || black == null;
        }

        public Long soloPlayer() {
            return white != null ? white : black;
        }
    }

    private KnockoutBracket() {
    }

    public static int bracketSize(int players) {
        if (players < 2) {
            throw new IllegalArgumentException("A knockout needs at least 2 players");
        }
        int size = 1;
        while (size < players) {
            size <<= 1;
        }
        return size;
    }

    public static int roundCount(int players) {
        return Integer.numberOfTrailingZeros(bracketSize(players));
    }

    /** Number of matches (boards) in a given round, 1-based. */
    public static int matchesInRound(int players, int round) {
        return bracketSize(players) >> round;
    }

    /** Standard seeding order, e.g. size 8 → [1, 8, 4, 5, 2, 7, 3, 6]. */
    public static int[] seedOrder(int size) {
        List<Integer> order = new ArrayList<>(List.of(1));
        while (order.size() < size) {
            int m = order.size() * 2;
            List<Integer> next = new ArrayList<>(m);
            for (int s : order) {
                next.add(s);
                next.add(m + 1 - s);
            }
            order = next;
        }
        return order.stream().mapToInt(Integer::intValue).toArray();
    }

    public static List<Slot> firstRound(List<Long> playersBySeed) {
        int n = playersBySeed.size();
        int size = bracketSize(n);
        int[] order = seedOrder(size);
        List<Slot> slots = new ArrayList<>(size / 2);
        for (int b = 0; b < size / 2; b++) {
            int high = order[2 * b];
            int low = order[2 * b + 1];
            Long white = high <= n ? playersBySeed.get(high - 1) : null;
            Long black = low <= n ? playersBySeed.get(low - 1) : null;
            slots.add(new Slot(b, white, black));
        }
        return slots;
    }

    public static int nextBoard(int board) {
        return board / 2;
    }

    public static boolean advancesAsWhite(int board) {
        return board % 2 == 0;
    }

    public static String roundName(int round, int totalRounds) {
        int fromEnd = totalRounds - round;
        return switch (fromEnd) {
            case 0 -> "Final";
            case 1 -> "Semifinals";
            case 2 -> "Quarterfinals";
            default -> "Round " + round;
        };
    }
}
