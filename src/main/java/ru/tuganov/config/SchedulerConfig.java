package ru.tuganov.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Конфигурация ShedLock — distributed lock для Spring Scheduler.
 * Гарантирует, что планировщик (checkPrices) запускается только
 * на одном инстансе при горизонтальном масштабировании.
 * Использует ту же PostgreSQL БД через JdbcTemplate.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10S")
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build()
        );
    }
}
