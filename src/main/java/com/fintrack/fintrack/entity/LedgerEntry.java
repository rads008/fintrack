package com.fintrack.fintrack.entity;

import com.fintrack.fintrack.enums.EntryType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(
        name = "ledger_entries",
        indexes = {
                @Index(name = "idx_account", columnList = "accountId"),
                @Index(name = "idx_transaction", columnList = "transactionId")
        }
)
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Which account is affected
    @Column(nullable = false)
    private Long accountId;

    // Amount (always positive)
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // CREDIT or DEBIT
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryType type;

    // Links both entries of a transaction
    @Column(nullable = false)
    private Long transactionId;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    // Optional: description (nice for statements)
    private String description;
}
