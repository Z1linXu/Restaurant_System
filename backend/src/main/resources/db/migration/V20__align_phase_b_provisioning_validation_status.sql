-- V20__align_phase_b_provisioning_validation_status
-- Align Phase B Part 1 provisioning validation vocabulary with governance:
-- PASS / WARNING / BLOCKING for runtime validation, plus PENDING / FAILED for
-- ledger state transitions.

ALTER TABLE public.owner_store_provisioning_requests
    DROP CONSTRAINT chk_owner_store_provisioning_validation_status;

ALTER TABLE public.owner_store_provisioning_requests
    ADD CONSTRAINT chk_owner_store_provisioning_validation_status
        CHECK (validation_status IN ('PENDING', 'PASS', 'WARNING', 'BLOCKING', 'FAILED'));
