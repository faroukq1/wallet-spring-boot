CREATE TABLE accounts (
                          id BIGSERIAL PRIMARY KEY,
                          balance NUMERIC(19, 2) NOT NULL,
                          status VARCHAR(50) NOT NULL,
                          owner_id BIGINT NOT NULL,
                          version BIGINT
);

CREATE TABLE transactions (
                              id BIGSERIAL PRIMARY KEY,
                              type VARCHAR(50) NOT NULL,
                              amount NUMERIC(19, 2) NOT NULL,
                              status VARCHAR(50) NOT NULL,
                              source_account_id BIGINT,
                              destination_account_id BIGINT,
                              timestamp TIMESTAMP NOT NULL
);