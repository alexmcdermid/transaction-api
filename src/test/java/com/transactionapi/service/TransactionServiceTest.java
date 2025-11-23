package com.transactionapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.transactionapi.constants.OptionType;
import com.transactionapi.constants.TransactionType;
import com.transactionapi.dto.CreateTransactionRequest;
import com.transactionapi.dto.TransactionResponse;
import com.transactionapi.model.Account;
import com.transactionapi.model.Transaction;
import com.transactionapi.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountService accountService;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(transactionRepository, accountService);
    }

    @Test
    void createTransactionAssignsRelatedWhenSameUser() {
        Account account = account(UUID.randomUUID(), "user-1");
        Transaction related = transaction(UUID.randomUUID(), account);
        UUID relatedId = Objects.requireNonNull(related.getId());

        when(accountService.loadOwnedAccount(account.getId(), "user-1")).thenReturn(account);
        when(transactionRepository.findById(relatedId)).thenReturn(Optional.of(related));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = Objects.requireNonNull(invocation.getArgument(0, Transaction.class));
            tx.setOccurredAt(Instant.now());
            return tx;
        });

        CreateTransactionRequest request = new CreateTransactionRequest(
                TransactionType.DEPOSIT,
                new BigDecimal("100.00"),
                null,
                null,
                null,
                null,
                null,
                null,
                OptionType.CALL,
                new BigDecimal("10.00"),
                LocalDate.parse("2024-06-21"),
                "AAPL",
                null,
                relatedId,
                Instant.parse("2024-01-01T00:00:00Z"),
                "note"
        );

        TransactionResponse response = transactionService.createTransaction(account.getId(), request, "user-1");

        assertThat(response.relatedTransactionId()).isEqualTo(relatedId);
        assertThat(response.amount()).isEqualByComparingTo("100.00");
        assertThat(response.type()).isEqualTo(TransactionType.DEPOSIT);
    }

    @Test
    void createTransactionRejectsRelatedForOtherUser() {
        Account account = account(UUID.randomUUID(), "user-1");
        Account otherAccount = account(UUID.randomUUID(), "other");
        Transaction related = transaction(UUID.randomUUID(), otherAccount);
        UUID relatedId = Objects.requireNonNull(related.getId());

        when(accountService.loadOwnedAccount(account.getId(), "user-1")).thenReturn(account);
        when(transactionRepository.findById(relatedId)).thenReturn(Optional.of(related));

        CreateTransactionRequest request = new CreateTransactionRequest(
                TransactionType.DEPOSIT,
                new BigDecimal("50"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                relatedId,
                Instant.now(),
                null
        );

        assertThatThrownBy(() -> transactionService.createTransaction(account.getId(), request, "user-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    void listTransactionsDelegatesToRepository() {
        Account account = account(UUID.randomUUID(), "user-1");
        Transaction tx = transaction(UUID.randomUUID(), account);
        when(accountService.loadOwnedAccount(account.getId(), "user-1")).thenReturn(account);
        when(transactionRepository.findByAccountIdOrderByOccurredAtDesc(account.getId())).thenReturn(List.of(tx));

        List<TransactionResponse> responses = transactionService.listTransactions(account.getId(), "user-1");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(tx.getId());
    }

    @NonNull
    private Account account(UUID id, String userId) {
        Account account = new Account();
        account.setUserId(userId);
        account.setCurrency("CAD");
        account.setName("Name");
        try {
            var idField = Account.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(account, id);
        } catch (ReflectiveOperationException ignored) {
        }
        return account;
    }

    @NonNull
    private Transaction transaction(UUID id, Account account) {
        Transaction tx = new Transaction();
        tx.setAccount(account);
        tx.setType(TransactionType.DEPOSIT);
        tx.setAmount(new BigDecimal("1.00"));
        tx.setOccurredAt(Instant.now());
        try {
            var idField = Transaction.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(tx, id);
        } catch (ReflectiveOperationException ignored) {
        }
        return tx;
    }
}
