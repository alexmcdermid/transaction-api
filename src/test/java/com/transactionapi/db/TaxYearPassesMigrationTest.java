package com.transactionapi.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.transactionapi.constants.TaxYearPassStatus;
import com.transactionapi.service.TaxYearPassService;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

// Spring tests replace the Flyway schema with Hibernate's create-drop DDL, so only this test sees V18's constraints.
class TaxYearPassesMigrationTest {

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;
    private UUID userId;

    @BeforeEach
    void migrateFreshDatabase() {
        String databaseName = "tax_year_passes_" + UUID.randomUUID().toString().replace("-", "");
        // One connection for Flyway and the inserts: H2 evaluates an IN-list CHECK through the session that
        // created it, so once Flyway's own connection closed every insert would fail as "Check constraint invalid".
        dataSource = new SingleConnectionDataSource("jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL", "sa", "", true);
        dataSource.setDriverClassName("org.h2.Driver");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);
        userId = UUID.randomUUID();
        jdbc.update("insert into users (id, auth_id) values (?, ?)", userId, "migration-user");
    }

    @AfterEach
    void closeDatabase() {
        dataSource.destroy();
    }

    @Test
    void acceptsEveryStatusTheEntityCanRead() {
        for (TaxYearPassStatus status : TaxYearPassStatus.values()) {
            insertPass(2025, status.name());
        }

        assertThat(jdbc.queryForObject("select count(*) from tax_year_passes", Integer.class))
                .isEqualTo(TaxYearPassStatus.values().length);
    }

    @Test
    void rejectsAStatusTheEntityCannotRead() {
        assertThatThrownBy(() -> insertPass(2025, "paid"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("Check constraint violation");
    }

    @Test
    void rejectsATaxYearBeforeTheEarliestSupportedYear() {
        insertPass(TaxYearPassService.EARLIEST_TAX_YEAR, TaxYearPassStatus.PAID.name());

        assertThatThrownBy(() -> insertPass(TaxYearPassService.EARLIEST_TAX_YEAR - 1, TaxYearPassStatus.PAID.name()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("Check constraint violation");
    }

    private void insertPass(int taxYear, String status) {
        jdbc.update(
                "insert into tax_year_passes (id, user_id, tax_year, status) values (?, ?, ?, ?)",
                UUID.randomUUID(),
                userId,
                taxYear,
                status
        );
    }
}
