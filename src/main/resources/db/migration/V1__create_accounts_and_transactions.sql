CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    name VARCHAR(100) NOT NULL,
    institution VARCHAR(100),
    type VARCHAR(20) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'CAD',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_accounts_user_id ON accounts(user_id);

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    type VARCHAR(20) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    ticker VARCHAR(4),
    name VARCHAR(255),
    currency VARCHAR(3) NOT NULL DEFAULT 'CAD',
    exchange VARCHAR(20),
    quantity INTEGER,
    price NUMERIC(18, 4),
    option_type VARCHAR(10),
    strike_price NUMERIC(18, 4),
    expiry_date DATE,
    underlying_ticker VARCHAR(4),
    fee NUMERIC(18, 2),
    related_transaction_id UUID,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_related_transaction FOREIGN KEY (related_transaction_id) REFERENCES transactions(id)
);

CREATE INDEX idx_transactions_account_id ON transactions(account_id);
CREATE INDEX idx_transactions_related_transaction_id ON transactions(related_transaction_id);
