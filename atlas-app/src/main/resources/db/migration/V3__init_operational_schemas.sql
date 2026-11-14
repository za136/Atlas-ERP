-- Atlas ERP V3: Operational Schemas (Procurement, Sales, Inventory)
-- Enforces ARC-001 (module schemas), ARC-004 (atomic posting)

CREATE SCHEMA IF NOT EXISTS prc;
CREATE SCHEMA IF NOT EXISTS sls;
CREATE SCHEMA IF NOT EXISTS inv;

-- 1. Procurement (PRC)
CREATE TABLE prc.purchase_orders (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    company_id UUID NOT NULL REFERENCES plt_org.companies(id),
    po_number VARCHAR(30) NOT NULL,
    supplier_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    order_date DATE NOT NULL,
    CONSTRAINT uq_po_num UNIQUE (tenant_id, company_id, po_number)
);

CREATE TABLE prc.purchase_order_items (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    po_id UUID NOT NULL REFERENCES prc.purchase_orders(id) ON DELETE CASCADE,
    line_number INT NOT NULL,
    material_code VARCHAR(40) NOT NULL,
    ordered_quantity NUMERIC(19, 4) NOT NULL,
    uom VARCHAR(10) NOT NULL,
    unit_price NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    delivered_quantity NUMERIC(19, 4) NOT NULL DEFAULT 0,
    invoiced_quantity NUMERIC(19, 4) NOT NULL DEFAULT 0,
    CONSTRAINT uq_po_item_line UNIQUE (po_id, line_number)
);

-- 2. Sales (SLS)
CREATE TABLE sls.sales_orders (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    company_id UUID NOT NULL REFERENCES plt_org.companies(id),
    order_number VARCHAR(30) NOT NULL,
    customer_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    order_date DATE NOT NULL,
    CONSTRAINT uq_so_num UNIQUE (tenant_id, company_id, order_number)
);

CREATE TABLE sls.sales_order_items (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL REFERENCES sls.sales_orders(id) ON DELETE CASCADE,
    line_number INT NOT NULL,
    material_code VARCHAR(40) NOT NULL,
    ordered_quantity NUMERIC(19, 4) NOT NULL,
    uom VARCHAR(10) NOT NULL,
    unit_price NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    delivered_quantity NUMERIC(19, 4) NOT NULL DEFAULT 0,
    invoiced_quantity NUMERIC(19, 4) NOT NULL DEFAULT 0,
    CONSTRAINT uq_so_item_line UNIQUE (order_id, line_number)
);

-- 3. Inventory (INV)
CREATE TABLE inv.stock_balances (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    plant_id UUID NOT NULL REFERENCES plt_org.plants(id),
    storage_location_id UUID NOT NULL REFERENCES plt_org.storage_locations(id),
    material_code VARCHAR(40) NOT NULL,
    quantity_on_hand NUMERIC(19, 4) NOT NULL DEFAULT 0,
    uom VARCHAR(10) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_stock_bal UNIQUE (tenant_id, plant_id, storage_location_id, material_code)
);

CREATE TABLE inv.stock_movements (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    plant_id UUID NOT NULL REFERENCES plt_org.plants(id),
    storage_location_id UUID NOT NULL REFERENCES plt_org.storage_locations(id),
    movement_type VARCHAR(10) NOT NULL,
    material_code VARCHAR(40) NOT NULL,
    quantity NUMERIC(19, 4) NOT NULL,
    uom VARCHAR(10) NOT NULL,
    valuation_amount NUMERIC(19, 4),
    currency VARCHAR(3),
    reference VARCHAR(50),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Row Level Security (RLS) policies for tenant isolation (Decision D-03/D-04)
DO $$
DECLARE
    t text;
    schemaname text;
BEGIN
    FOR schemaname, t IN
        SELECT 'plt_org', 'companies' UNION ALL
        SELECT 'plt_org', 'plants' UNION ALL
        SELECT 'plt_org', 'storage_locations' UNION ALL
        SELECT 'plt_org', 'cost_centers' UNION ALL
        SELECT 'plt_org', 'profit_centers' UNION ALL
        SELECT 'plt_iam', 'users' UNION ALL
        SELECT 'plt_iam', 'roles' UNION ALL
        SELECT 'plt_doc', 'document_flow_links' UNION ALL
        SELECT 'plt_doc', 'idempotency_records' UNION ALL
        SELECT 'plt_doc', 'number_ranges' UNION ALL
        SELECT 'plt_aud', 'audit_events' UNION ALL
        SELECT 'plt_cfg', 'config_entries' UNION ALL
        SELECT 'fin', 'gl_accounts' UNION ALL
        SELECT 'fin', 'fiscal_periods' UNION ALL
        SELECT 'fin', 'universal_journal_entries' UNION ALL
        SELECT 'fin', 'universal_journal_items' UNION ALL
        SELECT 'fin', 'ap_open_items' UNION ALL
        SELECT 'fin', 'ar_open_items' UNION ALL
        SELECT 'prc', 'purchase_orders' UNION ALL
        SELECT 'prc', 'purchase_order_items' UNION ALL
        SELECT 'sls', 'sales_orders' UNION ALL
        SELECT 'sls', 'sales_order_items' UNION ALL
        SELECT 'inv', 'stock_balances' UNION ALL
        SELECT 'inv', 'stock_movements'
    LOOP
        EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', schemaname, t);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation_policy ON %I.%I', schemaname, t);
        EXECUTE format('CREATE POLICY tenant_isolation_policy ON %I.%I USING (tenant_id = NULLIF(current_setting(''app.current_tenant_id'', true), '''')::uuid OR current_setting(''app.current_tenant_id'', true) IS NULL OR current_setting(''app.current_tenant_id'', true) = '''')', schemaname, t);
    END LOOP;
END $$;
