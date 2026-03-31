package com.fintrack.fintrack.specification;

import com.fintrack.fintrack.entity.Transaction;
import com.fintrack.fintrack.enums.TransactionType;

import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public class TransactionSpecification {

    public static Specification<Transaction> hasAccount(Long accountId) {
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("fromAccountId"), accountId),
                cb.equal(root.get("toAccountId"), accountId)
        );
    }

    public static Specification<Transaction> hasType(TransactionType type) {
        return (root, query, cb) ->
                type == null ? null : cb.equal(root.get("type"), type);
    }

    public static Specification<Transaction> hasMinAmount(BigDecimal amount) {
        return (root, query, cb) ->
                amount == null ? null : cb.greaterThanOrEqualTo(root.get("amount"), amount);
    }
}