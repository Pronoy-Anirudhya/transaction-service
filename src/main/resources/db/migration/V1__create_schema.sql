-- Transaction database (txn_db), spec 6.1. Only the indexes of spec 6.1 exist (P14): the primary keys, the UNIQUE
-- constraints and the two partial indexes for in-flight rows. Balances live only in the ledger.

CREATE TABLE wallet (
  wallet_id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  msisdn             varchar(15)  NOT NULL UNIQUE,
  holder_name        varchar(100) NOT NULL,
  wallet_type        varchar(10)  NOT NULL CHECK (wallet_type IN ('CUSTOMER','SYSTEM')),
  status             varchar(10)  NOT NULL CHECK (status IN ('ACTIVE','FROZEN','CLOSED')),
  kyc_tier           smallint     NOT NULL,
  ledger_account_id  uuid         NOT NULL UNIQUE,      -- = TigerBeetle account.id
  created_at         timestamptz  NOT NULL DEFAULT now(),
  updated_at         timestamptz  NOT NULL DEFAULT now()
);

-- one row per wallet, created with the wallet; day/month counters reset in-place
CREATE TABLE wallet_limit_usage (
  wallet_id     bigint PRIMARY KEY REFERENCES wallet(wallet_id),
  day           date   NOT NULL,
  day_amount    bigint NOT NULL DEFAULT 0,
  day_count     int    NOT NULL DEFAULT 0,
  month         date   NOT NULL,                       -- first day of month
  month_amount  bigint NOT NULL DEFAULT 0,
  month_count   int    NOT NULL DEFAULT 0
) WITH (fillfactor = 70);                              -- room for HOT updates

CREATE TABLE limit_rule (
  product        varchar(20) NOT NULL,
  kyc_tier       smallint    NOT NULL,
  per_txn_min    bigint NOT NULL, per_txn_max    bigint NOT NULL,
  daily_amount   bigint NOT NULL, daily_count    int    NOT NULL,
  monthly_amount bigint NOT NULL, monthly_count  int    NOT NULL,
  PRIMARY KEY (product, kyc_tier)
);

CREATE TABLE fee_rule (
  rule_id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  product        varchar(20) NOT NULL,
  kyc_tier       smallint    NOT NULL,
  min_amount     bigint NOT NULL, max_amount bigint NOT NULL,   -- inclusive slab
  fee_type       varchar(7)  NOT NULL CHECK (fee_type IN ('FLAT','PERCENT')),
  fee_value      bigint NOT NULL,                  -- poisha if FLAT, bps if PERCENT
  fee_min        bigint NOT NULL DEFAULT 0, fee_max bigint,
  vat_bps        int    NOT NULL,
  commission_bps int    NOT NULL DEFAULT 0,
  active         boolean NOT NULL DEFAULT true
);

CREATE TABLE send_money_txn (
  txn_id             uuid        PRIMARY KEY,         -- time-ordered; = TB transfer.user_data_128
  client_ref         varchar(64) NOT NULL,            -- Idempotency-Key
  request_hash       bytea       NOT NULL,
  sender_wallet_id   bigint      NOT NULL REFERENCES wallet(wallet_id),
  receiver_wallet_id bigint      NOT NULL REFERENCES wallet(wallet_id),
  amount             bigint      NOT NULL CHECK (amount > 0),
  fee                bigint      NOT NULL, vat bigint NOT NULL,
  commission         bigint      NOT NULL, fee_income bigint NOT NULL,
  currency           char(3)     NOT NULL DEFAULT 'BDT',
  reference          varchar(50),
  business_date      date        NOT NULL,            -- Asia/Dhaka, for limit release
  status             varchar(10) NOT NULL CHECK (status IN ('INITIATED','COMPLETED','FAILED')),
  failure_code       varchar(32),
  ledger_attempts    smallint    NOT NULL DEFAULT 0,
  next_check_at      timestamptz,
  ledger_ts          bigint,                          -- TigerBeetle transfer timestamp (ns)
  created_at         timestamptz NOT NULL DEFAULT now(),
  completed_at       timestamptz,
  event_published_at timestamptz,
  UNIQUE (sender_wallet_id, client_ref)
) WITH (fillfactor = 80);

CREATE INDEX smt_in_doubt   ON send_money_txn (next_check_at) WHERE status = 'INITIATED';
CREATE INDEX smt_unpublished ON send_money_txn (completed_at)
  WHERE status <> 'INITIATED' AND event_published_at IS NULL;

-- P13: the two frequently updated tables are vacuumed and analysed after 2% of their rows change (default 20% / 10%).
ALTER TABLE send_money_txn SET (autovacuum_vacuum_scale_factor = 0.02, autovacuum_analyze_scale_factor = 0.02);
ALTER TABLE wallet_limit_usage SET (autovacuum_vacuum_scale_factor = 0.02, autovacuum_analyze_scale_factor = 0.02);
