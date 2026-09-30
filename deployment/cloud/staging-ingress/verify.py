#!/usr/bin/env python3
"""Read-only public HTTPS/CORS/assets/WebSocket acceptance. Never logs credentials."""
import base64
import hashlib
import json
import os
import re
import socket
import ssl
import urllib.error
import urllib.request

HOST = 'staging-pos.lanzhounoodlesmtl.com'
BASE = 'https://' + HOST
results = []

def check(condition, name, **details):
    results.append(dict(check=name, passed=bool(condition), **details))
    if not condition:
        print(json.dumps(results, indent=2))
        raise SystemExit('FAIL: ' + name)

def get(path, headers=None):
    request = urllib.request.Request(BASE + path, headers=headers or {})
    try:
        response = urllib.request.urlopen(request, timeout=12)
    except urllib.error.HTTPError as error:
        response = error
    return response.status, response.read(), response.headers

status, html, _ = get('/')
check(status == 200, 'frontend', sha256=hashlib.sha256(html).hexdigest())
for asset in sorted(set(re.findall(r'(?:src|href)="(/assets/[^\"]+)"', html.decode()))):
    code, body, _ = get(asset)
    check(code == 200 and bool(body), 'asset', path=asset, sha256=hashlib.sha256(body).hexdigest())
for origin, expected in [(BASE, 200), ('https://restaurant-pad.local', 200),
                         ('http://localhost:5173', 200), ('http://127.0.0.1:5173', 200),
                         ('http://192.168.1.9:5173', 200), ('https://attacker.invalid', 403),
                         ('https://pos.lanzhounoodlesmtl.com', 403), (BASE + ':444', 403)]:
    code, body, headers = get('/api/v1/system/health', {'Origin': origin})
    check(code == expected, 'origin', origin=origin, status=code)
    check(headers.get('Access-Control-Allow-Origin') != '*', 'no_wildcard', origin=origin)
    if expected == 200:
        check(json.loads(body)['data']['status'] == 'UP', 'health_up', origin=origin)
code, body, _ = get('/api/v1/auth/me', {'Origin': BASE})
check(code == 401 and b'Invalid CORS' not in body, 'auth_401', status=code)
spoof = {'Origin': BASE, 'Forwarded': 'proto=http;host=attacker.invalid',
         'X-Forwarded-Proto': 'http', 'X-Forwarded-Host': 'attacker.invalid',
         'X-Forwarded-Port': '81', 'X-Forwarded-For': '198.51.100.42',
         'X-Forwarded-Prefix': '/evil'}
code, body, _ = get('/api/v1/system/health?ingress_probe=phase1-spoof', spoof)
check(code == 200 and json.loads(body)['data']['status'] == 'UP', 'spoofed_forwarding_ignored', status=code)
spoof['Origin'] = 'https://attacker.invalid'
code, _, _ = get('/api/v1/system/health', spoof)
check(code == 403, 'spoof_does_not_allow_invalid_origin', status=code)

# Real TLS + RFC6455 upgrade; check the server accept hash, then close the socket.
for path in ['/ws', '/ws/websocket']:
    with socket.create_connection((HOST, 443), timeout=10) as tcp:
        with ssl.create_default_context().wrap_socket(tcp, server_hostname=HOST) as ws:
            key = base64.b64encode(os.urandom(16)).decode()
            ws.sendall((f'GET {path} HTTP/1.1\r\nHost: {HOST}\r\nOrigin: {BASE}\r\n'
                        'Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\n'
                        f'Sec-WebSocket-Key: {key}\r\n\r\n').encode())
            data = b''
            while b'\r\n\r\n' not in data and len(data) < 16384:
                chunk = ws.recv(4096)
                if not chunk: break
                data += chunk
            response = data.decode(errors='replace')
            if response.startswith('HTTP/1.1 101'):
                accept = base64.b64encode(hashlib.sha1((key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest()).decode()
                check(accept in response, 'wss_rfc6455_handshake', path=path, status=101)
                ws.sendall(b'\x88\x80\x00\x00\x00\x00')
                break
else:
    check(False, 'wss_rfc6455_handshake')
code, _, _ = get('/ws/info', {'Origin': 'https://attacker.invalid'})
check(code == 403, 'invalid_websocket_origin_rejected', status=code)
print(json.dumps(results, indent=2))
