ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(256);

UPDATE users
SET email = auth_id
WHERE email IS NULL
  AND auth_id LIKE '%@%';
