package com.fintrack.fintrack.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fintrack.fintrack.entity.Transaction;
import com.fintrack.fintrack.enums.TransactionType;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class TransactionResponse implements Serializable {

    private Long id;
    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal amount;
    private TransactionType type;
    private String timestamp;

    public static TransactionResponse fromEntity(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getFromAccountId(),
                tx.getToAccountId(),
                tx.getAmount(),
                tx.getType(),
                tx.getTimestamp().toString()
        );
    }
}