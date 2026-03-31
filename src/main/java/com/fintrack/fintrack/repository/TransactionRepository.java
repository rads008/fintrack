package com.fintrack.fintrack.repository;

import com.fintrack.fintrack.entity.Transaction;
import com.fintrack.fintrack.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


@Repository
public interface TransactionRepository extends
        JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {
    // 💰 WITHOUT date filter
    @Query("""
    SELECT COALESCE(SUM(t.amount),0)
    FROM Transaction t
    WHERE t.toAccountId = :accountId
      AND t.type = com.fintrack.fintrack.enums.TransactionType.CREDIT
""")
    BigDecimal getTotalCredits(Long accountId);

    // 💰 WITH date filter
    @Query("""
    SELECT COALESCE(SUM(t.amount),0)
    FROM Transaction t
    WHERE t.toAccountId = :accountId
      AND t.type = com.fintrack.fintrack.enums.TransactionType.CREDIT
      AND t.timestamp BETWEEN :startDate AND :endDate
""")
    BigDecimal getTotalCreditsWithDate(
            Long accountId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );
    // 💸 WITHOUT date filter
    @Query("""
    SELECT COALESCE(SUM(t.amount),0)
    FROM Transaction t
    WHERE t.fromAccountId = :accountId
      AND t.type = com.fintrack.fintrack.enums.TransactionType.DEBIT
""")
    BigDecimal getTotalDebits(Long accountId);

    // 💸 Total Debits (money sent)
    // 💸 WITH date filter
    @Query("""
    SELECT COALESCE(SUM(t.amount),0)
    FROM Transaction t
    WHERE t.fromAccountId = :accountId
      AND t.type = com.fintrack.fintrack.enums.TransactionType.DEBIT
      AND t.timestamp BETWEEN :startDate AND :endDate
""")
    BigDecimal getTotalDebitsWithDate(
            Long accountId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );
    // 🔢 WITHOUT date
    @Query("""
    SELECT COUNT(t)
    FROM Transaction t
    WHERE t.fromAccountId = :accountId OR t.toAccountId = :accountId
""")
    long countTransactions(Long accountId);
    // 🔢 WITH date
    @Query("""
    SELECT COUNT(t)
    FROM Transaction t
    WHERE (t.fromAccountId = :accountId OR t.toAccountId = :accountId)
      AND t.timestamp BETWEEN :startDate AND :endDate
""")
    long countTransactionsWithDate(
            Long accountId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );
    // 📄 Recent transactions (incoming + outgoing)
    @Query("""
        SELECT t
        FROM Transaction t
        WHERE t.fromAccountId = :accountId OR t.toAccountId = :accountId
    """)
    Page<Transaction> findAllByAccountInvolved(
            @Param("accountId") Long accountId,
            Pageable pageable
    );
}