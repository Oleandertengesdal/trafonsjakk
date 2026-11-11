package no.trafonsjakk.user;

/** Compact public view of a player, used everywhere a player is referenced. */
public record PlayerRef(long id, String username, String realName, int rating, int rd, boolean provisional) {

    public static PlayerRef of(User u) {
        if (u == null) {
            return null;
        }
        return new PlayerRef(u.getId(), u.getUsername(), u.getRealName(), (int) Math.round(u.getRating()),
                (int) Math.round(u.getRatingDeviation()), u.isProvisional());
    }
}
