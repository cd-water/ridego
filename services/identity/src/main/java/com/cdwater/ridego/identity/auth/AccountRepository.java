package com.cdwater.ridego.identity.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AccountRepository {

    private static final RowMapper<Account> ROW_MAPPER = (rs, rowNum) -> new Account(
            rs.getObject("id", UUID.class),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("role"),
            rs.getString("driver_id"),
            rs.getTimestamp("created_at").toInstant()
    );

    private final JdbcTemplate jdbc;

    public AccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Account> findByUsername(String username) {
        return jdbc.query("SELECT * FROM accounts WHERE username = ?", ROW_MAPPER, username)
                .stream()
                .findFirst();
    }

    public void insert(Account account) {
        jdbc.update(
                "INSERT INTO accounts (id, username, password_hash, role, driver_id, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                account.id(),
                account.username(),
                account.passwordHash(),
                account.role(),
                account.driverId(),
                Timestamp.from(Instant.now())
        );
    }
}
