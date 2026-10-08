/* Creating two namespaces */
CREATE SCHEMA ledger;
CREATE SCHEMA pii;

/* Database roles */
CREATE ROLE ledger_admin LOGIN PASSWORD 'ledger_admin';
CREATE ROLE pii_admin LOGIN PASSWORD 'pii_admin';

/* Tables from namespaces */
CREATE TABLE ledger.accounts(
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id bigint NOT NULL
);

CREATE TABLE ledger.entries(
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id bigint NOT NULL REFERENCES ledger.accounts (id),
    amount integer NOT NULL,
    transfer_id  bigint NOT NULL,
    time_posted timestamp NOT NULL
);

CREATE TABLE pii.customers(
    customer_id bigint PRIMARY KEY,
    full_name text NOT NULL
);

INSERT INTO ledger.accounts (customer_id) VALUES (300);
INSERT INTO pii.customers (customer_id, full_name) VALUES (300, 'User Test');

/* Role permission */
GRANT CONNECT ON DATABASE northrail TO ledger_admin;
GRANT CONNECT ON DATABASE northrail TO pii_admin;

GRANT USAGE ON SCHEMA ledger TO ledger_admin;
GRANT SELECT, INSERT ON ledger.accounts TO ledger_admin;
GRANT SELECT, INSERT ON ledger.entries TO ledger_admin;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA ledger TO ledger_admin;

GRANT USAGE ON SCHEMA pii TO pii_admin;
GRANT SELECT, INSERT ON pii.customers TO pii_admin;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA pii to pii_admin;

ALTER ROLE ledger_admin SET search_path = ledger;
ALTER ROLE pii_admin SET search_path = pii;



