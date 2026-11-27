-- A job may have only one non-cancelled invoice.
-- Cancelled invoices remain available for audit history.

CREATE UNIQUE INDEX ux_sales_invoices_one_active_per_job
    ON garage.sales_invoices (service_job_id)
    WHERE status <> 'CANCELLED';