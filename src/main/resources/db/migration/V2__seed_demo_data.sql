-- Demo accounts so the API is usable right after `docker compose up`.
-- Login users (hardcoded in CustomUserDetailsService): alice/password, admin/admin
INSERT INTO accounts (id, balance, status, owner_id, version) VALUES
    (1, 1000.00, 'ACTIVE', 1, 0),
    (2, 500.00, 'ACTIVE', 2, 0),
    (3, 250.00, 'BLOCKED', 3, 0);
