package no.trafonsjakk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import no.trafonsjakk.support.ApiSession.Response;
import no.trafonsjakk.support.IntegrationTest;
import no.trafonsjakk.support.Race;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Replies (one level, like most apps) and likes on comments. */
class CommentThreadsApiTest extends IntegrationTest {

    Player admin;
    Player alice;
    Player bob;
    Player carol;
    long gameId;

    @BeforeEach
    void setUp() {
        admin = registerAdmin();
        alice = registerMember(admin, "alice");
        bob = registerMember(admin, "bob");
        carol = registerMember(admin, "carol");
        gameId = playConfirmed(alice, bob, game(alice, bob, "WHITE_WIN")).path("id").asLong();
    }

    private String comments() {
        return "/api/games/" + gameId + "/comments";
    }

    private long post(Player p, String body, Long parentId) {
        Map<String, Object> req = new java.util.HashMap<>(Map.of("body", body));
        if (parentId != null) {
            req.put("parentId", parentId);
        }
        Response r = p.api().post(comments(), req);
        assertThat(r.status()).as("%s", r).isEqualTo(201);
        return r.json().path("id").asLong();
    }

    @Test
    void repliesAreNestedUnderTheirComment() {
        long root = post(carol, "Great finish!", null);
        long reply = post(alice, "Thanks!", root);
        long replyToReply = post(bob, "Lucky", reply); // replying to a reply stays in the same thread
        long other = post(bob, "Separate thought", null);

        JsonNode list = carol.api().get(comments()).json();
        assertThat(list).hasSize(2);
        assertThat(list.get(0).path("id").asLong()).isEqualTo(root);
        JsonNode replies = list.get(0).path("replies");
        assertThat(replies).hasSize(2);
        assertThat(replies.get(0).path("id").asLong()).isEqualTo(reply);
        assertThat(replies.get(1).path("id").asLong()).isEqualTo(replyToReply);
        assertThat(replies.get(1).path("parentId").asLong()).isEqualTo(root);
        assertThat(replies.get(1).path("replyTo").path("id").asLong()).as("who was answered").isEqualTo(alice.id());
        assertThat(list.get(1).path("id").asLong()).isEqualTo(other);
        assertThat(list.get(1).path("replies")).isEmpty();

        assertThat(alice.api().get("/api/games/" + gameId).json().path("commentCount").asInt()).isEqualTo(4);
    }

    @Test
    void aReplyMustBelongToTheSameGame() {
        long otherGame = playConfirmed(carol, bob, game(carol, bob, "DRAW")).path("id").asLong();
        long foreign = carol.api().post("/api/games/" + otherGame + "/comments", Map.of("body", "elsewhere")).json().path("id").asLong();
        Response r = alice.api().post(comments(), Map.of("body", "hi", "parentId", foreign));
        assertThat(r.status()).isEqualTo(404);
        assertThat(r.code()).isEqualTo("comment.not_found");
        assertThat(alice.api().post(comments(), Map.of("body", "hi", "parentId", 999999)).code()).isEqualTo("comment.not_found");
    }

    @Test
    void likesAreOnePerPersonAndNotOnYourOwnComment() {
        long id = post(carol, "Nice", null);
        String like = comments() + "/" + id + "/like";

        Response own = carol.api().put(like, Map.of());
        assertThat(own.status()).isEqualTo(403);
        assertThat(own.code()).isEqualTo("comment.own_like");

        JsonNode liked = alice.api().put(like, Map.of()).json();
        assertThat(liked.path("likeCount").asInt()).isEqualTo(1);
        assertThat(liked.path("likedByMe").asBoolean()).isTrue();
        assertThat(alice.api().put(like, Map.of()).json().path("likeCount").asInt()).as("idempotent").isEqualTo(1);
        bob.api().put(like, Map.of());

        JsonNode asCarol = carol.api().get(comments()).json().get(0);
        assertThat(asCarol.path("likeCount").asInt()).isEqualTo(2);
        assertThat(asCarol.path("likedByMe").asBoolean()).isFalse();
        assertThat(asCarol.path("canLike").asBoolean()).isFalse();
        assertThat(asCarol.path("likedBy").findValuesAsString("realName")).hasSize(2);
        assertThat(alice.api().get(comments()).json().get(0).path("canLike").asBoolean()).isTrue();

        JsonNode unliked = alice.api().delete(like).json();
        assertThat(unliked.path("likeCount").asInt()).isEqualTo(1);
        assertThat(unliked.path("likedByMe").asBoolean()).isFalse();
        assertThat(alice.api().delete(like).json().path("likeCount").asInt()).isEqualTo(1);
    }

