package no.trafonsjakk.tournament;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * Round-robin fixtures using Berger tables (the circle method used by chess federations).
 * <p>
 * Pure logic: takes player ids in seed order and returns pairings. With an odd number of players
 * the fixed seat of the circle is the bye, so whoever would face it rests that round.
 * <p>
 * Colours follow the Berger rule: the fixed seat alternates every round and the other boards
 * alternate by board number. This guarantees, for every field size, that white/black counts differ
 * by at most one and that nobody gets the same colour three rounds in a row. A double round-robin
 * plays the first cycle again with colours swapped in <em>reverse</em> round order, so the seam
 * between the cycles doesn't create a third consecutive colour either.
 */
public final class RoundRobinScheduler {

    public record Pairing(int round, int board, long white, long black) {
    }

    private RoundRobinScheduler() {
    }

    public static int roundCount(int players, boolean doubleRoundRobin) {
        int n = players % 2 == 0 ? players : players + 1;
        return (n - 1) * (doubleRoundRobin ? 2 : 1);
    }

    public static List<Pairing> schedule(List<Long> playersBySeed, boolean doubleRoundRobin) {
        if (playersBySeed.size() < 2) {
            throw new IllegalArgumentException("A round-robin needs at least 2 players");
        }
        if (new HashSet<>(playersBySeed).size() != playersBySeed.size()) {
            throw new IllegalArgumentException("Duplicate players");
        }

        List<Long> ring = new ArrayList<>(playersBySeed.size() + 1);
        if (playersBySeed.size() % 2 == 1) {
            ring.add(null); // the bye occupies the fixed seat
        }
        ring.addAll(playersBySeed);
        int n = ring.size();
        int rounds = n - 1;

        List<Pairing> pairings = new ArrayList<>();
        for (int r = 0; r < rounds; r++) {
            int board = 0;
            for (int i = 0; i < n / 2; i++) {
                Long top = ring.get(i);
                Long bottom = ring.get(n - 1 - i);
                if (top == null || bottom == null) {
                    continue;
                }
                boolean topWhite = i == 0 ? r % 2 == 0 : i % 2 == 1;
                pairings.add(new Pairing(r + 1, board++, topWhite ? top : bottom, topWhite ? bottom : top));
            }
            // Rotate: keep seat 0 fixed, move the last seat to position 1.
            Long last = ring.remove(n - 1);
            ring.add(1, last);
        }

        if (doubleRoundRobin) {
            List<Pairing> second = new ArrayList<>(pairings.size());
            for (Pairing p : pairings) {
                // Round r of the first cycle is replayed as round (2*rounds + 1 - r), colours swapped.
                second.add(new Pairing(2 * rounds + 1 - p.round(), p.board(), p.black(), p.white()));
            }
            second.sort(Comparator.comparingInt(Pairing::round).thenComparingInt(Pairing::board));
            pairings.addAll(second);
        }
        return pairings;
    }
}
