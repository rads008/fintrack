package com.fintrack.fintrack.repository;

import com.fintrack.fintrack.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    @Query("""
    SELECT COALESCE(SUM(l.amount), 0)
    FROM LedgerEntry l
    WHERE l.accountId = :accountId AND l.type = 'CREDIT'
""")
    BigDecimal sumCredits(@Param("accountId") Long accountId);

    @Query("""
    SELECT COALESCE(SUM(l.amount), 0)
    FROM LedgerEntry l
    WHERE l.accountId = :accountId AND l.type = 'DEBIT'
""")
    BigDecimal sumDebits(@Param("accountId") Long accountId);
    @Query("""
    SELECT COALESCE(SUM(l.amount), 0)
    FROM LedgerEntry l
    WHERE l.accountId = :accountId 
    AND l.type = 'CREDIT'
    AND l.timestamp BETWEEN :from AND :to
""")
    BigDecimal sumCreditsWithDate(Long accountId, LocalDateTime from, LocalDateTime to);
    @Query("""
    SELECT COALESCE(SUM(l.amount), 0)
    FROM LedgerEntry l
    WHERE l.accountId = :accountId 
    AND l.type = 'DEBIT'
    AND l.timestamp BETWEEN :from AND :to
""")
    BigDecimal sumDebitsWithDate(Long accountId, LocalDateTime from, LocalDateTime to);
}
