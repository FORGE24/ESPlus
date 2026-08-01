package com.esplus.panel;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class PanelDataConfig {
    @Bean
    DataSource dataSource(@Value("${esplus.db:esplus/security.db}") String dbPath) {
        String url = "jdbc:sqlite:" + dbPath.replace('\\', '/');
        return DataSourceBuilder.create()
                .driverClassName("org.sqlite.JDBC")
                .url(url)
                .build();
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS user_permissions (
                    uuid TEXT NOT NULL,
                    perm TEXT NOT NULL,
                    allowed INTEGER NOT NULL DEFAULT 1,
                    PRIMARY KEY (uuid, perm)
                )
                """);
        return jdbc;
    }
}
