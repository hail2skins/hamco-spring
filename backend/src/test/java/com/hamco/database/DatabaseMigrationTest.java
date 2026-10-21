package com.hamco.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.hamco.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A schema contract test, not a mock of a database.
 *
 * <p>Spring starts Flyway against an isolated PostgreSQL 16 container before
 * this test executes. Reading {@code information_schema} lets us prove that
 * the actual SQL produced the tables our ASP.NET replacement needs. The first
 * run intentionally fails because V1 does not exist yet (the TDD red step).
 */
@Import(PostgresTestConfiguration.class)
@SpringBootTest
class DatabaseMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayCreatesTheFourDomainTables() {
        // Schema history is bookkeeping, not a domain table; selecting only
        // these names makes the intent obvious without depending on Flyway's
        // implementation details or the order returned by PostgreSQL.
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('users', 'notes', 'api_keys', 'slogans')
                ORDER BY table_name
                """, String.class);

        assertThat(tables).containsExactly("api_keys", "notes", "slogans", "users");
    }

    @Test
    void noteAuthorMayBeNullAndDeletingAUserRetainsTheirNotes() {
        // The effective ASP.NET schema made user_id nullable and used ON
        // DELETE SET NULL. This matters for API-key-authored notes and for
        // retaining posts after a user is removed.
        String nullable = jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'notes'
                  AND column_name = 'user_id'
                """, String.class);
        assertThat(nullable).isEqualTo("YES");

        String deleteAction = jdbc.queryForObject("""
                SELECT rc.delete_rule
                FROM information_schema.referential_constraints rc
                JOIN information_schema.table_constraints tc
                  ON tc.constraint_catalog = rc.constraint_catalog
                 AND tc.constraint_schema = rc.constraint_schema
                 AND tc.constraint_name = rc.constraint_name
                WHERE tc.table_schema = 'public' AND tc.table_name = 'notes'
                """, String.class);
        assertThat(deleteAction).isEqualTo("SET NULL");
    }
}
