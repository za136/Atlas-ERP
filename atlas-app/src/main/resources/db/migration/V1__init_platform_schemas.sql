-- Atlas ERP V1: Platform Schemas (PLT-ORG, PLT-IAM, PLT-DOC, PLT-AUD, PLT-CFG)
-- Enforces Decisions D-03 (multi-schema), D-04 (RLS tenant isolation)

CREATE SCHEMA IF NOT EXISTS plt_org;
CREATE SCHEMA IF NOT EXISTS plt_iam;
CREATE SCHEMA IF NOT EXISTS plt_doc;
CREATE SCHEMA IF NOT EXISTS plt_aud;
CREATE SCHEMA IF NOT EXISTS plt_cfg;

-- 1. PLT-ORG: Organization Structure
CREATE TABLE plt_org.companies (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    country VARCHAR(2) NOT NULL,
    fiscal_variant VARCHAR(10) NOT NULL DEFAULT 'STANDARD',
    valid_from DATE NOT NULL,
    valid_to DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_company_tenant_code UNIQUE (tenant_id, code)
);

CREATE TABLE plt_org.plants (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    company_id UUID NOT NULL REFERENCES plt_org.companies(id),
    code VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    country VARCHAR(2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_plant_tenant_code UNIQUE (tenant_id, company_id, code)
);

CREATE TABLE plt_org.storage_locations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    plant_id UUID NOT NULL REFERENCES plt_org.plants(id),
    code VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uq_sloc_tenant_code UNIQUE (tenant_id, plant_id, code)
);

CREATE TABLE plt_org.cost_centers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    company_id UUID NOT NULL REFERENCES plt_org.companies(id),
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    profit_center_id UUID,
    valid_from DATE NOT NULL,
    valid_to DATE NOT NULL,
    CONSTRAINT uq_cc_tenant_code UNIQUE (tenant_id, company_id, code)
);

CREATE TABLE plt_org.profit_centers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    company_id UUID NOT NULL REFERENCES plt_org.companies(id),
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    segment VARCHAR(30),
    CONSTRAINT uq_pc_tenant_code UNIQUE (tenant_id, company_id, code)
);

-- 2. PLT-IAM: Identity & Access Management
CREATE TABLE plt_iam.users (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    username VARCHAR(50) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    principal_type VARCHAR(20) NOT NULL,
    amount_limit NUMERIC(19, 4),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_tenant_username UNIQUE (tenant_id, username)
);

CREATE TABLE plt_iam.roles (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(50) NOT NULL,
    CONSTRAINT uq_role_tenant_name UNIQUE (tenant_id, name)
);

CREATE TABLE plt_iam.role_permissions (
    role_id UUID NOT NULL REFERENCES plt_iam.roles(id) ON DELETE CASCADE,
    permission VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

-- 3. PLT-DOC: Document Framework & Flow
CREATE TABLE plt_doc.document_flow_links (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    predecessor_type VARCHAR(30) NOT NULL,
    predecessor_id UUID NOT NULL,
    successor_type VARCHAR(30) NOT NULL,
    successor_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE plt_doc.idempotency_records (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_idempotency_key UNIQUE (idempotency_key)
);

CREATE TABLE plt_doc.number_ranges (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    company_id UUID NOT NULL,
    document_type VARCHAR(20) NOT NULL,
    prefix VARCHAR(10) NOT NULL,
    current_number BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_num_range UNIQUE (tenant_id, company_id, document_type)
);

-- 4. PLT-AUD: Tamper-Evident Audit Trail
CREATE TABLE plt_aud.audit_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(30) NOT NULL,
    actor_id UUID NOT NULL,
    actor_name VARCHAR(100) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    payload_before TEXT,
    payload_after TEXT,
    previous_hash VARCHAR(64) NOT NULL,
    entry_hash VARCHAR(64) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_entity ON plt_aud.audit_events (entity_id, timestamp);
CREATE INDEX idx_audit_tenant ON plt_aud.audit_events (tenant_id, timestamp DESC);

-- 5. PLT-CFG: Configuration
CREATE TABLE plt_cfg.config_entries (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    category VARCHAR(50) NOT NULL,
    config_key VARCHAR(100) NOT NULL,
    config_value TEXT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cfg_entry UNIQUE (tenant_id, category, config_key)
);
