package com.arguspay.core.dto;

import com.arguspay.core.entity.TransactionStatus;
import com.arguspay.core.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        BigDecimal amount,
        TransactionType type,
        TransactionStatus status,
        UUID relatedAccountId,
        LocalDateTime createdAt
) {}