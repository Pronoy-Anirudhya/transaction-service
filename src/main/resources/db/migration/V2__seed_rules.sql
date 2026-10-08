-- POC rule seed (spec 3, "Example fee_rule and limit_rule seed data"). All amounts are poisha (1 BDT = 100 poisha).

-- Tier 1 limits for SEND_MONEY.
INSERT INTO limit_rule (product, kyc_tier, per_txn_min, per_txn_max, daily_amount, daily_count, monthly_amount, monthly_count)
VALUES ('SEND_MONEY', 1,
        1000,       --  per txn min:        10.00 BDT
        2500000,    --  per txn max:    25,000.00 BDT
        5000000,    --  daily amount:   50,000.00 BDT
        50,         --  daily count:    50 transactions
        30000000,   --  monthly amount: 300,000.00 BDT
        200);       --  monthly count:  200 transactions

-- Tier 1 fee slabs for SEND_MONEY (inclusive ranges). VAT 15% (1,500 bps) is included in the fee; commission is 20%
-- (2,000 bps) of the fee net of VAT. No clamp: fee_min 0, fee_max NULL.
INSERT INTO fee_rule (product, kyc_tier, min_amount, max_amount, fee_type, fee_value, fee_min, fee_max, vat_bps, commission_bps, active)
VALUES ('SEND_MONEY', 1,
        100,        --  from      1.00 BDT
        10000,      --  to      100.00 BDT
        'FLAT', 0,  --  fee 0
        0, NULL, 1500, 2000, true),
       ('SEND_MONEY', 1,
        10001,      --  from    100.01 BDT
        2500000,    --  to   25,000.00 BDT
        'FLAT', 500, --  fee 5.00 BDT
        0, NULL, 1500, 2000, true);
