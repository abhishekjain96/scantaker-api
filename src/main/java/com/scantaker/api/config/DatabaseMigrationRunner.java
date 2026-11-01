package com.scantaker.api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseMigrationRunner implements ApplicationRunner {
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Running automatic database schema migrations...");
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            // Safe column additions for users table
            executeQuietly(stmt, "ALTER TABLE users ADD COLUMN IF NOT EXISTS google_user boolean DEFAULT false");
            executeQuietly(stmt, "ALTER TABLE users ADD COLUMN IF NOT EXISTS has_password_set boolean DEFAULT true");
            executeQuietly(stmt, "ALTER TABLE users ADD COLUMN IF NOT EXISTS mobile_verified boolean DEFAULT true");
            executeQuietly(stmt, "UPDATE users SET google_user = false WHERE google_user IS NULL");
            executeQuietly(stmt, "UPDATE users SET has_password_set = true WHERE has_password_set IS NULL");
            executeQuietly(stmt, "UPDATE users SET mobile_verified = true WHERE mobile_verified IS NULL");

            // Safe column additions for bookings table
            executeQuietly(stmt, "ALTER TABLE bookings ADD COLUMN IF NOT EXISTS cancel_note character varying(2000)");
            executeQuietly(stmt, "ALTER TABLE bookings ADD COLUMN IF NOT EXISTS scans_list character varying(4000)");
            executeQuietly(stmt, "ALTER TABLE bookings ALTER COLUMN scan_slug TYPE character varying(2000)");
            executeQuietly(stmt, "ALTER TABLE bookings ALTER COLUMN scan_name TYPE character varying(4000)");
            executeQuietly(stmt, "ALTER TABLE bookings ALTER COLUMN notes TYPE character varying(2000)");

            log.info("Database schema auto-migrations completed successfully!");
        } catch (Exception e) {
            log.error("Database migration runner error: {}", e.getMessage());
        }
    }

    private void executeQuietly(Statement stmt, String sql) {
        try {
            stmt.execute(sql);
        } catch (Exception e) {
            log.warn("Migration notice [{}]: {}", sql, e.getMessage());
        }
    }
}
