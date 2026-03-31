package com.fintrack.fintrack.service;

import com.fintrack.fintrack.entity.Account;
import com.fintrack.fintrack.entity.LedgerEntry;
import com.fintrack.fintrack.entity.Transaction;
import com.fintrack.fintrack.entity.User;
import com.fintrack.fintrack.repository.AccountRepository;
import com.fintrack.fintrack.repository.LedgerEntryRepository;
import com.fintrack.fintrack.repository.TransactionRepository;
import com.fintrack.fintrack.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository ledgerRepository;

    @Mock
    private UserRepository userRepository; // optional for credit, but good practice
    @InjectMocks
    private AccountService accountService;

    @Test
    void testCredit_success() {

        // 🔐 Mock security context
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("testuser");

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);

        SecurityContextHolder.setContext(securityContext);

        // 👤 Mock user + account
        User user = new User();
        user.setUsername("testuser");

        Account account = new Account();
        account.setId(1L);
        account.setUser(user);

        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(account));

        // 🚀 Call method
        String result = accountService.credit(1L, BigDecimal.valueOf(500));

        // ✅ Assertions
        assertEquals("Amount credited successfully", result);

        // 🔍 Verify interactions
        verify(transactionRepository).save(any(Transaction.class));
        verify(ledgerRepository).save(any(LedgerEntry.class));
    }
    @Test
    void testCredit_invalidAmount() {

        // 🔐 Security
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("testuser");

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // 👤 Account
        User user = new User();
        user.setUsername("testuser");

        Account account = new Account();
        account.setId(1L);
        account.setUser(user);

        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(account));

        // ❌ Act + Assert
        assertThrows(RuntimeException.class, () -> {
            accountService.credit(1L, BigDecimal.ZERO);
        });

        // 🔍 Ensure nothing was saved
        verify(transactionRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }
    @Test
    void testCredit_unauthorizedUser() {

        // 🔐 Logged-in user
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("wronguser");

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // 👤 Actual account owner
        User user = new User();
        user.setUsername("realuser");

        Account account = new Account();
        account.setId(1L);
        account.setUser(user);

        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(account));

        // ❌ Act + Assert
        assertThrows(RuntimeException.class, () -> {
            accountService.credit(1L, BigDecimal.valueOf(500));
        });

        // 🔍 Ensure nothing was saved
        verify(transactionRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }
    @Test
    void testCredit_accountNotFound() {

        // 🔐 Security
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("testuser");

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        when(accountRepository.findById(1L))
                .thenReturn(Optional.empty());

        // ❌ Act + Assert
        assertThrows(RuntimeException.class, () -> {
            accountService.credit(1L, BigDecimal.valueOf(500));
        });

        // 🔍 Ensure nothing was saved
        verify(transactionRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }
    @Test
    void testDebit_success() {

        // 🔐 Security
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("testuser");

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // 👤 Account
        User user = new User();
        user.setUsername("testuser");

        Account account = new Account();
        account.setId(1L);
        account.setUser(user);

        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(account));

        // 💰 Mock balance = 1000
        when(ledgerRepository.sumCredits(1L))
                .thenReturn(BigDecimal.valueOf(1000));
        when(ledgerRepository.sumDebits(1L))
                .thenReturn(BigDecimal.ZERO);

        // 🚀 Call
        String result = accountService.debit(1L, BigDecimal.valueOf(300));

        // ✅ Assert
        assertEquals("Amount debited successfully", result);

        verify(transactionRepository).save(any(Transaction.class));
        verify(ledgerRepository).save(any(LedgerEntry.class));
    }
    @Test
    void testDebit_insufficientBalance() {

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("testuser");

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        User user = new User();
        user.setUsername("testuser");

        Account account = new Account();
        account.setId(1L);
        account.setUser(user);

        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(account));

        // 💥 Balance = 100, trying to debit 500
        when(ledgerRepository.sumCredits(1L))
                .thenReturn(BigDecimal.valueOf(100));
        when(ledgerRepository.sumDebits(1L))
                .thenReturn(BigDecimal.ZERO);

        assertThrows(RuntimeException.class, () -> {
            accountService.debit(1L, BigDecimal.valueOf(500));
        });
    }
    @Test
    void testDebit_invalidAmount() {

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("testuser");

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        User user = new User();
        user.setUsername("testuser");

        Account account = new Account();
        account.setId(1L);
        account.setUser(user);

        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(account));

        assertThrows(RuntimeException.class, () -> {
            accountService.debit(1L, BigDecimal.ZERO);
        });
    }
    @Test
    void testTransfer_success() {

        // 🔐 Security
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("testuser");

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // 👤 Users
        User user = new User();
        user.setUsername("testuser");

        // 💳 Accounts
        Account from = new Account();
        from.setId(1L);
        from.setUser(user);
        from.setBalance(BigDecimal.valueOf(1000));

        Account to = new Account();
        to.setId(2L);
        to.setUser(user);
        to.setBalance(BigDecimal.valueOf(500));

        when(accountRepository.findById(1L)).thenReturn(Optional.of(from));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(to));

        // 💰 Balance mock (IMPORTANT)
        when(ledgerRepository.sumCredits(1L))
                .thenReturn(BigDecimal.valueOf(1000));
        when(ledgerRepository.sumDebits(1L))
                .thenReturn(BigDecimal.ZERO);

        // 🚀 Call
        String result = accountService.transfer(1L, 2L, BigDecimal.valueOf(300));

        // ✅ Assertions
        assertEquals("Transfer successful", result);
        assertEquals(BigDecimal.valueOf(700), from.getBalance());
        assertEquals(BigDecimal.valueOf(800), to.getBalance());

        // 🔍 Verify writes
        verify(transactionRepository).save(any(Transaction.class));
        verify(ledgerRepository, times(2)).save(any(LedgerEntry.class));
        verify(accountRepository).save(from);
        verify(accountRepository).save(to);
    }
    @Test
    void testTransfer_sameAccount() {

        assertThrows(RuntimeException.class, () -> {
            accountService.transfer(1L, 1L, BigDecimal.valueOf(100));
        });
    }
    @Test
    void testTransfer_insufficientBalance() {

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("testuser");

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        User user = new User();
        user.setUsername("testuser");

        Account from = new Account();
        from.setId(1L);
        from.setUser(user);

        Account to = new Account();
        to.setId(2L);
        to.setUser(user);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(from));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(to));

        // 💥 Balance = 100, trying to send 500
        when(ledgerRepository.sumCredits(1L))
                .thenReturn(BigDecimal.valueOf(100));
        when(ledgerRepository.sumDebits(1L))
                .thenReturn(BigDecimal.ZERO);

        assertThrows(RuntimeException.class, () -> {
            accountService.transfer(1L, 2L, BigDecimal.valueOf(500));
        });
    }
    @Test
    void testTransfer_invalidAmount() {

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("testuser");

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        User user = new User();
        user.setUsername("testuser");

        Account from = new Account();
        from.setId(1L);
        from.setUser(user);

        Account to = new Account();
        to.setId(2L);
        to.setUser(user);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(from));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(to));

        assertThrows(RuntimeException.class, () -> {
            accountService.transfer(1L, 2L, BigDecimal.ZERO);
        });
    }

}