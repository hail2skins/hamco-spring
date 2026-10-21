package com.hamco;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared PostgreSQL fixture for integration tests.
 *
 * <p>The PostgreSQL container is disposable and has its own credentials and
 * database. {@code @ServiceConnection} asks Spring Boot to supply its JDBC
 * details to the DataSource and Flyway automatically, overriding the ordinary
 * local-development connection. Tests can therefore freely migrate and clean
 * their data without risking the long-lived {@code hamco_spring_dev} database.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        // Match our local Postgres major version, not an unpredictable 'latest'.
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    }
}
