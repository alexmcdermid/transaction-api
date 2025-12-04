package com.transactionapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transactionapi.constants.Currency;
import com.transactionapi.constants.Exchange;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
            try {
                setTransactionId(tx, UUID.randomUUID());
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            tx.setOccurredAt(Instant.now());
            return tx;
        });

        CreateTransactionRequest request = new CreateTransactionRequest(
                TransactionType.DEPOSIT,
                new BigDecimal("100.00"),
                null,
                null,
                Currency.CAD,
                Exchange.TSX,
                null,
                null,
                OptionType.CALL,
                new BigDecimal("10.00"),
                LocalDate.parse("2024-06-21"),
                null,
                relatedId,
                null,
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
                Currency.CAD,
                Exchange.TSX,
                null,
                null,
                null,
                null,
                null,
                null,
                relatedId,
                null,
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

    @Test
    void transferCreatesMirrorTransaction() throws Exception {
        Account source = account(UUID.randomUUID(), "user-1");
        Account target = account(UUID.randomUUID(), "user-1");
        when(accountService.loadOwnedAccount(source.getId(), "user-1")).thenReturn(source);
        when(accountService.loadOwnedAccount(target.getId(), "user-1")).thenReturn(target);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0, Transaction.class);
            setTransactionId(tx, UUID.randomUUID());
            tx.setOccurredAt(Instant.now());
            return tx;
        });

        CreateTransactionRequest request = new CreateTransactionRequest(
                TransactionType.TRANSFER,
                new BigDecimal("100.00"),
                null,
                null,
                Currency.CAD,
                Exchange.TSX,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                target.getId(),
                Instant.now(),
                null
        );

        TransactionResponse response = transactionService.createTransaction(source.getId(), request, "user-1");

        verify(transactionRepository, times(3)).save(txCaptor.capture());
        List<Transaction> saved = txCaptor.getAllValues();
        Transaction sourceTx = saved.get(0);
        Transaction mirrorTx = saved.get(1);
        Transaction updatedSource = saved.get(2);

        assertThat(sourceTx.getAccount().getId()).isEqualTo(source.getId());
        assertThat(sourceTx.getAmount()).isEqualByComparingTo("-100.00");
        assertThat(mirrorTx.getAccount().getId()).isEqualTo(target.getId());
        assertThat(mirrorTx.getAmount()).isEqualByComparingTo("100.00");
        assertThat(mirrorTx.getRelatedTransaction()).isEqualTo(sourceTx);
        assertThat(updatedSource.getRelatedTransaction()).isEqualTo(mirrorTx);

        Assertions.assertNotNull(response.id());
    }

    @NonNull
    private Account account(UUID id, String userId) {
        Account account = new Account();
        account.setUserId(userId);
        account.setCurrency(Currency.CAD);
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
            setTransactionId(tx, id);
        } catch (ReflectiveOperationException ignored) {
        }
        return tx;
    }

    private void setTransactionId(Transaction tx, UUID id) throws ReflectiveOperationException {
        var idField = Transaction.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(tx, id);
    }
}
