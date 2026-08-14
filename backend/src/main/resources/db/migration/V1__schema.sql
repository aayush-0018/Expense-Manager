-- ---------------------------------------------------------------------------
-- Mini Expense Manager :: core schema
-- ---------------------------------------------------------------------------

CREATE TABLE category (
    id          BIGSERIAL   PRIMARY KEY,
    name        TEXT        NOT NULL UNIQUE,
    color_hex   TEXT,
    is_default  BOOLEAN     NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE vendor_category_rule (
    id          BIGSERIAL   PRIMARY KEY,
    pattern     TEXT        NOT NULL,
    match_type  TEXT        NOT NULL CHECK (match_type IN ('EXACT', 'CONTAINS')),
    category_id BIGINT      NOT NULL REFERENCES category (id),
    priority    INT         NOT NULL DEFAULT 100,
    active      BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_rule_pattern_match UNIQUE (pattern, match_type)
);

CREATE TABLE csv_import_batch (
    id            BIGSERIAL   PRIMARY KEY,
    filename      TEXT        NOT NULL,
    total_rows    INT         NOT NULL DEFAULT 0,
    imported_rows INT         NOT NULL DEFAULT 0,
    failed_rows   INT         NOT NULL DEFAULT 0,
    status        TEXT        NOT NULL CHECK (status IN ('COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED')),
    error_report  JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE expense (
    id                    BIGSERIAL     PRIMARY KEY,
    expense_date          DATE          NOT NULL,
    amount                NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    vendor_name           TEXT          NOT NULL,
    vendor_normalized     TEXT          NOT NULL,
    description           TEXT,
    category_id           BIGINT        NOT NULL REFERENCES category (id),
    categorization_source TEXT          NOT NULL CHECK (categorization_source IN ('RULE', 'DEFAULT', 'MANUAL_OVERRIDE')),
    is_anomaly            BOOLEAN       NOT NULL DEFAULT false,
    anomaly_reason        TEXT,
    anomaly_evaluated_at  TIMESTAMPTZ,
    import_batch_id       BIGINT        REFERENCES csv_import_batch (id),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_expense_date          ON expense (expense_date DESC);
CREATE INDEX idx_expense_category_date ON expense (category_id, expense_date);
CREATE INDEX idx_expense_vendor_norm   ON expense (vendor_normalized);
CREATE INDEX idx_expense_anomaly       ON expense (is_anomaly) WHERE is_anomaly = true;
CREATE INDEX idx_expense_batch         ON expense (import_batch_id);
CREATE INDEX idx_rule_pattern_active   ON vendor_category_rule (pattern) WHERE active = true;

-- Exactly one category may be the fallback used when no vendor rule matches.
CREATE UNIQUE INDEX uq_category_single_default ON category (is_default) WHERE is_default = true;
