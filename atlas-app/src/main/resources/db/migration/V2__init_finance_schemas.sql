-- Atlas ERP V2: Finance Schemas (Universal Journal, GL, AP, AR)
-- Enforces DAT-001 (Universal Journal), FIN-INV-1..5 (Invariants)

CREATE SCHEMA IF NOT EXISTS fin;

-- 1. GL Master & Periods
CREATE TABLE fin.gl_accounts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    company_id UUID NOT NULL REFERENCES plt_org.companies(id),
    account_code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    account_type VARCHAR(30) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    is_reconciliation BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_account_tenant_code UNIQUE (tenant_id, company_id, account_code)
);

CREATE TABLE fin.fiscal_periods (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    company_id UUID NOT NULL REFERENCES plt_org.companies(id),
    ledger_code VARCHAR(10) NOT NULL DEFAULT '0L',
    fiscal_year INT NOT NULL,
    fiscal_period INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    is_open BOOLEAN NOT NULL DEFAULT TRUE,
    is_special_period BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_period UNIQUE (tenant_id, company_id, ledger_code, fiscal_year, fiscal_period)
);

-- 2. Universal Journal (DAT-001, FIN-GL-001)
CREATE TABLE fin.universal_journal_entries (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    company_id UUID NOT NULL REFERENCES plt_org.companies(id),
    ledger_code VARCHAR(10) NOT NULL DEFAULT '0L',
    document_number VARCHAR(30) NOT NULL,
    document_type VARCHAR(30) NOT NULL,
    fiscal_year INT NOT NULL,
    fiscal_period INT NOT NULL,
    posting_date DATE NOT NULL,
    document_date DATE NOT NULL,
    entry_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reference VARCHAR(50) NOT NULL,
    header_text VARCHAR(100),
    source_module VARCHAR(20) NOT NULL,
    source_document_id UUID NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    is_reversed BOOLEAN NOT NULL DEFAULT FALSE,
    reversal_document_id UUID,
    reversed_document_id UUID,
    CONSTRAINT uq_journal_doc_num UNIQUE (tenant_id, company_id, document_number)
);

CREATE TABLE fin.universal_journal_items (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    entry_id UUID NOT NULL REFERENCES fin.universal_journal_entries(id) ON DELETE CASCADE,
    line_number INT NOT NULL,
    account_code VARCHAR(30) NOT NULL,
    posting_side VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    cost_center_code VARCHAR(30),
    profit_center_code VARCHAR(30),
    business_partner_id UUID,
    line_text VARCHAR(150),
    CONSTRAINT uq_item_line_num UNIQUE (entry_id, line_number)
);

CREATE INDEX idx_journal_reporting ON fin.universal_journal_entries (company_id, ledger_code, posting_date);
CREATE INDEX idx_journal_items_account ON fin.universal_journal_items (account_code, currency);

-- 3. Subledgers (AP & AR Open Items for FIN-INV-2)
CREATE TABLE fin.ap_open_items (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    company_id UUID NOT NULL REFERENCES plt_org.companies(id),
    supplier_id UUID NOT NULL,
    document_id UUID NOT NULL,
    invoice_number VARCHAR(50) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    due_date DATE NOT NULL,
    is_cleared BOOLEAN NOT NULL DEFAULT FALSE,
    clearing_document_id UUID
);

CREATE INDEX idx_ap_open ON fin.ap_open_items (company_id, is_cleared);

CREATE TABLE fin.ar_open_items (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    company_id UUID NOT NULL REFERENCES plt_org.companies(id),
    customer_id UUID NOT NULL,
    document_id UUID NOT NULL,
    invoice_number VARCHAR(50) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    due_date DATE NOT NULL,
    is_cleared BOOLEAN NOT NULL DEFAULT FALSE,
    clearing_document_id UUID
);

CREATE INDEX idx_ar_open ON fin.ar_open_items (company_id, is_cleared);
