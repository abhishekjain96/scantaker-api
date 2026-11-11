package com.scantaker.api.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
public class DatabaseConfig {

    @Value("${SPRING_DATASOURCE_URL:${spring.datasource.url:}}")
    private String configuredUrl;

    @Value("${SPRING_DATASOURCE_USERNAME:${DATABASE_USERNAME:${DB_USERNAME:${spring.datasource.username:}}}}")
    private String configuredUsername;

    @Value("${SPRING_DATASOURCE_PASSWORD:${DATABASE_PASSWORD:${DB_PASSWORD:${SUPABASE_PASSWORD:${spring.datasource.password:}}}}}")
    private String configuredPassword;

    @Value("${DATABASE_URL:}")
    private String databaseUrl;

    @Value("${spring.datasource.driver-class-name:}")
    private String configuredDriverClassName;

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();

        // 1. Determine connection URL (Prefer DATABASE_URL if set, else spring.datasource.url)
        String activeUrl = (databaseUrl != null && !databaseUrl.isBlank())
                ? databaseUrl.trim()
                : configuredUrl.trim();

        if (activeUrl.isBlank()) {
            activeUrl = "jdbc:postgresql://localhost:5432/scantaker";
        }

        String finalJdbcUrl = activeUrl;
        String finalUsername = configuredUsername != null ? configuredUsername.trim() : "";
        String finalPassword = configuredPassword != null ? configuredPassword.trim() : "";

        // 2. Normalize and parse URI (handles both postgresql:// and jdbc:postgresql:// with credentials)
        String urlForParsing = activeUrl;
        if (urlForParsing.startsWith("jdbc:")) {
            urlForParsing = urlForParsing.substring("jdbc:".length());
        }

        if (urlForParsing.startsWith("postgresql://") || urlForParsing.startsWith("postgres://")) {
            try {
                URI uri = new URI(urlForParsing);
                String userInfo = uri.getUserInfo();
                if (userInfo != null && !userInfo.isBlank()) {
                    if (userInfo.contains(":")) {
                        String[] parts = userInfo.split(":", 2);
                        if (finalUsername.isBlank()) {
                            finalUsername = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                        }
                        if (finalPassword.isBlank() && parts.length > 1 && !parts[1].isBlank()) {
                            finalPassword = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                        }
                    } else {
                        if (finalUsername.isBlank()) {
                            finalUsername = URLDecoder.decode(userInfo, StandardCharsets.UTF_8);
                        }
                    }
                }

                String host = uri.getHost();
                int port = uri.getPort() == -1 ? 5432 : uri.getPort();
                String path = uri.getPath(); // e.g. /postgres
                String query = uri.getQuery();

                StringBuilder sb = new StringBuilder("jdbc:postgresql://")
                        .append(host)
                        .append(":")
                        .append(port)
                        .append(path);

                if (query != null && !query.isBlank()) {
                    if (!query.contains("sslmode")) {
                        sb.append("?").append(query).append("&sslmode=require");
                    } else {
                        sb.append("?").append(query);
                    }
                } else {
                    sb.append("?sslmode=require");
                }

                finalJdbcUrl = sb.toString();
                log.info("DatabaseConfig: Successfully parsed PostgreSQL URL for host: {}, user: {}", host, finalUsername);
            } catch (Exception e) {
                log.warn("DatabaseConfig: Could not parse URI cleanly ({}), using active URL", e.getMessage());
                if (!activeUrl.startsWith("jdbc:")) {
                    finalJdbcUrl = "jdbc:" + activeUrl;
                }
            }
        }

        // 3. Fallbacks for local development
        if (finalUsername.isBlank()) {
            finalUsername = "postgres";
        }
        if (finalPassword.isBlank() && (finalJdbcUrl.contains("localhost") || finalJdbcUrl.contains("127.0.0.1"))) {
            finalPassword = "admin123";
        }

        if (finalPassword.isBlank() && !finalJdbcUrl.startsWith("jdbc:h2:")) {
            log.error("*****************************************************************************");
            log.error("DATABASE PASSWORD IS EMPTY! Check Render Environment Variables: DATABASE_URL or DATABASE_PASSWORD");
            log.error("*****************************************************************************");
        }

        log.info("DatabaseConfig: Initializing DataSource [URL: {}, User: {}, HasPassword: {}]",
                finalJdbcUrl.replaceAll(":[^:@]+@", ":****@"), finalUsername, !finalPassword.isBlank());

        config.setJdbcUrl(finalJdbcUrl);
        config.setUsername(finalUsername);
        config.setPassword(finalPassword);

        String driverClass = "org.postgresql.Driver";
        if (configuredDriverClassName != null && !configuredDriverClassName.isBlank()) {
            driverClass = configuredDriverClassName.trim();
        } else if (finalJdbcUrl.startsWith("jdbc:h2:")) {
            driverClass = "org.h2.Driver";
        }
        config.setDriverClassName(driverClass);

        // Optimal pool configuration for Render free tier (512MB RAM) and Supabase pooler
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000); // 30 seconds
        config.setIdleTimeout(600000);       // 10 minutes
        config.setMaxLifetime(1800000);     // 30 minutes

        return new HikariDataSource(config);
    }
}
