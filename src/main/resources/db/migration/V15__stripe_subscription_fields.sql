ALTER TABLE users ADD COLUMN stripe_customer_id VARCHAR(255);
ALTER TABLE users ADD COLUMN stripe_subscription_id VARCHAR(255);
ALTER TABLE users ADD COLUMN stripe_subscription_status VARCHAR(64);

CREATE UNIQUE INDEX idx_users_stripe_customer_id ON users(stripe_customer_id);
CREATE UNIQUE INDEX idx_users_stripe_subscription_id ON users(stripe_subscription_id);
