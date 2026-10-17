package com.transactionapi.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Spring Boot 4 only auto-configures Flyway through spring-boot-starter-flyway; flyway-core alone
// leaves migrations silently unapplied while ddl-auto=validate still passes on an existing schema.
@SpringBootTest
@ActiveProfiles("test")
class FlywayAutoConfigurationTest {

    @Autowired
    private Flyway flyway;

    @Test
    void appliesAllMigrationsOnStartup() {
        assertThat(flyway.info().applied()).isNotEmpty();
        assertThat(flyway.info().pending()).isEmpty();
    }
}
