package no.trafonsjakk.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TokensTest {

    @Test
    void inviteCodesAreReadableAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2_000; i++) {
            String code = Tokens.inviteCode();
            // Two groups of five, no look-alike characters (0/O, 1/I/L)
            assertThat(code).matches("[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{5}-[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{5}");
            assertThat(seen.add(code)).as("duplicate invite code %s", code).isTrue();
        }
    }

    @Test
    void normalizationIgnoresCaseSpacesAndDashes() {
        assertThat(Tokens.normalizeInviteCode("k7qx4 mzp2r")).isEqualTo("K7QX4MZP2R");
        assertThat(Tokens.normalizeInviteCode(" K7QX4-MZP2R ")).isEqualTo("K7QX4MZP2R");
        assertThat(Tokens.normalizeInviteCode(null)).isEmpty();
        assertThat(Tokens.normalizeInviteCode("  - ")).isEmpty();
    }

    @Test
    void normalizationIsLocaleIndependent() {
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            // In Turkish, "i".toUpperCase() is "İ" (dotted), which must not break code matching
            assertThat(Tokens.normalizeInviteCode("abcdi-efghi")).isEqualTo("ABCDIEFGHI");
        } finally {
            java.util.Locale.setDefault(original);
        }
    }

    @Test
    void sha256MatchesKnownVector() {
        assertThat(Tokens.sha256("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void randomTokensAre256BitUrlSafe() {
        String t = Tokens.randomToken();
        assertThat(t).matches("[A-Za-z0-9_-]{43}");
        assertThat(Tokens.randomToken()).isNotEqualTo(t);
    }

    @Test
    void constantTimeEqualsHandlesNulls() {
        assertThat(Tokens.constantTimeEquals("a", "a")).isTrue();
        assertThat(Tokens.constantTimeEquals("a", "b")).isFalse();
        assertThat(Tokens.constantTimeEquals(null, "a")).isFalse();
        assertThat(Tokens.constantTimeEquals("a", null)).isFalse();
    }
}
