package no.trafonsjakk.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start against a real database with the development defaults, which are public in this
 * repository: the dev JWT secret and the dev bootstrap invite code. That can only happen if the
 * server runs without {@code SPRING_PROFILES_ACTIVE=prod}, where the {@code dev} profile fills them in.
 * Better down than signing sessions with a secret anyone can read on GitHub.
 */
@Component
class DeploymentSafetyCheck implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DeploymentSafetyCheck.class);
    static final String DEV_BOOTSTRAP_CODE = "let-me-in";
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    private final Environment env;
    private final AppProperties props;

    DeploymentSafetyCheck(Environment env, AppProperties props) {
        this.env = env;
        this.props = props;
    }

    @Override
    public void afterPropertiesSet() {
        String url = env.getProperty("spring.datasource.url", "");
        List<String> problems = problems(url, props.jwt().secret(), props.bootstrapInviteCode());
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Unsafe configuration for a real database: " + String.join(" ", problems)
                    + " Set SPRING_PROFILES_ACTIVE=prod and real values in the environment.");
        }
        if (!isLocal(url) && !props.cookies().secure()) {
            log.warn("Auth cookies are sent without the Secure flag to a non-local deployment. Set SPRING_PROFILES_ACTIVE=prod.");
        }
    }

    static List<String> problems(String datasourceUrl, String jwtSecret, String bootstrapCode) {
        List<String> problems = new ArrayList<>();
        if (isLocal(datasourceUrl)) {
            return problems;
        }
        if (jwtSecret != null && jwtSecret.contains(JwtConfig.DEV_SECRET_MARKER)) {
            problems.add("JWT_SECRET is the public development secret.");
        }
        if (bootstrapCode != null && bootstrapCode.trim().equalsIgnoreCase(DEV_BOOTSTRAP_CODE)) {
            problems.add("BOOTSTRAP_INVITE_CODE is the public development code.");
        }
        return problems;
    }

    static boolean isLocal(String datasourceUrl) {
        if (datasourceUrl == null || !datasourceUrl.startsWith("jdbc:")) {
            return false;
        }
        try {
            String host = URI.create(datasourceUrl.substring("jdbc:".length())).getHost();
            return host != null && LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
