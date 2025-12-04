package com.transactionapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.transactionapi.constants.AccountStatus;
import com.transactionapi.constants.AccountType;
import com.transactionapi.constants.Currency;
import com.transactionapi.dto.AccountResponse;
import com.transactionapi.dto.CreateAccountRequest;
import com.transactionapi.model.Account;
import com.transactionapi.repository.AccountRepository;
import com.transactionapi.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
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
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, transactionRepository);
    }

    @Test
    void createAccountSetsFields() {
        when(transactionRepository.sumAmountsByAccountId(any(UUID.class))).thenReturn(BigDecimal.ZERO);
        CreateAccountRequest request = new CreateAccountRequest("Checking", AccountType.BANK, Currency.CAD, "Bank");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account acc = Objects.requireNonNull(invocation.getArgument(0, Account.class));
            try {
                setAccountId(acc, UUID.randomUUID());
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            acc.setCurrency(Currency.CAD);
            acc.setStatus(AccountStatus.ACTIVE);
            return acc;
        });

        AccountResponse response = accountService.createAccount(request, "user-1");

        assertThat(response.name()).isEqualTo("Checking");
        assertThat(response.type()).isEqualTo(AccountType.BANK);
        assertThat(response.currency()).isEqualTo(Currency.CAD);
        assertThat(response.balance()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void listAccountsReturnsOnlyUserAccounts() {
        Account a1 = account("user-1", UUID.randomUUID(), "A");
        Account a2 = account("user-1", UUID.randomUUID(), "B");
        when(accountRepository.findByUserIdOrderByCreatedAtAsc("user-1")).thenReturn(List.of(a1, a2));
        when(transactionRepository.sumAmountsByAccountId(a1.getId())).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumAmountsByAccountId(a2.getId())).thenReturn(BigDecimal.ZERO);

        List<AccountResponse> accounts = accountService.listAccounts("user-1");

        assertThat(accounts).hasSize(2);
        assertThat(accounts).extracting(AccountResponse::id).containsExactly(a1.getId(), a2.getId());
    }

    @Test
    void loadOwnedAccountThrowsForWrongUser() {
        UUID id = UUID.randomUUID();
        Account other = account("other", id, "Other");
        UUID otherId = Objects.requireNonNull(other.getId());
        when(accountRepository.findById(otherId)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> accountService.loadOwnedAccount(otherId, "user-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @NonNull
    private Account account(String userId, UUID id, String name) {
        Account acc = new Account();
        acc.setUserId(userId);
        acc.setInstitution("Bank");
        acc.setCurrency(Currency.CAD);
        acc.setName(name);
        acc.setType(AccountType.BANK);
        try {
            setAccountId(acc, id);
            var createdField = Account.class.getDeclaredField("createdAt");
            createdField.setAccessible(true);
            createdField.set(acc, Instant.now());
            var updatedField = Account.class.getDeclaredField("updatedAt");
            updatedField.setAccessible(true);
            updatedField.set(acc, Instant.now());
        } catch (ReflectiveOperationException ignored) {
        }
        return acc;
    }

    private void setAccountId(Account account, UUID id) throws ReflectiveOperationException {
        var idField = Account.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(account, id);
    }
}
