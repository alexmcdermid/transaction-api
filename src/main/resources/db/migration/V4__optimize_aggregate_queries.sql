CREATE INDEX IF NOT EXISTS idx_trades_user_closed 
ON trades(user_id, closed_at DESC);

CREATE INDEX IF NOT EXISTS idx_trades_user_currency_closed 
ON trades(user_id, currency, closed_at);
