"""Two reviewed Test identities. This is not an arbitrary deployment configuration API."""
from pathlib import Path

DEFAULT_TARGET = 'restaurant-template-test'
ALLO_TARGET = 'allo-nouilles-test'
TARGET_NAMES = (DEFAULT_TARGET, ALLO_TARGET)
DEPLOYMENT_FILES = ('compose.yml', 'Dockerfile.backend', 'Dockerfile.frontend', 'nginx.conf',
                    'relay.conf', 'images.json', 'remote.py', 'deployment_targets.py')


def target_config(name=DEFAULT_TARGET):
    if name == DEFAULT_TARGET:
        return dict(name=name, root=Path('/srv/restaurant-pos/test'),
                    database='restaurant_template_test', user='template_test', profile='template-test',
                    frontend_port=28080, relay_port=28480,
                    owner_key='TEMPLATE_TEST_OWNER_PASSWORD', manager_key='TEMPLATE_TEST_MANAGER_PASSWORD')
    if name == ALLO_TARGET:
        return dict(name=name, root=Path('/srv/restaurant-pos/allo-nouilles-test'),
                    database='restaurant_allo_nouilles_test', user='allo_test', profile='allo-test',
                    frontend_port=28080, relay_port=28480,
                    owner_key='ALLO_TEST_OWNER_PASSWORD', manager_key='ALLO_TEST_MANAGER_PASSWORD')
    raise ValueError('only the two reviewed Test targets are allowed')


def rendered_files(here, name=DEFAULT_TARGET):
    target_config(name)
    files = {n: (Path(here) / n).read_bytes() for n in DEPLOYMENT_FILES}
    if name == DEFAULT_TARGET:
        return files  # Existing Template topology and config bytes stay unchanged.
    compose = files['compose.yml'].decode()
    for old, new in (
        ('restaurant-template-test', ALLO_TARGET),
        ('restaurant_template_test', 'restaurant_allo_nouilles_test'),
        ('template_test', 'allo_test'),
        ('SPRING_PROFILES_ACTIVE: template-test', 'SPRING_PROFILES_ACTIVE: allo-test'),
        ('TEMPLATE_TEST_OWNER_PASSWORD', 'ALLO_TEST_OWNER_PASSWORD'),
        ('TEMPLATE_TEST_MANAGER_PASSWORD', 'ALLO_TEST_MANAGER_PASSWORD'),
    ):
        if old not in compose:
            raise ValueError('reviewed Compose anchor changed: ' + old)
        compose = compose.replace(old, new)
    anchor = '    depends_on:\n      db: {condition: service_healthy}'
    if compose.count(anchor) != 1:
        raise ValueError('reviewed backend mount anchor changed')
    compose = compose.replace(anchor, "    volumes: ['./merchant/menu.source.json:/run/merchant/menu.source.json:ro']\n" + anchor)
    files['compose.yml'] = compose.encode()
    relay = files['relay.conf'].decode()
    if relay.count('172.18.0.1:28480') != 1 or relay.count('127.0.0.1:28080') != 1:
        raise ValueError('reviewed relay anchor changed')
    files['relay.conf'] = relay.encode()  # Reuse the retired Template slot; shared HTTPS config stays untouched.
    return files


def validate_inventory(files, name):
    target_config(name)
    if name == DEFAULT_TARGET:
        return  # Older sealed Template bundles predate the added target module.
    names = set(files)
    fixed = set(DEPLOYMENT_FILES) | {'app.jar', 'checks.json', 'code-sha.txt', 'merchant/menu.source.json'}
    dist = {n for n in names if n.startswith('dist/') and n != 'dist/' and '..' not in Path(n).parts}
    if 'dist/index.html' not in dist or names != fixed | dist:
        raise ValueError('Allo bundle inventory is not the closed reviewed artifact set')
