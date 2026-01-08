CREATE TABLE share_links (
    id UUID PRIMARY KEY,
    code VARCHAR(8) UNIQUE NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    share_type VARCHAR(20) NOT NULL,
    data JSONB NOT NULL,
    requires_auth BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    access_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_share_links_code ON share_links(code);
CREATE INDEX idx_share_links_user_id ON share_links(user_id);
CREATE INDEX idx_share_links_expires_at ON share_links(expires_at);