    @Test
    void likingRepliesWorksToo() {
        long root = post(carol, "Root", null);
        long reply = post(alice, "Reply", root);
        bob.api().put(comments() + "/" + reply + "/like", Map.of());
        JsonNode r = carol.api().get(comments()).json().get(0).path("replies").get(0);
        assertThat(r.path("likeCount").asInt()).isEqualTo(1);
    }

    @Test
    void doubleTappingLikeCountsOnce() {
        long id = post(carol, "Nice", null);
        String like = comments() + "/" + id + "/like";
        Player aliceTab2 = login(alice.id());
        List<Response> rs = Race.run(() -> alice.api().put(like, Map.of()), () -> aliceTab2.api().put(like, Map.of()),
                () -> alice.api().put(like, Map.of()));
        assertThat(rs).allSatisfy(r -> assertThat(r.status()).as("%s", r).isEqualTo(200));
        assertThat(jdbc.queryForObject("select count(*) from comment_likes", Integer.class)).isEqualTo(1);
    }

    @Test
    void deletingACommentWithRepliesKeepsTheThreadReadable() {
        long root = post(carol, "Controversial take", null);
        long reply = post(alice, "Disagree", root);
        bob.api().put(comments() + "/" + root + "/like", Map.of());

        assertThat(carol.api().delete(comments() + "/" + root).status()).isEqualTo(204);
        JsonNode thread = alice.api().get(comments()).json().get(0);
        assertThat(thread.path("deleted").asBoolean()).isTrue();
        assertThat(thread.path("body").asString()).as("text is gone").isEmpty();
        assertThat(thread.path("author").isNull()).isTrue();
        assertThat(thread.path("likeCount").asInt()).isZero();
        assertThat(thread.path("canEdit").asBoolean()).isFalse();
        assertThat(thread.path("canLike").asBoolean()).isFalse();
        assertThat(thread.path("replies")).hasSize(1);
        assertThat(alice.api().get("/api/games/" + gameId).json().path("commentCount").asInt()).as("deleted ones don't count")
                .isEqualTo(1);

        // People can still answer in the thread, but not edit or like the deleted comment
        assertThat(post(bob, "Still here", root)).isPositive();
        assertThat(carol.api().patch(comments() + "/" + root, Map.of("body", "back")).code()).isEqualTo("comment.not_found");
        assertThat(alice.api().put(comments() + "/" + root + "/like", Map.of()).code()).isEqualTo("comment.not_found");

        // When the last reply goes, the empty thread goes too
        alice.api().delete(comments() + "/" + reply);
        assertThat(alice.api().get(comments()).json().get(0).path("replies")).hasSize(1);
        long last = alice.api().get(comments()).json().get(0).path("replies").get(0).path("id").asLong();
        bob.api().delete(comments() + "/" + last);
        assertThat(alice.api().get(comments()).json()).isEmpty();
    }

    @Test
    void deletingACommentWithoutRepliesRemovesIt() {
        long id = post(carol, "Oops", null);
        alice.api().put(comments() + "/" + id + "/like", Map.of());
        carol.api().delete(comments() + "/" + id);
        assertThat(alice.api().get(comments()).json()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from comment_likes", Integer.class)).isZero();
    }

    @Test
    void anAdminCanDeleteAReplyAndVoidedGamesTakeNoReplies() {
        long root = post(carol, "Root", null);
        long reply = post(alice, "Rude reply", root);
        assertThat(admin.api().delete(comments() + "/" + reply).status()).isEqualTo(204);
        admin.api().post("/api/admin/games/" + gameId + "/resolve", Map.of("action", "VOID"));
        assertThat(bob.api().post(comments(), Map.of("body", "late", "parentId", root)).code()).isEqualTo("game.voided");
    }
}
