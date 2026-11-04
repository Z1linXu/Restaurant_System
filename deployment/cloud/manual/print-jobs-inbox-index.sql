\set ON_ERROR_STOP on
-- PREPARED ONLY. Outside db/migration: application startup never executes this file.
-- Run only after independent Owner approval of exact database, index, and maintenance window.
-- psql -X -v ON_ERROR_STOP=1 -v owner_approved_index=true -v approved_database=EXACT_DB -f THIS_FILE
-- Use the existing private DB connection procedure; never put credentials in command history.
\if :{?owner_approved_index}
\else
  \echo 'REFUSED: explicit owner_approved_index is required'
  SELECT CAST('REFUSED_INDEX_APPROVAL_OR_IDENTITY' AS integer);
\endif
\if :owner_approved_index
\else
  \echo 'REFUSED: index approval is false'
  SELECT CAST('REFUSED_INDEX_APPROVAL_OR_IDENTITY' AS integer);
\endif
\if :{?approved_database}
\else
  \echo 'REFUSED: exact approved_database is required'
  SELECT CAST('REFUSED_INDEX_APPROVAL_OR_IDENTITY' AS integer);
\endif
SELECT current_database() = :'approved_database' AS approved_database_matches \gset
\if :approved_database_matches
\else
  \echo 'REFUSED: database does not match approval'
  SELECT CAST('REFUSED_INDEX_APPROVAL_OR_IDENTITY' AS integer);
\endif
-- A pre-existing name is not accepted silently, including an invalid concurrent-build remnant.
SELECT to_regclass('public.idx_print_jobs_inbox_order_created') IS NULL AS new_index_name \gset
\if :new_index_name
\else
  \echo 'REFUSED: inspect the existing index definition and validity before retrying'
  SELECT CAST('REFUSED_INDEX_APPROVAL_OR_IDENTITY' AS integer);
\endif
SET lock_timeout = '2s';
SET statement_timeout = '15min';
-- Must not run inside a transaction. No data change, Worker change or print state transition.
CREATE INDEX CONCURRENTLY idx_print_jobs_inbox_order_created
    ON public.print_jobs (store_id, order_id, created_at DESC, id DESC);
SELECT indexrelid::regclass AS index_name,indisvalid,indisready,pg_get_indexdef(indexrelid)
FROM pg_index WHERE indexrelid='public.idx_print_jobs_inbox_order_created'::regclass;
