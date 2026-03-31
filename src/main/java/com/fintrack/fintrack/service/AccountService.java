package com.fintrack.fintrack.service;

import com.fintrack.fintrack.dto.AccountResponse;
import com.fintrack.fintrack.dto.AccountSummaryResponse;
import com.fintrack.fintrack.dto.TransactionResponse;
import com.fintrack.fintrack.entity.Account;
import com.fintrack.fintrack.entity.LedgerEntry;
import com.fintrack.fintrack.entity.User;
import com.fintrack.fintrack.enums.EntryType;
import com.fintrack.fintrack.repository.AccountRepository;
import com.fintrack.fintrack.repository.LedgerEntryRepository;
import com.fintrack.fintrack.repository.UserRepository;

import com.fintrack.fintrack.specification.TransactionSpecification;
import lombok.RequiredArgsConstructor;


import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import com.fintrack.fintrack.entity.Transaction;
import com.fintrack.fintrack.enums.TransactionType;
import com.fintrack.fintrack.repository.TransactionRepository;


@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerRepository;
    private static final Long SYSTEM_ACCOUNT_ID = 999L;
    private Account getAuthorizedAccount(Long accountId) {

        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        if (account.getUser()==null ||!account.getUser().getUsername().equals(username)) {
            throw new RuntimeException("Unauthorized access to account");
        }

        return account;
    }

    // 💳 Create Account
    public Account createAccount() {

        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Account account = new Account();
        account.setUser(user);
        account.setBalance(BigDecimal.ZERO);

        return accountRepository.save(account);
    }

    @Cacheable(value = "balance", key = "#accountId + '_' + T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getName()", sync = true)
    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long accountId) {

        Account account = getAuthorizedAccount(accountId); // 🔥 THIS FIXES EVERYTHING

        BigDecimal credits = ledgerRepository.sumCredits(accountId);
        BigDecimal debits = ledgerRepository.sumDebits(accountId);

        if (credits == null) credits = BigDecimal.ZERO;
        if (debits == null) debits = BigDecimal.ZERO;

        return credits.subtract(debits);
    }
    // 📊 Get All Accounts of User
    public List<AccountResponse> getMyAccounts() {

        String username = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Account> accounts = accountRepository.findByUser_Id(user.getId());

        return accounts.stream().map(account -> {

            BigDecimal credits = ledgerRepository.sumCredits(account.getId());
            BigDecimal debits = ledgerRepository.sumDebits(account.getId());

            if (credits == null) credits = BigDecimal.ZERO;
            if (debits == null) debits = BigDecimal.ZERO;

            BigDecimal balance = credits.subtract(debits);

            return new AccountResponse(account.getId(), balance);

        }).toList();
    }
    @CacheEvict(value = "balance", allEntries = true)
    @Transactional
    public String transfer(Long fromId, Long toId, BigDecimal amount) {

        if (fromId.equals(toId)) {
            throw new RuntimeException("Cannot transfer to same account");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be greater than zero");
        }

        Account from = getAuthorizedAccount(fromId);

        Account to = accountRepository.findById(toId)
                .orElseThrow(() -> new RuntimeException("Receiver account not found"));

        if (from == null) {
            throw new RuntimeException("Sender account not found");
        }

        if (to == null) {
            throw new RuntimeException("Receiver account not found");
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        if (!from.getUser().getUsername().equals(username)) {
            throw new RuntimeException("Unauthorized access to account");
        }

        BigDecimal currentBalance = getBalance(fromId);

        if (currentBalance.compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance");
        }

        // ✅ Transaction AFTER validation
        Transaction tx = new Transaction();
        tx.setFromAccountId(fromId);
        tx.setToAccountId(toId);
        tx.setAmount(amount);
        tx.setType(TransactionType.TRANSFER);
        tx.setTimestamp(LocalDateTime.now());

        transactionRepository.save(tx);

        LedgerEntry debit = new LedgerEntry();
        debit.setAccountId(fromId);
        debit.setAmount(amount);
        debit.setType(EntryType.DEBIT);
        debit.setTransactionId(tx.getId());
        debit.setTimestamp(LocalDateTime.now());

        ledgerRepository.save(debit);

        LedgerEntry credit = new LedgerEntry();
        credit.setAccountId(toId);
        credit.setAmount(amount);
        credit.setType(EntryType.CREDIT);
        credit.setTransactionId(tx.getId());
        credit.setTimestamp(LocalDateTime.now());

        ledgerRepository.save(credit);
        return "Transfer successful";
    }

    @CacheEvict(value = "balance", key = "#accountId")
    @Transactional
    public String credit(Long accountId, BigDecimal amount) {

        Account account = getAuthorizedAccount(accountId);
        if (account == null) {
            throw new RuntimeException("Account not found");
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        if (!account.getUser().getUsername().equals(username)) {
            throw new RuntimeException("Unauthorized access to account");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be greater than zero");
        }

        // 🧾 1. Create transaction
        Transaction tx = new Transaction();
        tx.setFromAccountId(null);
        tx.setToAccountId(accountId);
        tx.setAmount(amount);
        tx.setType(TransactionType.CREDIT);
        tx.setTimestamp(LocalDateTime.now());

        transactionRepository.save(tx); // 🔥 IMPORTANT;

        // 🔺 2. Ledger entry (ONLY THIS matters now)
        // 🔺 CREDIT → user account
        LedgerEntry credit = new LedgerEntry();
        credit.setAccountId(accountId);
        credit.setAmount(amount);
        credit.setType(EntryType.CREDIT);
        credit.setTransactionId(tx.getId());
        credit.setTimestamp(LocalDateTime.now());

        ledgerRepository.save(credit);

// 🔻 DEBIT → system account
        LedgerEntry systemDebit = new LedgerEntry();
        systemDebit.setAccountId(SYSTEM_ACCOUNT_ID);
        systemDebit.setAmount(amount);
        systemDebit.setType(EntryType.DEBIT);
        systemDebit.setTransactionId(tx.getId());
        systemDebit.setTimestamp(LocalDateTime.now());

        ledgerRepository.save(systemDebit);

        return "Amount credited successfully";
    }
    @CacheEvict(value = "balance", key = "#accountId")
    @Transactional
    public String debit(Long accountId, BigDecimal amount) {

        Account account = getAuthorizedAccount(accountId);
        if (account == null) {
            throw new RuntimeException("Account not found");
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        if (!account.getUser().getUsername().equals(username)) {
            throw new RuntimeException("Unauthorized access to account");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be greater than zero");
        }

        BigDecimal balance = getBalance(accountId);

        if (balance.compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance");
        }

        // 🧾 1. Transaction
        Transaction tx = new Transaction();
        tx.setFromAccountId(accountId);
        tx.setAmount(amount);
        tx.setType(TransactionType.DEBIT);
        tx.setTimestamp(LocalDateTime.now());
        tx.setToAccountId(null);

        transactionRepository.save(tx);

        // 🔻 2. Ledger entry
// 🔻 DEBIT → user account
        LedgerEntry debit = new LedgerEntry();
        debit.setAccountId(accountId);
        debit.setAmount(amount);
        debit.setType(EntryType.DEBIT);
        debit.setTransactionId(tx.getId());
        debit.setTimestamp(LocalDateTime.now());

        ledgerRepository.save(debit);

// 🔺 CREDIT → system account
        LedgerEntry systemCredit = new LedgerEntry();
        systemCredit.setAccountId(SYSTEM_ACCOUNT_ID);
        systemCredit.setAmount(amount);
        systemCredit.setType(EntryType.CREDIT);
        systemCredit.setTransactionId(tx.getId());
        systemCredit.setTimestamp(LocalDateTime.now());

        ledgerRepository.save(systemCredit);

        return "Amount debited successfully";
    }
    public Page<TransactionResponse> getTransactionHistory(
            Long accountId,
            int page,
            int size,
            TransactionType type,
            BigDecimal minAmount
    ) {

        // 🔒 authorization
        getAuthorizedAccount(accountId);

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("timestamp").descending()
        );

        Specification<Transaction> spec = Specification
                .where(TransactionSpecification.hasAccount(accountId))
                .and(TransactionSpecification.hasType(type))
                .and(TransactionSpecification.hasMinAmount(minAmount));

        Page<Transaction> transactions =
                transactionRepository.findAll(spec, pageable);

        return transactions.map(TransactionResponse::fromEntity);
    }

    @Cacheable(
            value = "accountSummary",
            key = "#accountId + '_' + (#from != null ? #from.toString() : 'null') + '_' + (#to != null ? #to.toString() : 'null')",
            sync = true
    )
    public AccountSummaryResponse getAccountSummary(
            Long accountId,
            LocalDateTime from,
            LocalDateTime to
    ) {

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        BigDecimal totalCredits;
        BigDecimal totalDebits;
        long transactionCount;

        if (from == null || to == null) {
            // ✅ NO date filter
            totalCredits = ledgerRepository.sumCredits(accountId);
            totalDebits = ledgerRepository.sumDebits(accountId);

            if (totalCredits == null) totalCredits = BigDecimal.ZERO;
            if (totalDebits == null) totalDebits = BigDecimal.ZERO;
            transactionCount = transactionRepository.countTransactions(accountId);
        } else {
            // ✅ WITH date filter
            totalCredits = ledgerRepository.sumCreditsWithDate(accountId,from,to);
            totalDebits = ledgerRepository.sumDebitsWithDate(accountId,from,to);
            if (totalCredits == null) totalCredits = BigDecimal.ZERO;
            if (totalDebits == null) totalDebits = BigDecimal.ZERO;

            transactionCount = transactionRepository.countTransactionsWithDate(accountId, from, to);
        }

        Pageable pageable = PageRequest.of(
                0,
                5,
                Sort.by(Sort.Direction.DESC, "timestamp")
        );

        List<TransactionResponse> recentTransactions =
                transactionRepository.findAllByAccountInvolved(accountId, pageable)
                        .getContent()
                        .stream()
                        .map(this::mapToResponse)
                        .toList();

        return new AccountSummaryResponse(
                accountId,
                getBalance(accountId),
                totalCredits,
                totalDebits,
                transactionCount,
                recentTransactions
        );
    }
    private TransactionResponse mapToResponse(Transaction t) {
        return new TransactionResponse(
                t.getId(),
                t.getFromAccountId(),
                t.getToAccountId(),
                t.getAmount(),
                t.getType(),
                t.getTimestamp().toString()
        );
    }
}