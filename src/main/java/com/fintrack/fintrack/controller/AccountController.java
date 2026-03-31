package com.fintrack.fintrack.controller;

import com.fintrack.fintrack.dto.*;
import com.fintrack.fintrack.entity.Account;
import com.fintrack.fintrack.entity.Transaction;
import com.fintrack.fintrack.enums.TransactionType;
import com.fintrack.fintrack.service.AccountService;

import com.fintrack.fintrack.service.IdempotencyService;

import com.fintrack.fintrack.service.RateLimitService;
import io.github.bucket4j.Bucket;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;



@Validated
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {


    private final AccountService accountService;
    private final IdempotencyService idempotencyService;
    @Autowired
    private RateLimitService rateLimitService;

    // 💳 Create Account
    @PostMapping
    public ResponseEntity<Account> createAccount() {
        return ResponseEntity.ok(accountService.createAccount());
    }

    // 💰 Get Balance
    @GetMapping("/{id}/balance")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.getBalance(id));
    }

    // 📊 Get My Accounts
    @GetMapping
    public ResponseEntity<List<AccountResponse>> getMyAccounts() {
        return ResponseEntity.ok(accountService.getMyAccounts());
    }


    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody TransferRequest request
    ) {

        // 👤 Identify user
        String user = SecurityContextHolder.getContext()
                .getAuthentication().getName();

        // 🪣 Rate limiting (FIRST)
        String rateKey = user + ":" + request.getFromAccountId(); // smarter key
        Bucket bucket = rateLimitService.resolveBucket(rateKey);

        if (!bucket.tryConsume(1)) {
            return ResponseEntity
                    .status(429)
                    .body("Too many requests. Try again later.");
        }

        // 🔁 Idempotency (SECOND)
        Object response = idempotencyService.execute(key, () ->
                accountService.transfer(
                        request.getFromAccountId(),
                        request.getToAccountId(),
                        request.getAmount()
                )
        );

        return ResponseEntity.ok(response);
    }
    @PostMapping("/credit")
    public ResponseEntity<?> credit(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody AmountRequest request
    ) {

        Object response = idempotencyService.execute(key, () ->
                accountService.credit(
                        request.getAccountId(),
                        request.getAmount()
                )
        );

        return ResponseEntity.ok(response);
    }
    @PostMapping("/debit")
    public ResponseEntity<?> debit(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody AmountRequest request
    ) {

        Object response = idempotencyService.execute(key, () ->
                accountService.debit(
                        request.getAccountId(),
                        request.getAmount()
                )
        );

        return ResponseEntity.ok(response);
    }
    @GetMapping("/{id}/transactions")
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            @PathVariable Long id,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page must be >= 0")
            int page,

            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Size must be >= 1")
            int size,

            @RequestParam(required = false)
            TransactionType type,

            @RequestParam(required = false)
            @Positive(message = "Amount must be positive")
            BigDecimal minAmount
    ) {
        return ResponseEntity.ok(
                accountService.getTransactionHistory(id, page, size, type, minAmount)
        );
    }
    @GetMapping("/{id}/summary")
    public ResponseEntity<AccountSummaryResponse> getAccountSummary(
            @PathVariable Long id,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to
    ) {
        return ResponseEntity.ok(
                accountService.getAccountSummary(id, from, to)
        );
    }
}