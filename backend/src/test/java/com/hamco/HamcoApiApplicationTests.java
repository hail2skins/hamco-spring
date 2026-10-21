package com.hamco;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Our first Spring test checks that the application can be assembled by Spring.
 *
 * <p>{@code @SpringBootTest} constructs the real application context: it finds
 * the configuration class, wires dependencies, and detects configuration errors
 * before we try to serve a request. This is intentionally written before the
 * application class so its first run should fail to compile (the TDD red step).
 */
@Import(PostgresTestConfiguration.class)
@SpringBootTest(classes = HamcoApiApplication.class)
class HamcoApiApplicationTests {

    @Test
    void contextLoads() {
        // There is no assertion because constructing the context is the behavior.
        // Spring/JUnit fails the test automatically if dependency wiring breaks.
    }
}
