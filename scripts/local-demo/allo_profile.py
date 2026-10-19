#!/usr/bin/env python3
"""Explicit local-only PRIVATE configuration archive; no Store apply/provision path.

`build` is pure data. `archive --runtime ...` is an opt-in synthetic bootstrap and
writes only store_profiles, store_profile_versions and store_profile_artifacts.
No credentials are read here: the existing isolated lifecycle owns SQL transport.
"""
import argparse
import hashlib
import json
import pathlib
import sys

import allo_runtime as life

PROFILE_CODE = 'ALLO_NOUILLE_PROFILE'
PROFILE_VERSION = 'v1'
SCHEMA = 'STORE_PROFILE_CONTRACT_V1'
PROVENANCE = 'ALLO_LOCAL_DEMO_CONFIGURATION_ARCHIVE_V1'
DATABASE = 'commercial_allo_demo'


def canonical(value):
    """Same bytes as StoreProfileCanonicalJson for this integer/string JSON data.

    All object keys are ASCII; prices remain decimal strings, never Python floats.
    Java sorts keys, preserves array order and emits unescaped UTF-8 identically.
    StoreConfigurationPackageContractTest verifies the actual complete package
    using the Java validator and computeAggregateFingerprint implementation.
    """
    if isinstance(value, float):
        raise ValueError('Profile source must use decimal strings, not binary floats')
    if isinstance(value, dict):
        if any(not isinstance(key, str) or not key.isascii() for key in value):
            raise ValueError('Profile object keys must be ASCII strings')
        for child in value.values():
            canonical(child)
    elif isinstance(value, list):
        for child in value:
            canonical(child)
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(',', ':'), allow_nan=False)


def digest(value):
    return hashlib.sha256(value.encode('utf-8')).hexdigest()


