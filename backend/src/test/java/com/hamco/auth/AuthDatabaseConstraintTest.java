package com.hamco.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.hamco.PostgresTestConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Database uniqueness is the final concurrency guard even when two app nodes race. */
@Import(PostgresTestConfiguration.class)
@SpringBootTest
class AuthDatabaseConstraintTest {
    @Autowired JdbcTemplate db;
    @Test void emailIsUniqueRegardlessOfCase() {
        String id = UUID.randomUUID().toString();
        String otherId = UUID.randomUUID().toString();
        String address = id + "@example.test";
        try {
            db.update("insert into users(id,username,email,password_hash) values(?,?,?,?)", id, "a", address, "unused");
            assertThatThrownBy(() -> db.update("insert into users(id,username,email,password_hash) values(?,?,?,?)",
                    otherId, "b", address.toUpperCase(java.util.Locale.ROOT), "unused"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {
            db.update("delete from users where id in (?,?)", id, otherId);
        }
    }
}
