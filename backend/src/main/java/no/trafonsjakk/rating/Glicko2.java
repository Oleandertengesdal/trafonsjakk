package no.trafonsjakk.rating;

import java.util.List;

/**
 * Glicko-2 rating system (Mark Glickman, "Example of the Glicko-2 system", 2022 revision).
 * <p>
 * Pure math, no framework dependencies. Trafonsjakk treats every game as its own rating period
 * (lichess-style), and grows the rating deviation for inactivity before each game via {@link #decay}.
 */
public final class Glicko2 {

    public static final double DEFAULT_RATING = 1500.0;
    public static final double DEFAULT_RD = 350.0;
    public static final double DEFAULT_VOLATILITY = 0.06;
    /** System constant constraining volatility change. Glickman suggests 0.3–1.2. */
    public static final double DEFAULT_TAU = 0.5;
    /** RD above this is shown as provisional ("?"). */
    public static final double PROVISIONAL_RD = 110.0;
    /** Inactivity is measured in periods of this many days. */
    public static final double RATING_PERIOD_DAYS = 7.0;

    static final double SCALE = 173.7178;
    private static final double CONVERGENCE = 0.000001;

    public record Rating(double rating, double rd, double volatility) {
        public static Rating initial() {
            return new Rating(DEFAULT_RATING, DEFAULT_RD, DEFAULT_VOLATILITY);
        }

        public boolean provisional() {
            return rd > PROVISIONAL_RD;
        }
    }

    /** One game against an opponent. {@code score} is 1 (win), 0.5 (draw) or 0 (loss). */
    public record Outcome(Rating opponent, double score) {
    }

    private final double tau;

    public Glicko2() {
        this(DEFAULT_TAU);
    }

    public Glicko2(double tau) {
        if (tau <= 0) {
            throw new IllegalArgumentException("tau must be positive");
        }
        this.tau = tau;
    }

    /**
     * Grows RD for {@code periods} rating periods without games (fractional periods allowed).
     * {@code φ* = sqrt(φ² + σ²·t)}, capped at the initial RD.
     */
    public Rating decay(Rating r, double periods) {
        if (periods <= 0) {
            return r;
        }
        double phi = r.rd() / SCALE;
        double phiStar = Math.sqrt(phi * phi + r.volatility() * r.volatility() * periods);
        return new Rating(r.rating(), Math.min(phiStar * SCALE, DEFAULT_RD), r.volatility());
    }

    /** Convenience: update after a single game. */
    public Rating update(Rating player, Rating opponent, double score) {
        return update(player, List.of(new Outcome(opponent, score)));
    }

    /** Full Glicko-2 update for one rating period containing {@code outcomes}. */
    public Rating update(Rating player, List<Outcome> outcomes) {
        if (outcomes.isEmpty()) {
            return decay(player, 1);
        }

        double mu = (player.rating() - DEFAULT_RATING) / SCALE;
        double phi = player.rd() / SCALE;
        double sigma = player.volatility();

        // Steps 3 & 4: estimated variance v and improvement Δ
        double vInverse = 0;
        double scoreSum = 0;
        for (Outcome o : outcomes) {
            validateScore(o.score());
            double muJ = (o.opponent().rating() - DEFAULT_RATING) / SCALE;
            double phiJ = o.opponent().rd() / SCALE;
            double g = g(phiJ);
            double e = expected(mu, muJ, g);
            vInverse += g * g * e * (1 - e);
            scoreSum += g * (o.score() - e);
        }
        double v = 1.0 / vInverse;
        double delta = v * scoreSum;

        // Step 5: new volatility
        double sigmaPrime = newVolatility(sigma, phi, v, delta);

        // Steps 6 & 7: new RD and rating
        double phiStar = Math.sqrt(phi * phi + sigmaPrime * sigmaPrime);
        double phiPrime = 1.0 / Math.sqrt(1.0 / (phiStar * phiStar) + 1.0 / v);
        double muPrime = mu + phiPrime * phiPrime * scoreSum;

        // Step 8: back to the Glicko scale
        return new Rating(SCALE * muPrime + DEFAULT_RATING, Math.min(SCALE * phiPrime, DEFAULT_RD), sigmaPrime);
    }

    /** Expected score of a player against an opponent (Glicko-2 E function), on the 0..1 scale. */
    public static double expectedScore(Rating player, Rating opponent) {
        double mu = (player.rating() - DEFAULT_RATING) / SCALE;
        double muJ = (opponent.rating() - DEFAULT_RATING) / SCALE;
        return expected(mu, muJ, g(opponent.rd() / SCALE));
    }

    private static double g(double phi) {
        return 1.0 / Math.sqrt(1.0 + 3.0 * phi * phi / (Math.PI * Math.PI));
    }

    private static double expected(double mu, double muJ, double g) {
        return 1.0 / (1.0 + Math.exp(-g * (mu - muJ)));
    }

    private double newVolatility(double sigma, double phi, double v, double delta) {
        double a = Math.log(sigma * sigma);
        double deltaSq = delta * delta;
        double phiSq = phi * phi;

        double bigA = a;
        double bigB;
        if (deltaSq > phiSq + v) {
            bigB = Math.log(deltaSq - phiSq - v);
        } else {
            int k = 1;
            while (f(a - k * tau, a, deltaSq, phiSq, v) < 0) {
                k++;
            }
            bigB = a - k * tau;
        }

        double fA = f(bigA, a, deltaSq, phiSq, v);
        double fB = f(bigB, a, deltaSq, phiSq, v);
        int guard = 0;
        while (Math.abs(bigB - bigA) > CONVERGENCE && guard++ < 1000) {
            double bigC = bigA + (bigA - bigB) * fA / (fB - fA);
            double fC = f(bigC, a, deltaSq, phiSq, v);
            if (fC * fB <= 0) {
                bigA = bigB;
                fA = fB;
            } else {
                fA = fA / 2.0;
            }
            bigB = bigC;
            fB = fC;
        }
        return Math.exp(bigA / 2.0);
    }

    private double f(double x, double a, double deltaSq, double phiSq, double v) {
        double ex = Math.exp(x);
        double num = ex * (deltaSq - phiSq - v - ex);
        double den = 2.0 * Math.pow(phiSq + v + ex, 2);
        return num / den - (x - a) / (tau * tau);
    }

    private static void validateScore(double score) {
        if (score != 0.0 && score != 0.5 && score != 1.0) {
            throw new IllegalArgumentException("score must be 0, 0.5 or 1");
        }
    }
}
