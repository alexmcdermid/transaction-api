CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    name VARCHAR(120) NOT NULL,
    default_stock_fees NUMERIC(18, 2) NOT NULL DEFAULT 0,
    default_option_fees NUMERIC(18, 2) NOT NULL DEFAULT 0,
    default_margin_rate_usd NUMERIC(8, 4) NOT NULL DEFAULT 0,
    default_margin_rate_cad NUMERIC(8, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_accounts_user_id ON accounts(user_id);

ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS account_id UUID;

CREATE INDEX idx_trades_account_id ON trades(account_id);

ALTER TABLE trades
    ADD CONSTRAINT fk_trades_account
    FOREIGN KEY (account_id)
    REFERENCES accounts(id)
    ON DELETE SET NULL;
