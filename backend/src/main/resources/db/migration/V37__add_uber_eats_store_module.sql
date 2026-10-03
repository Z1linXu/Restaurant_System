-- Store capability only; never enable an external integration from a migration.
ALTER TABLE public.store_modules DROP CONSTRAINT chk_store_modules_module_key;
ALTER TABLE public.store_modules ADD CONSTRAINT chk_store_modules_module_key CHECK (module_key IN (
    'ORDERING_POS','MENU','MENU_MANAGEMENT','TABLE_MANAGEMENT','PRINTING',
    'ORDER_HISTORY','REPORTING_CORE','STAFF_ACCESS','STORE_ADMINISTRATION',
    'KDS','ANALYTICS_ADVANCED','UBER_EATS'
));
INSERT INTO public.store_modules(store_id,module_key,enabled,source,configuration_status,metadata_json,created_at,updated_at)
SELECT id,'UBER_EATS',false,'MIGRATION_DEFAULT','CONFIGURED','{"default_rule":"optional_default_off"}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM public.stores
ON CONFLICT (store_id,module_key) DO NOTHING;
