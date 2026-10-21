package com.hamco.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** Deploying with the production profile must not silently use an ephemeral local key. */
class AuthProductionConfigurationTest {
    @Test void productionProfileRequiresStrongExplicitKey() {
        var production = new MockEnvironment();
        production.setActiveProfiles("production");
        assertThatThrownBy(() -> new AuthSecurity("", "hamco-api", "hamco-client", false, production))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AuthSecurity("too-short", "hamco-api", "hamco-client", false, production))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LocalMailSink(production))
                .isInstanceOf(IllegalStateException.class);
        org.assertj.core.api.Assertions.assertThat(AuthController.requiresSecureCookie(false, production)).isTrue();
    }
}
