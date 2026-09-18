package com.arguspay.core.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateAccountResponse(UUID id, BigDecimal balance) {
}
