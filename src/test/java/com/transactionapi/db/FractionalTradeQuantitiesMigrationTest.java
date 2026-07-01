package com.transactionapi.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class FractionalTradeQuantitiesMigrationTest {

    @Test
    void preservesExistingIntegerTradeQuantitiesWhenMigratingToNumeric() {
        String databaseName = "fractional_quantities_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        dataSource.setDriverClassName("org.h2.Driver");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("15")
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID tradeId = UUID.randomUUID();
        UUID historyId = UUID.randomUUID();

        jdbc.update("""
                insert into trades (
                    id, user_id, symbol, currency, asset_type, direction, quantity,
                    entry_price, exit_price, fees, margin_rate, opened_at, closed_at,
                    realized_pnl, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                """,
                tradeId,
                "migration-user",
                "BTC",
                "USD",
                "STOCK",
                "LONG",
                7,
                new BigDecimal("60000.0000"),
                new BigDecimal("61000.0000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "2026-06-24",
                "2026-06-24",
                new BigDecimal("7000.00")
        );

        jdbc.update("""
                insert into trade_history (
                    id, trade_id, action, user_id, symbol, currency, asset_type, direction,
                    quantity, entry_price, exit_price, fees, margin_rate, opened_at, closed_at,
                    realized_pnl, trade_created_at, trade_updated_at, action_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp, current_timestamp)
                """,
                historyId,
                tradeId,
                "CREATE",
                "migration-user",
                "BTC",
                "USD",
                "STOCK",
                "LONG",
                7,
                new BigDecimal("60000.0000"),
                new BigDecimal("61000.0000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "2026-06-24",
                "2026-06-24",
                new BigDecimal("7000.00")
        );

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        BigDecimal tradeQuantity = jdbc.queryForObject(
                "select quantity from trades where id = ?",
                BigDecimal.class,
                tradeId
        );
        BigDecimal historyQuantity = jdbc.queryForObject(
                "select quantity from trade_history where id = ?",
                BigDecimal.class,
                historyId
        );

        assertThat(tradeQuantity).isEqualByComparingTo("7");
        assertThat(historyQuantity).isEqualByComparingTo("7");

        jdbc.update("update trades set quantity = ? where id = ?", new BigDecimal("0.1257"), tradeId);
        jdbc.update("update trade_history set quantity = ? where id = ?", new BigDecimal("0.1257"), historyId);

        assertThat(jdbc.queryForObject("select quantity from trades where id = ?", BigDecimal.class, tradeId))
                .isEqualByComparingTo("0.1257");
        assertThat(jdbc.queryForObject("select quantity from trade_history where id = ?", BigDecimal.class, historyId))
                .isEqualByComparingTo("0.1257");
    }
}
