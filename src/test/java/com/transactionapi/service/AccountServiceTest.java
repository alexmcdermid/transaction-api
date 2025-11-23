package com.transactionapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.transactionapi.constants.AccountStatus;
import com.transactionapi.constants.AccountType;
import com.transactionapi.dto.AccountResponse;
import com.transactionapi.dto.CreateAccountRequest;
import com.transactionapi.model.Account;
import com.transactionapi.repository.AccountRepository;
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
@SuppressWarnings("null")
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository);
    }

    @Test
    void createAccountSetsFields() {
        CreateAccountRequest request = new CreateAccountRequest("Checking", AccountType.BANK, "cad", "Bank");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account acc = Objects.requireNonNull(invocation.getArgument(0, Account.class));
            acc.setCurrency(acc.getCurrency().toUpperCase());
            acc.setStatus(AccountStatus.ACTIVE);
            return acc;
        });

        AccountResponse response = accountService.createAccount(request, "user-1");

        assertThat(response.name()).isEqualTo("Checking");
        assertThat(response.type()).isEqualTo(AccountType.BANK);
        assertThat(response.currency()).isEqualTo("CAD");
    }

    @Test
    void listAccountsReturnsOnlyUserAccounts() {
        Account a1 = account("user-1", UUID.randomUUID(), "A");
        Account a2 = account("user-1", UUID.randomUUID(), "B");
        when(accountRepository.findByUserIdOrderByCreatedAtAsc("user-1")).thenReturn(List.of(a1, a2));

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
        acc.setCurrency("CAD");
        acc.setName(name);
        acc.setType(AccountType.BANK);
        try {
            var idField = Account.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(acc, id);

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
}
