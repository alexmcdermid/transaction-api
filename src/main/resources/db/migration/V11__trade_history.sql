CREATE TABLE trade_history (
    id UUID PRIMARY KEY,
    trade_id UUID NOT NULL,
    action VARCHAR(10) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    symbol VARCHAR(12) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    asset_type VARCHAR(10) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    quantity INTEGER NOT NULL,
    entry_price NUMERIC(18, 4) NOT NULL,
    exit_price NUMERIC(18, 4) NOT NULL,
    fees NUMERIC(18, 2) NOT NULL DEFAULT 0,
    margin_rate NUMERIC(8, 4) NOT NULL DEFAULT 0,
    account_id UUID,
    option_type VARCHAR(10),
    strike_price NUMERIC(18, 4),
    expiry_date DATE,
    opened_at DATE NOT NULL,
    closed_at DATE NOT NULL,
    realized_pnl NUMERIC(18, 2) NOT NULL DEFAULT 0,
    notes TEXT,
    trade_created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    trade_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    action_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trade_history_trade_id ON trade_history(trade_id);
CREATE INDEX idx_trade_history_user_action_at ON trade_history(user_id, action_at DESC);

ALTER TABLE users
    ADD COLUMN show_trade_history BOOLEAN NOT NULL DEFAULT FALSE;
