#!/usr/bin/env python3
"""Generate an offline retention review. There is deliberately no execute mode."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import re

CACHE_AGE_DAYS = 30  # Existing installed shared-host policy, not the old 7-day helper.


def timestamp(value):
    if not isinstance(value, str):
        raise ValueError('timestamp missing')
    parsed = dt.datetime.fromisoformat(re.sub(r'(\.\d{6})\d+', r'\1', value).replace('Z', '+00:00'))
    if parsed.tzinfo is None:
        raise ValueError('timestamp lacks timezone')
    return parsed


def review(snapshot, policy):
    if snapshot.get('schema_version') != 1:
        raise ValueError('unsupported metadata schema')
    now = timestamp(snapshot['observed_utc'])
    if type(snapshot.get('containers_complete')) is not bool or not snapshot['containers_complete']:
        raise ValueError('complete running and stopped container inventory required')
    if snapshot.get('docker_socket') != 'unix:///var/run/docker.sock':
        raise ValueError('unexpected Docker source')
    containers = snapshot['containers']
    images = snapshot['images']
    for collection in (containers, images, snapshot['cache'], snapshot['releases']):
        if not isinstance(collection, list) or len(collection) > 5000:
            raise ValueError('metadata collection is invalid or exceeds budget')
    image_ids = {image['Id'] for image in images}
    if len(image_ids) != len(images):
        raise ValueError('duplicate image identities')
    rollback = policy.get('rollback_image_ids', [])
    if not isinstance(rollback, list) or not set(rollback).issubset(image_ids):
        raise ValueError('rollback image identity missing from inventory')
    protected = {image: [] for image in image_ids}
    for container in containers:
        image = container['image_id']
        if image not in image_ids or type(container.get('running')) is not bool:
            raise ValueError('container/image inventory inconsistent')
        state = 'RUNNING_CONTAINER' if container['running'] else 'STOPPED_CONTAINER_RECOVERY'
        protected[image].append(state + ':' + container['name'])
    for image in rollback:
        protected[image].append('EXPLICIT_ROLLBACK_IMAGE')
    # Allô Nouilles and opaque tool dependencies have no proven complete mapping.
    # An unreferenced Docker image is a review item, never an eligible deletion.
    image_plan = []
    for image in sorted(images, key=lambda item: item['Id']):
        reasons = protected[image['Id']] or ['UNVERIFIED_EXTERNAL_OR_TOOLING_DEPENDENCIES']
        image_plan.append({'id': image['Id'], 'tags': image.get('RepoTags') or [],
                           'status': 'RETAINED', 'reasons': reasons,
                           'logical_bytes': image.get('Size'), 'shared_bytes': image.get('SharedSize')})

    gate = snapshot.get('operation_gate', {})
    gate_clear = gate.get('complete') is True and gate.get('active') == []
    cache_plan, seen = [], set()
    for record in sorted(snapshot['cache'], key=lambda item: item.get('ID', '')):
        cid = record.get('ID')
        if not isinstance(cid, str) or not re.fullmatch('[A-Za-z0-9._:-]+', cid) or cid in seen:
            raise ValueError('invalid or duplicate cache identity')
        seen.add(cid)
        reasons = []
        if type(record.get('Size')) is not int or record['Size'] < 0:
            raise ValueError('cache size invalid')
        if type(record.get('InUse')) is not bool or type(record.get('Shared')) is not bool:
            reasons.append('NATIVE_REFERENCE_METADATA_UNKNOWN')
        else:
            if record['InUse']:
                reasons.append('IN_USE')
            if record['Shared']:
                reasons.append('SHARED_WITH_IMAGE_OR_OTHER_REFERENCE')
        if not isinstance(record.get('Type'), str) or record['Type'] in ('internal', 'frontend'):
            reasons.append('INTERNAL_FRONTEND_OR_UNKNOWN_TYPE')
        try:
            age = (now - timestamp(record.get('LastUsedAt'))).total_seconds()
            if age < 0:
                reasons.append('FUTURE_LAST_USE')
            elif age <= CACHE_AGE_DAYS * 86400:
                reasons.append('LAST_USE_NOT_OLDER_THAN_720H')
        except (ValueError, TypeError):
            reasons.append('LAST_USE_UNKNOWN')
        if not gate_clear:
            reasons.append('OPERATION_GATE_ACTIVE_OR_UNKNOWN')
        cache_plan.append({'id': cid, 'logical_bytes': record['Size'],
                           'last_used_at': record.get('LastUsedAt'),
                           'status': 'RETAINED' if reasons else 'REVIEW_CANDIDATE',
                           'reasons': reasons or ['PRIVATE_UNUSED_LAST_USE_OLDER_THAN_720H'],
                           'rebuildability': 'UNKNOWN_UNTIL_SOURCE_LOCKFILES_AND_REQUIRED_ARTIFACTS_ARE_VERIFIED',
                           'recovery': 'Cache deletion has no byte-for-byte rollback. Any future approval must accept rebuild cost and dependency availability.'})

    release_plan, paths = [], set()
    references = []
    for container in containers:
        references.extend(container.get('mount_sources') or [])
        # Config.WorkingDir is inside the container, not a host pathname.
        # Only Compose's host working directory can protect a host release.
        references.extend(value for value in [container.get('compose_working_dir')] if value)
        references.extend((container.get('compose_files') or '').split(','))
    for release in sorted(snapshot['releases'], key=lambda item: item['path']):
        path = release['path']
        if not isinstance(path, str) or not path.startswith('/') or path in paths:
            raise ValueError('invalid or duplicate release identity')
        paths.add(path)
        reasons = []
        if any(ref and (ref == path or ref.startswith(path + '/') or path.startswith(ref.rstrip('/') + '/')) for ref in references):
            reasons.append('CONTAINER_CONFIG_MOUNT_OR_WORKDIR_REFERENCE')
        if release.get('contains_windows_package') or release.get('data_paths'):
            reasons.append('DATA_BEARING_OR_WINDOWS_DISTRIBUTION')
        if release.get('identity_verified') is not True:
            reasons.append('IDENTITY_OR_SYMLINK_UNVERIFIED')
        if release.get('references_complete') is not True:
            reasons.append('OPAQUE_OR_INCOMPLETE_RECOVERY_REFERENCES')
        # This companion does not supersede the installed, digest-bound worktree remover.
        reasons.append('EXISTING_RELEASE_RETENTION_REVIEW_REQUIRED')
        release_plan.append({'path': path, 'sha': release.get('sha'), 'status': 'RETAINED',
                             'reasons': reasons, 'identity': release.get('identity'),
                             'data_paths': release.get('data_paths') or []})
    candidates = [row for row in cache_plan if row['status'] == 'REVIEW_CANDIDATE']
    return {'schema_version': 1, 'observed_utc': snapshot['observed_utc'],
            'mode': 'DRY_RUN_ONLY', 'execute_supported': False,
            'automatic_cleanup_enabled': False, 'images': image_plan, 'cache': cache_plan,
            'containers': containers,
            'volumes': [{'name': volume['Name'], 'status': 'RETAINED',
                         'reason': 'DATABASE_PAUSED_ENVIRONMENT_OR_UNVERIFIED_PERSISTENT_DATA',
                         'metadata': volume} for volume in snapshot.get('volumes', [])],
            'releases': release_plan, 'logs': snapshot.get('logs', []), 'journal': snapshot.get('journal', {}),
            'operation_gate': gate, 'metadata_errors': snapshot.get('errors', []),
            'policy_files': snapshot.get('policy_files', []),
            'cache_candidate_ids': [row['id'] for row in candidates],
            'cache_candidate_logical_bytes_upper_bound': sum(row['logical_bytes'] for row in candidates),
            'image_candidate_ids': [], 'release_candidate_paths': [],
            'accounting_note': 'No combined reclaim total: images/cache share layers; release/log allocated bytes use another basis.',
            'authorization_note': 'Candidates are evidence only. Fresh exact-object review and independent Owner approval are required before any cleanup.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--snapshot', required=True, type=Path)
    parser.add_argument('--policy', required=True, type=Path)
    parser.add_argument('--dry-run', action='store_true', help='Explicit alias; read-only is always the only mode.')
    args = parser.parse_args()
    raw = args.snapshot.read_bytes()
    if len(raw) > 16 * 1024 * 1024:
        raise ValueError('snapshot exceeds budget')
    policy_raw = args.policy.read_bytes()
    if len(policy_raw) > 1024 * 1024:
        raise ValueError('policy exceeds budget')
    result = review(json.loads(raw), json.loads(policy_raw))
    result['snapshot_sha256'] = hashlib.sha256(raw).hexdigest()
    result['policy_sha256'] = hashlib.sha256(policy_raw).hexdigest()
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == '__main__':
    main()
