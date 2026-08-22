-- V22__repair_printing_display_rule_revision_lifecycle
-- A fingerprint is a content checksum, not the historical identity of a
-- revision. Allow an intentional rollback to reuse historical content while
-- retaining revision identity as (rule_set_id, revision_number).

ALTER TABLE public.printing_display_rule_revisions
    DROP CONSTRAINT uq_printing_display_rule_revisions_fingerprint;

CREATE INDEX idx_printing_display_rule_revisions_set_fingerprint
    ON public.printing_display_rule_revisions (rule_set_id, fingerprint_sha256);

CREATE UNIQUE INDEX uq_printing_display_rule_revisions_single_draft
    ON public.printing_display_rule_revisions (rule_set_id)
    WHERE status = 'DRAFT';
