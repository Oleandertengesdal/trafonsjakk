package no.trafonsjakk.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Skips integration tests when no PostgreSQL is reachable locally (with a hint), but never in CI:
 * with {@code CI=true} they always run, so a broken database setup fails the build.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(RequiresDatabase.Condition.class)
public @interface RequiresDatabase {

    class Condition implements ExecutionCondition {
        private static Boolean available;

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                return ConditionEvaluationResult.enabled("CI: integration tests always run");
            }
            if (available == null) {
                available = canConnect();
            }
            return available
                    ? ConditionEvaluationResult.enabled("PostgreSQL reachable")
                    : ConditionEvaluationResult.disabled(
                            "PostgreSQL not reachable: start it with `docker compose up -d` to run the API integration tests");
        }

        private static boolean canConnect() {
            String url = env("TEST_DATABASE_URL", "jdbc:postgresql://localhost:5432/trafonsjakk");
            try {
                DriverManager.setLoginTimeout(3);
                try (Connection ignored = DriverManager.getConnection(url,
                        env("TEST_DATABASE_USERNAME", "trafonsjakk"), env("TEST_DATABASE_PASSWORD", "trafonsjakk"))) {
                    return true;
                }
            } catch (Exception e) {
                return false;
            }
        }

        private static String env(String key, String fallback) {
            String v = System.getenv(key);
            return v == null || v.isBlank() ? fallback : v;
        }
    }
}
