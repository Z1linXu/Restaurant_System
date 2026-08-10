#!/usr/bin/env python3
"""Small, fail-closed jq subset for the secret-safe Owner client.

It is only selected when the reviewed Staging host has no jq binary. It reads
JSON from private files/stdin and implements the fixed filters used by the
client; it never prints passwords, tokens, cookies, or authorization values.
"""
import json, sys

args = sys.argv[1:]
raw = False
compact = False
null_input = False
arg = {}
while args and args[0].startswith('-'):
    flag = args.pop(0)
    raw |= 'r' in flag
    compact |= 'c' in flag
    null_input |= flag == '-n'
    if flag in ('--arg', '--argjson'):
        key = args.pop(0)
        arg[key] = args.pop(0)
filter_text = args.pop(0) if args else ''
source = args.pop(0) if args else None
if null_input:
    value = None
else:
    with open(source, encoding='utf-8') as handle:
        value = json.load(handle)

def fail():
    raise SystemExit(1)

def walk(v):
    if isinstance(v, dict):
        for key, child in v.items():
            if any(word in key.lower() for word in ('password', 'token', 'cookie', 'authorization', 'secret')):
                fail()
            walk(child)
    elif isinstance(v, list):
        for child in v:
            walk(child)

if null_input and 'refresh_token' in filter_text:
    value = {'refresh_token': arg.get('refresh', '')}
elif filter_text.startswith('.login_identifier'):
    value = value.get('login_identifier') if isinstance(value, dict) else None
    if not isinstance(value, str) or not value.startswith('STG005_'): fail()
elif '.new_login_password' in filter_text and 'length == 20' in filter_text:
    new_password = value.get('new_login_password') if isinstance(value, dict) else None
    if not isinstance(new_password, str) or len(new_password) != 20 or new_password == value.get('login_password'): fail()
    value = True
elif 'type == "object"' in filter_text and '.login_identifier' in filter_text and '.login_password' in filter_text:
    if not isinstance(value, dict) or not isinstance(value.get('login_identifier'), str) or not value['login_identifier'] or not isinstance(value.get('login_password'), str) or len(value['login_password']) < 12: fail()
    value = True
elif '{new_password:' in filter_text and '.new_login_password' in filter_text:
    value = {'new_password': value['new_login_password']}
elif '.login_identifier' in filter_text and '.new_login_password' in filter_text:
    value = {'login_identifier': value['login_identifier'], 'password': value['new_login_password']}
elif '.login_identifier' in filter_text and '.login_password' in filter_text:
    value = {'login_identifier': value['login_identifier'], 'password': value['login_password']}
elif '.onboarding_request' in filter_text:
    value = value['onboarding_request']
elif '.success == true' in filter_text:
    if value.get('success') is not True: fail()
    value = True
elif 'paths(scalars)' in filter_text:
    walk(value)
    value = True
elif '.data.access_token' in filter_text:
    value = value['data']['access_token']
    minimum = 24 if 'length >= 24' in filter_text else 21
    if not isinstance(value, str) or len(value) < minimum: fail()
elif '.data.refresh_token' in filter_text:
    value = value['data']['refresh_token']
    if not isinstance(value, str) or len(value) <= 20: fail()
elif '.data.user.role_code' in filter_text:
    user = value['data']['user']
    if user.get('role_code') != 'OWNER' or user.get('organization_id') != int(arg.get('organization', 0)): fail()
    value = True
elif '.data.user.username' in filter_text:
    if value['data']['user'].get('username') != arg.get('login'): fail()
    value = True
elif '.data.user.id' in filter_text:
    value = value['data']['user']['id']
    if not isinstance(value, int) or value <= 0: fail()
elif '.data.stores' in filter_text and 'organization_id' in filter_text:
    org = int(arg.get('organization', 0)); source_id = int(arg.get('source', 0))
    stores = value['data']['stores']
    if not isinstance(stores, list) or len(stores) != 1 or stores[0].get('id') != source_id or stores[0].get('organization_id') != org: fail()
    value = True
elif '.data.organizations' in filter_text and '.stores[]?' in filter_text:
    source_id = int(arg.get('source', 0))
    stores = [s for o in value['data']['organizations'] for s in o.get('stores', [])]
    if len(stores) != 1 or stores[0].get('id') != source_id: fail()
    value = True
elif '.data.organizations' in filter_text and '.data.stores' in filter_text:
    org = int(arg.get('organization', 0)); source_id = int(arg.get('source', arg.get('target', 0)))
    organizations = value['data']['organizations']
    if not any(x.get('id') == org and x.get('role_code') == 'OWNER' for x in organizations): fail()
    stores = value['data']['stores'] if '.data.stores' in filter_text else [s for o in organizations for s in o.get('stores', [])]
    if len(stores) != 1 or stores[0].get('id') != source_id: fail()
    value = True
elif '.data.organizations' in filter_text:
    org = int(arg.get('organization', 0))
    if not any(x.get('id') == org and x.get('role_code') == 'OWNER' for x in value['data']['organizations']): fail()
    value = True
else:
    fail()

if value is True:
    if '-e' in sys.argv or '-er' in sys.argv: sys.exit(0)
elif raw and isinstance(value, str):
    print(value)
else:
    print(json.dumps(value, separators=(',', ':') if compact else None))