def build_package():
    source = json.loads((pathlib.Path(__file__).with_name('allo_menu.json')).read_text())
    if source.get('profile_code') != PROFILE_CODE or source.get('profile_version') != PROFILE_VERSION:
        raise ValueError('Menu source identity mismatch')
    if len(source.get('items', [])) != 32 or len(source.get('addons', [])) != 15:
        raise ValueError('Expected the reviewed 32-item and 15-Add-on source')
    modules = [{'module_key': key, 'enabled': True} for key in (
        'ORDERING_POS', 'MENU', 'MENU_MANAGEMENT', 'TABLE_MANAGEMENT', 'PRINTING',
        'ORDER_HISTORY', 'REPORTING_CORE', 'STAFF_ACCESS', 'STORE_ADMINISTRATION')]
    modules += [{'module_key': key, 'enabled': False} for key in ('KDS', 'ANALYTICS_ADVANCED', 'UBER_EATS')]
    defaults = {
        'modules': modules,
        'environment_capabilities': ['CORE_POS_RUNTIME', 'AUTH_RUNTIME', 'DATABASE', 'WEBSOCKET_RUNTIME',
                                     'ADMIN_RUNTIME', 'PRINTING_FEATURE_FLAG', 'PRINT_MODE_RUNTIME', 'ANALYTICS_FEATURE_FLAG'],
        # Catalog capabilities describe supported runtime adapters, never physical bindings.
        'hardware_capabilities': ['TOUCH_CLIENT', 'PRINTER_TOPOLOGY_FOR_REAL_OR_PAD_DIRECT', 'PAD_DEVICE_FOR_PAD_DIRECT'],
    }
    payloads = [
        ('menu_template', 'MENU_TEMPLATE', 'ALLO_MENU_V1', source),
        ('pricing_policy', 'PRICING_POLICY', 'ALLO_PRICING_V1', {
            'currency': 'CAD', 'prices_include_tax': False,
            'tax_policy_reference': 'TEST_ONLY_EXISTING_14_975_PERCENT_TAX_EXCLUSIVE',
            'combo_pricing': 'PER_ITEM_FIXED_TOTAL_USING_EXISTING_OPTION_SNAPSHOTS',
            'source': [{'code': offer['code'], 'fixed_price': offer['fixed_price']} for offer in source['combos']],
        }),
        ('combo_configuration', 'COMBO_CONFIGURATION', 'ALLO_COMBO_V1', {
            'groups': source['combo_groups'], 'offers': source['combos'],
            'component_only_items': source['component_only_items'],
        }),
        ('tables', 'TABLE_TEMPLATE', 'ALLO_TABLES_V1', {'tables': []}),
        ('stations', 'STATION_TEMPLATE', 'ALLO_STATIONS_V1', {
            'stations': [{'ref': 'BACK_KITCHEN', 'name_zh': '后厨', 'name_en': 'BACK_KITCHEN', 'is_active': True}],
            'drinks_skip_kitchen': True,
        }),
        ('logical_printing_topology', 'LOGICAL_PRINTING_TOPOLOGY', 'ALLO_PRINTING_V1', {
            'printing_mode': 'MOCK', 'kitchen_summary_module': 'GRAB', 'kitchen_display_name': 'BACK_KITCHEN',
            'one_summary_per_submission': True, 'receipt_module': 'FRONTDESK_RECEIPT',
            'physical_binding_required': False,
        }),
        ('role_access_defaults', 'ROLE_ACCESS_DEFAULTS', 'ALLO_ROLES_V1', {
            'access_policy': 'EXISTING_STORE_ORGANIZATION_AUTHORIZATION',
            'profile_visibility': 'PRIVATE', 'synthetic_only': True,
        }),
        ('hardware_requirements', 'HARDWARE_REQUIREMENTS', 'ALLO_HARDWARE_V1', {
            'runtime_mode': 'MOCK', 'physical_printer_required': False, 'physical_pad_required': False,
        }),
        ('printing_display_rules', 'PRINTING_DISPLAY_RULES', 'ALLO_DISPLAY_V1', {
            'brand_name': 'Allô Nouille', 'default_language': 'zh-CN',
            'use_frozen_full_menu_names': True, 'legacy_abbreviations': False,
        }),
    ]
    references = {}
    artifacts = []
    for reference, artifact_type, artifact_code, content in payloads:
        encoded = canonical(content)
        fingerprint = digest(encoded)
        artifacts.append({'artifact_type': artifact_type, 'artifact_code': artifact_code,
                          'artifact_version': PROFILE_VERSION, 'content_json': encoded,
                          'fingerprint_sha256': fingerprint})
        references[reference] = {'artifact_code': artifact_code, 'artifact_version': PROFILE_VERSION,
                                 'fingerprint_sha256': fingerprint}
    content = {
        'profile_code': PROFILE_CODE, 'profile_version': PROFILE_VERSION, 'schema_version': SCHEMA,
        'configuration_kind': 'STORE_CONFIGURATION_PACKAGE',
        'base_profile_reference': {'profile_code': 'NEUTRAL_RESTAURANT', 'profile_version': 'v1'},
        'module_defaults': defaults, 'template_references': references,
        'materialization_contract': {'uses_profile_local_refs': True, 'new_surrogate_ids_required': True,
                                     'source_store_db_ids_allowed': False},
        'archive_contract': {'runtime_apply_supported': False, 'legacy_provisioning_supported': False,
                             'menu_materialization': 'EXISTING_AUTHORIZED_MENU_APIS'},
    }
    content_json = canonical(content)
    artifact_lines = sorted('|'.join((a['artifact_type'], a['artifact_code'], a['artifact_version'],
                                     digest(canonical(json.loads(a['content_json']))))) for a in artifacts)
    aggregate_input = '\n'.join(('profile_code=' + PROFILE_CODE, 'profile_version=' + PROFILE_VERSION,
                                  'schema_version=' + SCHEMA, 'content=' + content_json,
                                  'artifacts=' + '\n'.join(artifact_lines)))
    return {'profile_code': PROFILE_CODE, 'display_name': 'Allô Nouille', 'profile_version': PROFILE_VERSION,
            'schema_version': SCHEMA, 'visibility': 'PRIVATE', 'provenance': PROVENANCE,
            'source_reference': PROVENANCE, 'content_json': content_json,
            'fingerprint_sha256': digest(aggregate_input), 'artifacts': artifacts}


def positive_id(value, label):
    if type(value) is not int or not 0 < value < 2 ** 63:
        raise ValueError('Missing or invalid exact local ' + label)
    return value


def sql_literal(value):
    return "'" + value.replace("'", "''") + "'"


