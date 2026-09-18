package com.arguspay.core.service;

import com.arguspay.core.entity.Account;
import com.arguspay.core.entity.Transaction;
import com.arguspay.core.entity.TransactionStatus;
import com.arguspay.core.entity.TransactionType;
import com.arguspay.core.exception.AccountAccessDeniedException;
import com.arguspay.core.exception.DuplicateIdempotencyKeyException;
import com.arguspay.core.exception.InsufficientFundsException;
import com.arguspay.core.exception.InvalidTransferException;
import com.arguspay.core.repository.AccountRepository;
import com.arguspay.core.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Account createAccount(UUID ownerId) {
        Account account = new Account(BigDecimal.ZERO, ownerId);
        return accountRepository.save(account);
    }

    public Account getAccount(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Account> listAccounts(UUID userId) {
        return accountRepository.findByOwnerIdOrderByCreatedAtDesc(userId);
    }

    public Account getOwnedAccount(UUID accountId, UUID userId) {
        Account account = getAccount(accountId);
        if (!userId.equals(account.getOwnerId())) {
            throw new AccountAccessDeniedException("You do not have access to this account");
        }
        return account;
    }

    @Transactional
    public Account deposit(UUID userId, UUID accountId, BigDecimal amount, String idempotencyKey) {
        Account account = getOwnedAccount(accountId, userId);
        checkIdempotency(idempotencyKey);

        Transaction transaction = new Transaction(accountId, amount, TransactionType.DEPOSIT, idempotencyKey);
        transaction = transactionRepository.save(transaction);

        account.setBalance(account.getBalance().add(amount));
        account = accountRepository.save(account);

        transaction.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(transaction);

        return account;
    }

    @Transactional
    public Account withdraw(UUID userId, UUID accountId, BigDecimal amount, String idempotencyKey) {
        Account account = getOwnedAccount(accountId, userId);
        checkIdempotency(idempotencyKey);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds in account " + accountId + " for withdrawal of " + amount);
        }

        Transaction transaction = new Transaction(accountId, amount, TransactionType.WITHDRAWAL, idempotencyKey);
        transaction = transactionRepository.save(transaction);

        account.setBalance(account.getBalance().subtract(amount));
        account = accountRepository.save(account);

        transaction.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(transaction);

        return account;
    }

    @Transactional
    public void transfer(UUID userId, UUID fromAccountId, UUID toAccountId, BigDecimal amount, String idempotencyKey) {
        Account fromAccount = getOwnedAccount(fromAccountId, userId);

        if (fromAccountId.equals(toAccountId)) {
            throw new InvalidTransferException("Cannot transfer to the same account");
        }

        checkIdempotency(idempotencyKey);

        Account toAccount = getAccount(toAccountId);

        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds in account " + fromAccountId + " for transfer of " + amount);
        }

        String outKey = idempotencyKey;
        String inKey = idempotencyKey != null ? idempotencyKey + "-credit" : null;

        Transaction outTransaction = new Transaction(
                fromAccountId, amount, TransactionType.TRANSFER_OUT, outKey, toAccountId);
        outTransaction = transactionRepository.save(outTransaction);
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        accountRepository.save(fromAccount);
        outTransaction.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(outTransaction);

        Transaction inTransaction = new Transaction(
                toAccountId, amount, TransactionType.TRANSFER_IN, inKey, fromAccountId);
        inTransaction = transactionRepository.save(inTransaction);
        toAccount.setBalance(toAccount.getBalance().add(amount));
        accountRepository.save(toAccount);
        inTransaction.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(inTransaction);
    }

    @Transactional(readOnly = true)
    public List<Transaction> getTransactionHistory(UUID userId, UUID accountId) {
        getOwnedAccount(accountId, userId);
        return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    private void checkIdempotency(String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()
                && transactionRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            throw new DuplicateIdempotencyKeyException(
                    "A transaction with this idempotency key already exists: " + idempotencyKey);
        }
    }
}