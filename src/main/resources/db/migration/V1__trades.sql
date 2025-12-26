CREATE TABLE trades (
    id UUID PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    symbol VARCHAR(12) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    asset_type VARCHAR(10) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    quantity INTEGER NOT NULL,
    entry_price NUMERIC(18, 4) NOT NULL,
    exit_price NUMERIC(18, 4) NOT NULL,
    fees NUMERIC(18, 2) NOT NULL DEFAULT 0,
    option_type VARCHAR(10),
    strike_price NUMERIC(18, 4),
    expiry_date DATE,
    opened_at DATE NOT NULL,
    closed_at DATE NOT NULL,
    realized_pnl NUMERIC(18, 2) NOT NULL DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trades_user_id ON trades(user_id);
CREATE INDEX idx_trades_closed_at ON trades(closed_at);
