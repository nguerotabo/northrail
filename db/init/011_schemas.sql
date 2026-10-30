/* Creating two namespaces */
CREATE SCHEMA ledger;
CREATE SCHEMA pii;

/* Database roles */
CREATE ROLE ledger_admin LOGIN PASSWORD 'ledger_admin';
CREATE ROLE pii_admin LOGIN PASSWORD 'pii_admin';

/* Tables from namespaces */
CREATE TABLE ledger.accounts(
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id bigint NOT NULL,
    institution text NOT NULL
);

CREATE TABLE ledger.entries(
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id bigint NOT NULL REFERENCES ledger.accounts (id),
    amount integer NOT NULL,
    transfer_id  bigint NOT NULL,
    time_posted timestamp NOT NULL
);

CREATE TABLE ledger.outbox_events(
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transfer_id bigint NOT NULL,
    done timestamp
);

CREATE TABLE ledger.settlement_batches(
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_date date NOT NULL,
    created timestamp
);

CREATE TABLE ledger.settlement_transfers(
    batch_id bigint NOT NULL REFERENCES ledger.settlement_batches (id),
    transfer_id bigint PRIMARY KEY
);

CREATE TABLE pii.customers(
    customer_id bigint PRIMARY KEY,
    full_name text NOT NULL
);

INSERT INTO ledger.accounts (customer_id, institution) VALUES (300, 'wealthsimple');
INSERT INTO pii.customers (customer_id, full_name) VALUES (300, 'User Test');

INSERT INTO ledger.accounts (customer_id, institution) VALUES (400, 'td');
INSERT INTO pii.customers (customer_id, full_name) VALUES (400, 'User Test2');

/* Role permission */
GRANT CONNECT ON DATABASE northrail TO ledger_admin;
GRANT CONNECT ON DATABASE northrail TO pii_admin;

GRANT USAGE ON SCHEMA ledger TO ledger_admin;
GRANT SELECT, INSERT ON ledger.accounts TO ledger_admin;
GRANT SELECT, INSERT ON ledger.entries TO ledger_admin;
GRANT SELECT, INSERT, UPDATE ON ledger.outbox_events TO ledger_admin;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA ledger TO ledger_admin;
GRANT SELECT, INSERT ON ledger.settlement_batches TO ledger_admin;
GRANT SELECT, INSERT ON ledger.settlement_transfers TO ledger_admin;

GRANT USAGE ON SCHEMA pii TO pii_admin;
GRANT SELECT, INSERT ON pii.customers TO pii_admin;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA pii to pii_admin;

ALTER ROLE ledger_admin SET search_path = ledger;
ALTER ROLE pii_admin SET search_path = pii;

/* Trigger functions */
CREATE FUNCTION ledger.refuse_entry_edit()
RETURNS Trigger
LANGUAGE plpgsql
AS $$
BEGIN 
    RAISE EXCEPTION 'ledger entries cannot be changed';
END;
$$;

CREATE TRIGGER entries_are_add_only
BEFORE UPDATE OR DELETE ON ledger.entries
FOR EACH ROW
EXECUTE FUNCTION ledger.refuse_entry_edit();


