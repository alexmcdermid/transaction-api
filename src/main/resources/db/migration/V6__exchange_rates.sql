CREATE TABLE exchange_rates (
    id UUID PRIMARY KEY,
    base_currency VARCHAR(3) NOT NULL,
    quote_currency VARCHAR(3) NOT NULL,
    rate NUMERIC(18, 6) NOT NULL,
    effective_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_exchange_rates_pair_date ON exchange_rates(base_currency, quote_currency, effective_date);
CREATE INDEX idx_exchange_rates_effective_date ON exchange_rates(effective_date);
