package no.trafonsjakk.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The move tokens of a game's move text, read the same way as the website reads them
 * ({@code frontend/src/chess/moves.ts}): no tag headers, comments, variations, move numbers, NAGs,
 * annotations or result. Only counts and splits; it doesn't check that the moves are legal.
 */
public final class MoveText {

    private static final Set<String> RESULTS = Set.of("1-0", "0-1", "1/2-1/2", "½-½", "*");
    private static final Pattern HEADER = Pattern.compile("(?m)^\\s*\\[[^\\]]*\\]\\s*$");
    private static final Pattern BRACE_COMMENT = Pattern.compile("\\{[^}]*\\}");
    private static final Pattern LINE_COMMENT = Pattern.compile(";[^\\n]*");
    private static final Pattern VARIATION = Pattern.compile("\\([^()]*\\)");
    private static final Pattern NUMBER_GLUED = Pattern.compile("(?<=\\d\\.)(?=[^.\\d])");
    private static final Pattern MOVE_NUMBER = Pattern.compile("^\\d+\\.+");
    private static final Pattern ANNOTATION = Pattern.compile("[!?]+$");
    private static final Pattern NAG = Pattern.compile("^\\$\\d+$");

    private MoveText() {
    }

    public static List<String> tokens(String text) {
        if (text == null) {
            return List.of();
        }
        String body = HEADER.matcher(text).replaceAll(" ");
        body = BRACE_COMMENT.matcher(body).replaceAll(" ");
        body = LINE_COMMENT.matcher(body).replaceAll(" ");
        String previous;
        do {
            previous = body;
            body = VARIATION.matcher(body).replaceAll(" ");
        } while (!body.equals(previous));
        List<String> out = new ArrayList<>();
        for (String word : body.split("\\s+")) {
            for (String part : NUMBER_GLUED.split(word)) {
                String t = ANNOTATION.matcher(MOVE_NUMBER.matcher(part).replaceFirst("")).replaceFirst("");
                if (!t.isEmpty() && !NAG.matcher(t).matches() && !RESULTS.contains(t)) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    /** Half-moves in the text. */
    public static int plies(String text) {
        return tokens(text).size();
    }
}
