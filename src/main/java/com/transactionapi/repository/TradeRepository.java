package com.transactionapi.repository;

import com.transactionapi.model.Trade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeRepository extends JpaRepository<Trade, UUID> {

    @Query("select t from Trade t where t.userId = :userId order by t.closedAt desc, t.createdAt desc")
    List<Trade> findAllForUser(@Param("userId") String userId);

    Page<Trade> findByUserIdOrderByClosedAtDesc(String userId, Pageable pageable);

    Page<Trade> findByUserIdAndClosedAtBetweenOrderByClosedAtDesc(
            String userId,
            LocalDate start,
            LocalDate end,
            Pageable pageable
    );

    Page<Trade> findByUserIdAndClosedAtOrderByClosedAtDesc(
            String userId,
            LocalDate closedAt,
            Pageable pageable
    );

    List<Trade> findByUserIdAndClosedAtBetweenOrderByClosedAtDesc(String userId, LocalDate start, LocalDate end);

    Optional<Trade> findByIdAndUserId(UUID id, String userId);
    
    @Query("select count(t) from Trade t where t.userId = :userId")
    int countByUserId(@Param("userId") String userId);

    @Query(value = """
        select sum(
            case 
                when currency = 'CAD' then realized_pnl * CAST(:cadToUsd AS numeric)
                else realized_pnl
            end
        )
        from trades
        where user_id = :userId
        """, nativeQuery = true)
    BigDecimal sumPnlByUserId(@Param("userId") String userId, @Param("cadToUsd") BigDecimal cadToUsdRate);

    @Query(value = """
        select
            closed_at as period,
            sum(
                case 
                    when currency = 'CAD' then realized_pnl * CAST(:cadToUsd AS numeric)
                    else realized_pnl
                end
            ) as pnl,
            count(*) as trades
        from trades
        where user_id = :userId
        group by closed_at
        order by pnl desc
        limit 1
        """, nativeQuery = true)
    DailyAggregateProjection findBestDayByUserId(@Param("userId") String userId, @Param("cadToUsd") BigDecimal cadToUsdRate);

    @Query(value = """
        select
            to_char(closed_at, 'YYYY-MM') as period,
            sum(
                case 
                    when currency = 'CAD' then realized_pnl * CAST(:cadToUsd AS numeric)
                    else realized_pnl
                end
            ) as pnl,
            count(*) as trades
        from trades
        where user_id = :userId
        group by to_char(closed_at, 'YYYY-MM')
        order by pnl desc
        limit 1
        """, nativeQuery = true)
    MonthlyAggregateProjection findBestMonthByUserId(@Param("userId") String userId, @Param("cadToUsd") BigDecimal cadToUsdRate);

    @Query(value = """
        select sum(
            case
                when currency = 'CAD' then (
                    entry_price * quantity *
                    case when asset_type = 'OPTION' then 100 else 1 end
                ) * CAST(:cadToUsd AS numeric)
                else entry_price * quantity *
                    case when asset_type = 'OPTION' then 100 else 1 end
            end
        )
        from trades
        where user_id = :userId
        """, nativeQuery = true)
    BigDecimal sumNotionalByUserId(@Param("userId") String userId, @Param("cadToUsd") BigDecimal cadToUsdRate);

    interface DailyAggregateProjection {
        LocalDate getPeriod();
        BigDecimal getPnl();
        Integer getTrades();
    }

    interface MonthlyAggregateProjection {
        String getPeriod();
        BigDecimal getPnl();
        Integer getTrades();
    }
}
