-- Exact 21 existing V14 garage base tables. Execute only against the explicit V14 source
-- or a disposable restored V14 database. Failure to find a table aborts verification.
-- Ordered numeric output is stable and does not reveal row contents.
SELECT concat_ws(':',
  (SELECT count(*) FROM garage.appointments),
  (SELECT count(*) FROM garage.audit_logs),
  (SELECT count(*) FROM garage.customers),
  (SELECT count(*) FROM garage.employees),
  (SELECT count(*) FROM garage.grn_items),
  (SELECT count(*) FROM garage.grns),
  (SELECT count(*) FROM garage.notifications),
  (SELECT count(*) FROM garage.sales_invoice_items),
  (SELECT count(*) FROM garage.sales_invoices),
  (SELECT count(*) FROM garage.service_categories),
  (SELECT count(*) FROM garage.service_history),
  (SELECT count(*) FROM garage.service_items),
  (SELECT count(*) FROM garage.service_job_items),
  (SELECT count(*) FROM garage.service_job_parts),
  (SELECT count(*) FROM garage.service_jobs),
  (SELECT count(*) FROM garage.service_reminders),
  (SELECT count(*) FROM garage.stock_categories),
  (SELECT count(*) FROM garage.stock_items),
  (SELECT count(*) FROM garage.stock_transactions),
  (SELECT count(*) FROM garage.suppliers),
  (SELECT count(*) FROM garage.vehicles)
);
