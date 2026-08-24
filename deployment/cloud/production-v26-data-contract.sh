#!/usr/bin/env bash

# Shared, read-only V10 -> V26 data contract.  Callers must provide a
# v26_db_query function that runs psql with ON_ERROR_STOP against the exact DB
# container they already validated.  Only counts and digests leave PostgreSQL;
# credentials, endpoints and business rows never enter evidence output.

v26_business_fingerprint() {
  v26_db_query <<'SQL' | sha256sum | awk '{print $1}'
begin;
set local statement_timeout = '110s';
create temporary table v26_content_fingerprints (
  table_name text primary key,
  content_md5 text not null
) on commit drop;

do $$
declare
  row record;
  expression text;
begin
  for row in
    select tablename
    from pg_catalog.pg_tables
    where schemaname = 'public'
      and tablename <> 'flyway_schema_history'
      and tablename <> all (array[
        'store_pricing_policies','store_combo_components','store_modules',
        'store_profiles','store_profile_versions','store_profile_artifacts',
        'store_combo_groups','printing_display_rule_sets',
        'printing_display_rule_revisions','chain_master_menus',
        'chain_master_menu_versions','chain_master_menu_categories',
        'chain_master_menu_products','chain_master_menu_options',
        'store_menu_master_mappings','owner_store_provisioning_requests',
        'store_readiness_evidence','store_readiness_evidence_history',
        'store_provisioning_part2_requests','store_provisioning_resources',
        'store_logical_printer_roles','store_device_readiness',
        'store_activation_requests','staging_store_fixture_cleanup_requests'
      ])
    order by tablename
  loop
    expression := case row.tablename
      when 'stores' then
        'to_jsonb(t) - array[''store_kind'',''lifecycle_status'',''provisioning_source'',''provisioned_profile_code'',''provisioned_profile_version'',''provisioned_profile_fingerprint_sha256'',''provisioned_master_menu_key'',''provisioned_master_menu_version'',''provisioned_master_menu_fingerprint_sha256'']'
      when 'stations' then
        'to_jsonb(t) - array[''name_zh'',''name_en'',''station_type'',''archived_at'',''updated_at'']'
      when 'print_jobs' then
        'to_jsonb(t) - array[''printing_rule_revision_id'',''printing_rule_fingerprint'']'
      when 'store_devices' then
        'to_jsonb(t) - array[''last_seen_at'',''last_heartbeat_at'',''updated_at'']'
      else 'to_jsonb(t)'
    end;
    execute format(
      'insert into v26_content_fingerprints select %L, md5(coalesce(string_agg((%s)::text, E''\n'' order by (%s)::text), '''')) from public.%I t',
      row.tablename,
      expression,
      expression,
      row.tablename
    );
  end loop;
end $$;

select table_name || '|' || content_md5
from v26_content_fingerprints
order by table_name;
commit;
SQL
}

v26_printing_fingerprint() {
  v26_db_query <<'SQL' | sha256sum | awk '{print $1}'
set statement_timeout = '110s';
select 'stores|' || md5(coalesce(string_agg(
  jsonb_build_object('id',id,'printing_enabled',printing_enabled,'printing_mode',printing_mode)::text,
  E'\n' order by id),'')) from stores
union all
select 'printer_configs|' || md5(coalesce(string_agg(to_jsonb(t)::text,E'\n' order by id),'')) from printer_configs t
union all
select 'printer_assignments|' || md5(coalesce(string_agg(to_jsonb(t)::text,E'\n' order by id),'')) from printer_assignments t
union all
select 'store_devices|' || md5(coalesce(string_agg((to_jsonb(t)-array['last_seen_at','last_heartbeat_at','updated_at'])::text,E'\n' order by id),'')) from store_devices t;
SQL
}

v26_additive_contract() {
  v26_db_query <<'SQL'
set statement_timeout = '110s';
with violations as (
  select 'pricing_per_store' code from stores s
    where (select count(*) from store_pricing_policies p where p.store_id=s.id) <> 1
  union all select 'combo_groups_per_store' from stores s
    where (select count(*) from store_combo_groups g where g.store_id=s.id) <> 2
  union all select 'combo_components_per_store' from stores s
    where (select count(*) from store_combo_components c where c.store_id=s.id) <> 5
  union all select 'combo_component_group_scope' from store_combo_components c
    join store_combo_groups g on g.id=c.group_id
    where c.store_id <> g.store_id
  union all select 'modules_per_store' from stores s
    where (select count(*) from store_modules m where m.store_id=s.id) <> 11
  union all select 'core_module_disabled' from store_modules
    where module_key in ('ORDERING_POS','MENU','MENU_MANAGEMENT','TABLE_MANAGEMENT','PRINTING','ORDER_HISTORY','REPORTING_CORE','STAFF_ACCESS','STORE_ADMINISTRATION')
      and (enabled is not true or configuration_status <> 'CONFIGURED')
  union all select 'display_rules_per_store' from stores s
    where not exists (
      select 1 from printing_display_rule_sets rs
      join printing_display_rule_revisions rr on rr.id=rs.active_revision_id
      where rs.store_id=s.id and rs.status='ACTIVE' and rr.status='PUBLISHED'
    )
  union all select 'legacy_lifecycle_default' from stores
    where store_kind <> 'BUSINESS' or lifecycle_status <> 'ACTIVE'
       or provisioning_source <> 'LEGACY_EXISTING_STORE'
  union all select 'profile_authority_missing'
    where not exists (
      select 1 from store_profiles p join store_profile_versions v on v.profile_id=p.id
      where v.status in ('READY','PUBLISHED','REVIEWED')
    )
  union all select 'master_authority_missing'
    where not exists (
      select 1 from chain_master_menus m join chain_master_menu_versions v on v.master_menu_id=m.id
      where m.status='PUBLISHED' and v.status='PUBLISHED'
    )
  union all select 'readiness_scope_orphan' from store_readiness_evidence e
    left join stores s on s.id=e.store_id and s.organization_id=e.organization_id
    where s.id is null
  union all select 'cleanup_ledger_not_empty' from staging_store_fixture_cleanup_requests
)
select
  (select count(*) from stores) || '|' ||
  (select count(*) from store_pricing_policies) || '|' ||
  (select count(*) from store_combo_groups) || '|' ||
  (select count(*) from store_combo_components) || '|' ||
  (select count(*) from store_modules) || '|' ||
  (select count(*) from printing_display_rule_sets) || '|' ||
  (select count(*) from printing_display_rule_revisions) || '|' ||
  (select count(*) from store_profiles) || '|' ||
  (select count(*) from chain_master_menus) || '|' ||
  (select count(*) from staging_store_fixture_cleanup_requests) || '|' ||
  (select count(*) from violations);
SQL
}
