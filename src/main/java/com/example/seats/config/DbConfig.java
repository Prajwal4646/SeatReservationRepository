package com.example.seats.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration
public class DbConfig {

    public record DbSettings(String jdbcUrl, String user, String password) {}

    static DbSettings parse(String raw) {
        if (raw.startsWith("jdbc:")) {
            return new DbSettings(raw, null, null);
        }

        URI uri = URI.create(raw.replaceFirst("^postgres(ql)?://", "http://"));
        String user = null;
        String password = null;

        if (uri.getRawUserInfo() != null) {
            String[] parts = uri.getRawUserInfo().split(":", 2);
            user = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            if (parts.length > 1) {
                password = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }

        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();

        return new DbSettings(
                "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath() + query,
                user,
                password
        );
    }

    @Bean
    public DbSettings dbSettings(@Value("${app.database-url}") String url) {
        return parse(url);
    }

    @Bean(destroyMethod = "close")
    public DataSource dataSource(DbSettings settings, @Value("${app.db-pool-max}") int poolMax) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("seat-reservation");
        config.setJdbcUrl(settings.jdbcUrl());
        if (settings.user() != null) {
            config.setUsername(settings.user());
        }
        if (settings.password() != null) {
            config.setPassword(settings.password());
        }
        config.setMaximumPoolSize(poolMax);
        config.setMinimumIdle(Math.min(5, poolMax));
        config.setConnectionTimeout(90_000);
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }
}
