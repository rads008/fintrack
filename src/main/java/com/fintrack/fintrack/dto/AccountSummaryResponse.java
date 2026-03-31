package com.fintrack.fintrack.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class AccountSummaryResponse implements Serializable {


    private Long accountId;
    private BigDecimal balance;
    private BigDecimal totalCredits;
    private BigDecimal totalDebits;
    private long transactionCount;
    private List<TransactionResponse> recentTransactions;
}