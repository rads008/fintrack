package com.fintrack.fintrack.dto;

import java.math.BigDecimal;

public class AccountResponse {

    private Long id;
    private BigDecimal balance;

    public AccountResponse(Long id, BigDecimal balance) {
        this.id = id;
        this.balance = balance;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getBalance() {
        return balance;
    }
}