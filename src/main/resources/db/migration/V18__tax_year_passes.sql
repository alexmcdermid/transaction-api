-- One row per tax year pass purchase attempt. A PAID row unlocks reports and exports for that
-- user's tax year. The row id doubles as the Stripe Payment Link client_reference_id.
-- The CHECKs make a typo in hand-written support SQL fail here instead of breaking the user's pages
-- when Hibernate reads the row back. Keep the status list in sync with TaxYearPassStatus.
CREATE TABLE tax_year_passes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    tax_year INTEGER NOT NULL CHECK (tax_year >= 2000),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'PAID', 'FAILED', 'REFUNDED')),
    stripe_checkout_session_id VARCHAR(255),
    stripe_payment_intent_id VARCHAR(255),
    stripe_customer_id VARCHAR(255),
    amount_total BIGINT,
    currency VARCHAR(3),
    paid_at TIMESTAMP WITH TIME ZONE,
    refunded_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tax_year_passes_user_year ON tax_year_passes(user_id, tax_year);
CREATE UNIQUE INDEX idx_tax_year_passes_checkout_session ON tax_year_passes(stripe_checkout_session_id);
CREATE INDEX idx_tax_year_passes_payment_intent ON tax_year_passes(stripe_payment_intent_id);