def archive(runtime):
    run, state = life.state_for(runtime)
    if state.get('kind') != 'ALLO_LOCAL_SYNTHETIC_ONLY' or state.get('database') != DATABASE:
        raise ValueError('Only the exact isolated Allô synthetic database is eligible')
    if state.get('configured') is not True or state.get('printing') != 'MOCK':
        raise ValueError('Complete the explicit MOCK local menu API configuration first')
    organization = positive_id(state.get('organizations', {}).get('ALLO_LOCAL'), 'ALLO_LOCAL Organization')
    store = positive_id(state.get('stores', {}).get('allo'), 'Allô Store')
    if state.get('store_codes', {}).get('allo') != 'ALLO_DEMO_V1':
        raise ValueError('Exact Allô Store code is required in runtime metadata')
    port = state.get('ports', {}).get('postgres')
    if type(port) is not int or not 0 < port < 65536:
        raise ValueError('Invalid isolated PostgreSQL port')
    package = build_package()
    encoded = sql_literal(canonical(package))
    statement = f"""BEGIN;
SET LOCAL standard_conforming_strings=on;
SET LOCAL lock_timeout='5s';
SELECT pg_advisory_xact_lock(hashtextextended('ALLO_LOCAL_SYNTHETIC_ONLY:ALLO_NOUILLE_PROFILE:v1',0));
DO $allo_private_archive$
DECLARE
    payload jsonb := {encoded}::jsonb;
    organization_key bigint := {organization};
    store_key bigint := {store};
    root_row store_profiles%ROWTYPE;
    version_row store_profile_versions%ROWTYPE;
    artifact jsonb;
BEGIN
    IF current_database() <> 'commercial_allo_demo' OR current_user <> 'allo_app'
       OR inet_server_addr() IS DISTINCT FROM '127.0.0.1'::inet
       OR inet_server_port() <> {port} THEN
        RAISE EXCEPTION 'ALLO_PROFILE_ARCHIVE_DATABASE_IDENTITY_MISMATCH';
    END IF;
    PERFORM id FROM organizations WHERE id=organization_key AND code='ALLO_LOCAL' AND status='active' FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION 'ALLO_PROFILE_ARCHIVE_ORGANIZATION_MISMATCH'; END IF;
    PERFORM id FROM stores WHERE id=store_key AND organization_id=organization_key
        AND code='ALLO_DEMO_V1' AND status='active'
        AND provisioned_profile_code='NEUTRAL_RESTAURANT' AND provisioned_profile_version='v1' FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION 'ALLO_PROFILE_ARCHIVE_STORE_MISMATCH'; END IF;
    IF NOT EXISTS (SELECT 1 FROM users u JOIN roles r ON r.id=u.role_id
        JOIN organization_memberships m ON m.user_id=u.id AND m.organization_id=organization_key
        WHERE u.username='allo.owner' AND u.status='active' AND r.code='OWNER'
          AND m.role_code='OWNER' AND m.is_active=true) THEN
        RAISE EXCEPTION 'ALLO_PROFILE_ARCHIVE_OWNER_SCOPE_MISMATCH';
    END IF;
    SELECT * INTO root_row FROM store_profiles WHERE profile_code=payload->>'profile_code' FOR UPDATE;
    IF FOUND THEN
        IF root_row.visibility <> 'PRIVATE' OR root_row.owner_organization_id IS DISTINCT FROM organization_key
           OR root_row.provenance <> payload->>'provenance' OR root_row.status <> 'PUBLISHED'
           OR root_row.display_name <> payload->>'display_name' THEN
            RAISE EXCEPTION 'ALLO_PROFILE_ARCHIVE_EXISTING_ROOT_MISMATCH';
        END IF;
        IF (SELECT count(*) FROM store_profile_versions WHERE profile_id=root_row.id) <> 1 THEN
            RAISE EXCEPTION 'ALLO_PROFILE_ARCHIVE_EXISTING_VERSIONS_MISMATCH';
        END IF;
        SELECT * INTO version_row FROM store_profile_versions WHERE profile_id=root_row.id
            AND profile_version=payload->>'profile_version' FOR SHARE;
        IF NOT FOUND OR version_row.fingerprint_sha256 <> payload->>'fingerprint_sha256'
           OR version_row.schema_version <> payload->>'schema_version' OR version_row.status <> 'PUBLISHED'
           OR version_row.content_json <> payload->>'content_json'
           OR version_row.source_reference <> payload->>'source_reference' THEN
            RAISE EXCEPTION 'ALLO_PROFILE_ARCHIVE_EXISTING_VERSION_MISMATCH';
        END IF;
        IF (SELECT count(*) FROM store_profile_artifacts WHERE profile_version_id=version_row.id)
            <> jsonb_array_length(payload->'artifacts') THEN
            RAISE EXCEPTION 'ALLO_PROFILE_ARCHIVE_EXISTING_ARTIFACTS_MISMATCH';
        END IF;
        FOR artifact IN SELECT value FROM jsonb_array_elements(payload->'artifacts') LOOP
            IF NOT EXISTS (SELECT 1 FROM store_profile_artifacts WHERE profile_version_id=version_row.id
                AND artifact_type=artifact->>'artifact_type' AND artifact_code=artifact->>'artifact_code'
                AND artifact_version=artifact->>'artifact_version' AND content_json=artifact->>'content_json'
                AND fingerprint_sha256=artifact->>'fingerprint_sha256') THEN
                RAISE EXCEPTION 'ALLO_PROFILE_ARCHIVE_EXISTING_ARTIFACT_MISMATCH';
            END IF;
        END LOOP;
        PERFORM set_config('demo.profile_archive_outcome','EXACT_REPLAY',true);
    ELSE
        INSERT INTO store_profiles(profile_code,display_name,description,status,provenance,visibility,owner_organization_id,created_at,updated_at)
        VALUES (payload->>'profile_code',payload->>'display_name','Local configuration archive; no apply or provisioning entry',
            'DRAFT',payload->>'provenance','PRIVATE',organization_key,current_timestamp,current_timestamp) RETURNING * INTO root_row;
        INSERT INTO store_profile_versions(profile_id,profile_version,status,schema_version,content_json,fingerprint_sha256,source_reference,created_at,updated_at)
        VALUES(root_row.id,payload->>'profile_version','DRAFT',payload->>'schema_version',payload->>'content_json',
            payload->>'fingerprint_sha256',payload->>'source_reference',current_timestamp,current_timestamp) RETURNING * INTO version_row;
        FOR artifact IN SELECT value FROM jsonb_array_elements(payload->'artifacts') LOOP
            INSERT INTO store_profile_artifacts(profile_version_id,artifact_type,artifact_code,artifact_version,content_json,fingerprint_sha256,created_at,updated_at)
            VALUES(version_row.id,artifact->>'artifact_type',artifact->>'artifact_code',artifact->>'artifact_version',
                artifact->>'content_json',artifact->>'fingerprint_sha256',current_timestamp,current_timestamp);
        END LOOP;
        UPDATE store_profile_versions SET status='PUBLISHED',published_at=current_timestamp,updated_at=current_timestamp WHERE id=version_row.id;
        UPDATE store_profiles SET status='PUBLISHED',updated_at=current_timestamp WHERE id=root_row.id;
        PERFORM set_config('demo.profile_archive_outcome','CREATED',true);
    END IF;
END;
$allo_private_archive$;
SELECT json_build_object('profile_code',p.profile_code,'profile_version',v.profile_version,
    'visibility',p.visibility,'fingerprint_sha256',v.fingerprint_sha256,
    'outcome',current_setting('demo.profile_archive_outcome'),'artifact_count',
    (SELECT count(*) FROM store_profile_artifacts a WHERE a.profile_version_id=v.id))
FROM store_profiles p JOIN store_profile_versions v ON v.profile_id=p.id
WHERE p.profile_code='ALLO_NOUILLE_PROFILE' AND v.profile_version='v1';
COMMIT;
"""
    result = life.sql(run, statement, DATABASE, False)
    report = next((json.loads(line) for line in result.splitlines() if line.startswith('{')), None)
    if report is None or report.get('fingerprint_sha256') != package['fingerprint_sha256']:
        raise RuntimeError('Profile archive verification response missing')
    life.put(run / 'profile-archive.json', report)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('build', 'archive'))
    parser.add_argument('--runtime')
    parser.add_argument('--output')
    args = parser.parse_args()
    if args.command == 'build':
        result = build_package()
    else:
        if not args.runtime:
            parser.error('archive requires --runtime')
        result = archive(args.runtime)
    text = json.dumps(result, ensure_ascii=False, indent=2) + '\n'
    if args.output:
        pathlib.Path(args.output).write_text(text)
    else:
        sys.stdout.write(text)


if __name__ == '__main__':
    main()
