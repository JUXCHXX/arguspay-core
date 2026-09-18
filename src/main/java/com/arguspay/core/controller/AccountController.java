package com.arguspay.core.controller;

import com.arguspay.core.dto.BalanceResponse;
import com.arguspay.core.dto.CreateAccountResponse;
import com.arguspay.core.dto.DepositRequest;
import com.arguspay.core.dto.TransactionResponse;
import com.arguspay.core.dto.TransferRequest;
import com.arguspay.core.dto.WithdrawRequest;
import com.arguspay.core.entity.Account;
import com.arguspay.core.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    @PostMapping
    public ResponseEntity<CreateAccountResponse> createAccount(@AuthenticationPrincipal Jwt jwt) {
        Account account = accountService.createAccount(userId(jwt));
        return ResponseEntity.ok(new CreateAccountResponse(account.getId(), account.getBalance()));
    }

    @GetMapping
    public List<BalanceResponse> listAccounts(@AuthenticationPrincipal Jwt jwt) {
        return accountService.listAccounts(userId(jwt)).stream()
                .map(account -> new BalanceResponse(account.getId(), account.getBalance()))
                .toList();
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        Account account = accountService.getOwnedAccount(id, userId(jwt));
        return ResponseEntity.ok(new BalanceResponse(account.getId(), account.getBalance()));
    }

    @PostMapping("/{id}/deposit")
    public BalanceResponse deposit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody DepositRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader) {
        String idempotencyKey = request.idempotencyKey() != null
                ? request.idempotencyKey()
                : idempotencyKeyHeader;

        Account account = accountService.deposit(userId(jwt), id, request.amount(), idempotencyKey);
        return new BalanceResponse(account.getId(), account.getBalance());
    }

    @PostMapping("/{id}/withdraw")
    public BalanceResponse withdraw(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody WithdrawRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader) {
        String idempotencyKey = request.idempotencyKey() != null
                ? request.idempotencyKey()
                : idempotencyKeyHeader;
        Account account = accountService.withdraw(userId(jwt), id, request.amount(), idempotencyKey);
        return new BalanceResponse(account.getId(), account.getBalance());
    }

    @PostMapping("/{id}/transfer")
    public ResponseEntity<Void> transfer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader) {
        String idempotencyKey = request.idempotencyKey() != null
                ? request.idempotencyKey()
                : idempotencyKeyHeader;
        accountService.transfer(userId(jwt), id, request.toAccountId(), request.amount(), idempotencyKey);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/transactions")
    public List<TransactionResponse> getTransactions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        return accountService.getTransactionHistory(userId(jwt), id).stream()
                .map(transaction -> new TransactionResponse(
                        transaction.getId(),
                        transaction.getAmount(),
                        transaction.getType(),
                        transaction.getStatus(),
                        transaction.getRelatedAccountId(),
                        transaction.getCreatedAt()))
                .toList();
    }
}