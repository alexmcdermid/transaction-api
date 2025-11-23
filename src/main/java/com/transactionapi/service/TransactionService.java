package com.transactionapi.service;

import com.transactionapi.dto.CreateTransactionRequest;
import com.transactionapi.dto.TransactionResponse;
import com.transactionapi.model.Account;
import com.transactionapi.model.Transaction;
import com.transactionapi.repository.TransactionRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;

    public TransactionService(TransactionRepository transactionRepository, AccountService accountService) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
    }

    public TransactionResponse createTransaction(UUID accountId, CreateTransactionRequest request, String userId) {
        Account account = accountService.loadOwnedAccount(accountId, userId);

        Transaction transaction = new Transaction();
        transaction.setAccount(account);
        transaction.setType(request.type());
        transaction.setAmount(request.amount());
        if (request.currency() != null) {
            transaction.setCurrency(request.currency());
        } else {
            transaction.setCurrency(account.getCurrency());
        }
        transaction.setTicker(request.ticker());
        transaction.setName(request.name());
        transaction.setExchange(request.exchange());
        transaction.setQuantity(request.quantity());
        transaction.setPrice(request.price());
        transaction.setOptionType(request.optionType());
        transaction.setStrikePrice(request.strikePrice());
        transaction.setExpiryDate(request.expiryDate());
        transaction.setUnderlyingTicker(request.underlyingTicker());
        transaction.setFee(request.fee());
        transaction.setOccurredAt(request.occurredAt());
        transaction.setNotes(request.notes());

        if (request.relatedTransactionId() != null) {
            Transaction related = transactionRepository.findById(request.relatedTransactionId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Related transaction not found"));
            String relatedUser = related.getAccount().getUserId();
            if (!relatedUser.equals(userId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Related transaction does not belong to user");
            }
            transaction.setRelatedTransaction(related);
        }

        Transaction saved = transactionRepository.save(transaction);
        return toResponse(saved);
    }

    public List<TransactionResponse> listTransactions(UUID accountId, String userId) {
        accountService.loadOwnedAccount(accountId, userId);
        return transactionRepository.findByAccountIdOrderByOccurredAtDesc(accountId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private TransactionResponse toResponse(Transaction transaction) {
        UUID relatedId = transaction.getRelatedTransaction() != null
                ? transaction.getRelatedTransaction().getId()
                : null;
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccount().getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getTicker(),
                transaction.getName(),
                transaction.getCurrency(),
                transaction.getExchange(),
                transaction.getQuantity(),
                transaction.getPrice(),
                transaction.getOptionType(),
                transaction.getStrikePrice(),
                transaction.getExpiryDate(),
                transaction.getUnderlyingTicker(),
                transaction.getFee(),
                relatedId,
                transaction.getOccurredAt(),
                transaction.getNotes(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }
}
