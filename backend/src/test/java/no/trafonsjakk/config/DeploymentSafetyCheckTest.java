package no.trafonsjakk.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeploymentSafetyCheckTest {

    static final String DEV_SECRET = "dev-only-insecure-jwt-secret-change-me-0123456789";
    /** Any secret other than the development one; kept low-entropy so secret scanners leave it alone. */
    static final String REAL_SECRET = "x".repeat(48);
    static final String NEON = "jdbc:postgresql://ep-cool-name-123456.eu-central-1.aws.neon.tech/neondb?sslmode=require";

    @Test
    void theDevelopmentDefaultsAreFineOnThisMachine() {
        assertThat(DeploymentSafetyCheck.problems("jdbc:postgresql://localhost:5432/trafonsjakk", DEV_SECRET, "let-me-in")).isEmpty();
        assertThat(DeploymentSafetyCheck.problems("jdbc:postgresql://127.0.0.1/trafonsjakk", DEV_SECRET, "let-me-in")).isEmpty();
        assertThat(DeploymentSafetyCheck.problems("jdbc:postgresql://[::1]:5432/x", DEV_SECRET, null)).isEmpty();
    }

    @Test
    void aRealDatabaseRefusesThePublicDevelopmentSecretAndInviteCode() {
        assertThat(DeploymentSafetyCheck.problems(NEON, DEV_SECRET, null)).singleElement().asString().contains("JWT_SECRET");
        assertThat(DeploymentSafetyCheck.problems(NEON, REAL_SECRET, "let-me-in")).singleElement().asString().contains("BOOTSTRAP_INVITE_CODE");
        assertThat(DeploymentSafetyCheck.problems(NEON, REAL_SECRET, "  LET-ME-IN ")).hasSize(1);
        assertThat(DeploymentSafetyCheck.problems(NEON, REAL_SECRET, null)).isEmpty();
        assertThat(DeploymentSafetyCheck.problems(NEON, REAL_SECRET, "a-long-random-code-nobody-guesses")).isEmpty();
    }

    @Test
    void aHostThatOnlyStartsWithLocalhostIsNotLocal() {
        assertThat(DeploymentSafetyCheck.problems("jdbc:postgresql://localhost.evil.example/db", DEV_SECRET, null)).isNotEmpty();
        assertThat(DeploymentSafetyCheck.problems("jdbc:postgresql://127.0.0.1.nip.io/db", DEV_SECRET, null)).isNotEmpty();
    }
}
