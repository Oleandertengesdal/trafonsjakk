package no.trafonsjakk.user;

import no.trafonsjakk.game.GameMode;

/** Compact public view of a player, used everywhere a player is referenced. */
public record PlayerRef(long id, String username, String realName, int rating, int rd, boolean provisional) {

    public static PlayerRef of(User u) {
        if (u == null) {
            return null;
        }
        return new PlayerRef(u.getId(), u.getUsername(), u.getRealName(), (int) Math.round(u.getRating()),
                (int) Math.round(u.getRatingDeviation()), u.isProvisional());
    }

    /** With the rating of that pool: the online rating for an online game. */
    public static PlayerRef of(User u, GameMode mode) {
        if (u == null || mode != GameMode.ONLINE) {
            return of(u);
        }
        return new PlayerRef(u.getId(), u.getUsername(), u.getRealName(), (int) Math.round(u.getOnlineRating()),
                (int) Math.round(u.getOnlineRatingDeviation()), u.isOnlineProvisional());
    }
}
