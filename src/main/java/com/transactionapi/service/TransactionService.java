package com.transactionapi.service;

import com.transactionapi.constants.TransactionType;
import com.transactionapi.dto.CreateTransactionRequest;
import com.transactionapi.dto.TransactionResponse;
import com.transactionapi.model.Account;
import com.transactionapi.model.Transaction;
import com.transactionapi.repository.TransactionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
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

    public TransactionResponse createTransaction(@NonNull UUID accountId, CreateTransactionRequest request, String userId) {
        Account account = accountService.loadOwnedAccount(Objects.requireNonNull(accountId), userId);

        Transaction transaction = new Transaction();
        transaction.setAccount(account);
        transaction.setType(request.type());
        transaction.setAmount(normalizeAmountForType(request.type(), request.amount(), false));
        transaction.setCurrency(request.currency() != null ? request.currency() : account.getCurrency());
        transaction.setTicker(request.ticker());
        transaction.setName(request.name());
        transaction.setExchange(request.exchange());
        transaction.setQuantity(request.quantity());
        transaction.setPrice(request.price());
        transaction.setOptionType(request.optionType());
        transaction.setStrikePrice(request.strikePrice());
        transaction.setExpiryDate(request.expiryDate());
        transaction.setFee(request.fee());
        transaction.setOccurredAt(request.occurredAt());
        transaction.setNotes(request.notes());

        if (request.relatedTransactionId() != null) {
            Transaction related = transactionRepository.findById(Objects.requireNonNull(request.relatedTransactionId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Related transaction not found"));
            String relatedUser = related.getAccount().getUserId();
            if (!relatedUser.equals(userId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Related transaction does not belong to user");
            }
            transaction.setRelatedTransaction(related);
        }

        Transaction saved = transactionRepository.save(transaction);

        if (request.type() == TransactionType.TRANSFER && request.targetAccountId() != null) {
            Account target = accountService.loadOwnedAccount(request.targetAccountId(), userId);
            Transaction mirror = new Transaction();
            mirror.setAccount(target);
            mirror.setType(TransactionType.TRANSFER);
            mirror.setAmount(normalizeAmountForType(TransactionType.TRANSFER, request.amount(), true));
            mirror.setCurrency(target.getCurrency());
            mirror.setTicker(request.ticker());
            mirror.setName(request.name());
            mirror.setExchange(request.exchange());
            mirror.setQuantity(request.quantity());
            mirror.setPrice(request.price());
            mirror.setOptionType(request.optionType());
            mirror.setStrikePrice(request.strikePrice());
            mirror.setExpiryDate(request.expiryDate());
            mirror.setFee(request.fee());
            mirror.setOccurredAt(request.occurredAt());
            mirror.setNotes(request.notes());
            mirror.setRelatedTransaction(transaction);
            Transaction savedMirror = transactionRepository.save(mirror);
            saved.setRelatedTransaction(savedMirror);
            transactionRepository.save(saved);
        }

        return toResponse(saved);
    }

    public List<TransactionResponse> listTransactions(UUID accountId, String userId) {
        accountService.loadOwnedAccount(Objects.requireNonNull(accountId), userId);
        return transactionRepository.findByAccountIdOrderByOccurredAtDesc(accountId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public void deleteTransaction(UUID accountId, UUID transactionId, String userId) {
        Account account = accountService.loadOwnedAccount(Objects.requireNonNull(accountId), userId);
        Transaction tx = transactionRepository.findById(Objects.requireNonNull(transactionId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        if (!tx.getAccount().getId().equals(account.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found");
        }
        Transaction related = tx.getRelatedTransaction();
        transactionRepository.delete(tx);
        if (related != null) {
            transactionRepository.delete(related);
        }
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
                transaction.getFee(),
                relatedId,
                transaction.getOccurredAt(),
                transaction.getNotes(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }

    private BigDecimal normalizeAmountForType(TransactionType type, BigDecimal amount, boolean incomingTransfer) {
        if (amount == null) {
            return null;
        }
        BigDecimal abs = amount.abs();
        if (abs.signum() == 0) {
            return abs;
        }
        boolean shouldBeNegative = switch (type) {
            case BUY, WITHDRAWAL, FEE -> true;
            case TRANSFER -> !incomingTransfer;
            default -> false;
        };
        return shouldBeNegative ? abs.negate() : abs;
    }
}
