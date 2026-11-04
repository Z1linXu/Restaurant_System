\set ON_ERROR_STOP on
-- PREPARED ONLY. Separate Owner approval is required; never called by application startup.
-- psql -X -v ON_ERROR_STOP=1 -v owner_approved_index=true -v approved_database=EXACT_DB -f THIS_FILE
\if :{?owner_approved_index}
\else
  \echo 'REFUSED: explicit owner_approved_index is required'
  SELECT CAST('REFUSED_INDEX_APPROVAL_OR_IDENTITY' AS integer);
\endif
\if :owner_approved_index
\else
  SELECT CAST('REFUSED_INDEX_APPROVAL_OR_IDENTITY' AS integer);
\endif
\if :{?approved_database}
\else
  SELECT CAST('REFUSED_INDEX_APPROVAL_OR_IDENTITY' AS integer);
\endif
SELECT current_database() = :'approved_database' AS approved_database_matches \gset
\if :approved_database_matches
\else
  \echo 'REFUSED: database does not match approval'
  SELECT CAST('REFUSED_INDEX_APPROVAL_OR_IDENTITY' AS integer);
\endif
-- Fail closed if the name has been reused for a different index.
SELECT coalesce((SELECT pg_get_indexdef(i.indexrelid) =
  'CREATE INDEX idx_print_jobs_inbox_order_created ON public.print_jobs USING btree (store_id, order_id, created_at DESC, id DESC)'
  AND i.indrelid='public.print_jobs'::regclass AND NOT i.indisunique
  AND NOT EXISTS (SELECT 1 FROM pg_constraint c WHERE c.conindid=i.indexrelid)
  FROM pg_index i WHERE i.indexrelid=to_regclass('public.idx_print_jobs_inbox_order_created')),false)
  AS exact_index_matches \gset
\if :exact_index_matches
\else
  \echo 'REFUSED: index identity or definition does not match this package'
  SELECT CAST('REFUSED_INDEX_APPROVAL_OR_IDENTITY' AS integer);
\endif
SET lock_timeout = '2s';
SET statement_timeout = '15min';
DROP INDEX CONCURRENTLY public.idx_print_jobs_inbox_order_created;
