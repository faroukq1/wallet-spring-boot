-- The demo seed (V2) inserts rows with explicit IDs, which does NOT advance the
-- BIGSERIAL sequence. The first generated ID would therefore collide with the
-- seeded rows (1, 2, 3). Sync the sequence with the current max ID.
SELECT setval(pg_get_serial_sequence('accounts', 'id'), (SELECT MAX(id) FROM accounts));

-- Idempotency: a client-supplied key makes a transfer safe to retry.
-- Non-idempotent operations leave the column NULL; PostgreSQL unique
-- constraints ignore NULLs, so only keyed transfers are deduplicated.
ALTER TABLE transactions ADD COLUMN idempotency_key VARCHAR(64);
CREATE UNIQUE INDEX ux_transactions_idempotency_key
    ON transactions (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
