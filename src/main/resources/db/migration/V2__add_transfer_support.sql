-- Permite enlazar una transacción con la cuenta contraparte en una transferencia
ALTER TABLE transaction ADD COLUMN related_account_id UUID NULL;

CREATE INDEX idx_transaction_related_account_id ON transaction (related_account_id);