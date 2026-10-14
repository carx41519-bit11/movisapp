"""Live HTTP integration proof in a unique disposable schema on the linked branch.

Uses real Neon Postgres and S3 with synthetic inference. No existing inventory or
accounts are modified. Test rows and uploaded objects are removed in finally.
Run explicitly with npm run test:neon; excluded from unittest discovery.
"""
import base64
import io
import json
import os
import secrets
import socket
import subprocess
import sys
import time
import uuid
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit, parse_qsl, urlencode, quote
from urllib.request import Request, urlopen


def main():
    from environment import load_environment
    load_environment()
    import psycopg
    from psycopg import sql
    from PIL import Image
    from object_storage import ImageStorage, PREFIX

    direct = os.environ['DATABASE_URL_UNPOOLED']
    schema = 'movis_e2e_' + uuid.uuid4().hex
    parts = urlsplit(direct)
    query = dict(parse_qsl(parts.query))
    query['options'] = '-c search_path=' + schema
    isolated_url = parts._replace(query=urlencode(query, quote_via=quote)).geturl()
    child_env = dict(os.environ, DATABASE_URL=isolated_url, MOVIS_MODE='demo',
                     MOVIS_ADMIN_USERNAME='e2e-owner', MOVIS_ADMIN_PASSWORD=secrets.token_urlsafe(32),
                     MOVIS_SEED_DEMO='0', HOST='127.0.0.1')
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        child_env['PORT'] = str(sock.getsockname()[1])
    base = 'http://127.0.0.1:' + child_env['PORT']
    token = None
    process = None
    storage = ImageStorage()
    assert storage.client is not None, 'Neon storage configuration missing'

    def request(path, data=None, authenticated=True):
        headers = {'Content-Type': 'application/json'}
        if authenticated and token:
            headers['Authorization'] = 'Bearer ' + token
        req = Request(base + path, data=None if data is None else json.dumps(data).encode(), headers=headers)
        with urlopen(req, timeout=60) as response:
            return json.loads(response.read())

    def start():
        proc = subprocess.Popen([sys.executable, 'backend/run.py'], env=child_env,
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            for _ in range(90):
                if proc.poll() is not None:
                    raise RuntimeError('App startup failed; exit code ' + str(proc.returncode))
                try:
                    if request('/health')['status'] == 'ok':
                        return proc
                except (URLError, OSError):
                    time.sleep(1)
            raise RuntimeError('App did not become healthy within 90 seconds')
        except BaseException:
            proc.terminate()
            proc.wait(timeout=10)
            raise

    with psycopg.connect(direct, autocommit=True) as con:
        con.execute(sql.SQL('CREATE SCHEMA {}').format(sql.Identifier(schema)))
    try:
        # Verify normal pooled application traffic independently of isolated-schema tests.
        with psycopg.connect(os.environ['DATABASE_URL'], connect_timeout=15) as con:
            assert con.execute('SELECT 1').fetchone()[0] == 1
        print('PASS: pooled Neon PostgreSQL connection', flush=True)
        process = start()
        print('PASS: app started; GET /health = 200 with live Neon database', flush=True)
        token = request('/login', {'username': child_env['MOVIS_ADMIN_USERNAME'],
                                  'password': child_env['MOVIS_ADMIN_PASSWORD']})['token']
        aid = request('/v2/open', {'request_id': str(uuid.uuid4()), 'kind': 'stock-in', 'products': [1]})['action_id']
        out = io.BytesIO()
        Image.new('RGB', (32, 32), 'white').save(out, format='JPEG')
        photo = base64.b64encode(out.getvalue()).decode()
        iid = str(uuid.uuid4())
        upload = {'action_id': aid, 'image_id': iid, 'source': 'camera', 'zoom': 1, 'image': photo}
        request('/v2/image', upload)
        request('/v2/image', upload)
        state = request('/v2/state?action_id=' + aid + '&images=1')
        assert state['action']['images'][0]['image'] == photo
        assert state['products'][0]['quantity'] == 0
        with psycopg.connect(isolated_url) as con:
            ref = con.execute('SELECT image FROM rice_images WHERE id=%s', (iid,)).fetchone()[0]
            assert ref.startswith(PREFIX), 'Image bytes must live in S3, not Postgres'
        bucket, key = ref[len(PREFIX):].split('/', 1)
        assert storage.client.head_object(Bucket=bucket, Key=key)['ContentLength'] == len(out.getvalue())
        print('PASS: authenticated image upload/retry -> private Neon S3 -> byte-identical HTTP download; DB stores object reference', flush=True)
        try:
            request('/v2/state?action_id=' + aid + '&images=1', authenticated=False)
            raise AssertionError('Unauthenticated image read accepted')
        except HTTPError as exc:
            assert exc.code == 401
        print('PASS: unauthenticated image access rejected (401)', flush=True)
        process.terminate()
        process.wait(timeout=10)
        process = start()
        state = request('/v2/state?action_id=' + aid + '&images=1')
        assert state['action']['images'][0]['image'] == photo
        print('PASS: session, action and S3 image survived app restart', flush=True)
        request('/v2/review', {'request_id': str(uuid.uuid4()), 'action_id': aid,
                              'revision': state['action']['revision'], 'image_id': iid,
                              'checked': [{'product_id': 1, 'quantity': 2}], 'confirmed': True})
        state = request('/v2/state?action_id=' + aid)
        request('/v2/submit', {'request_id': str(uuid.uuid4()), 'action_id': aid,
                              'revision': state['action']['revision'], 'unique_sacks': True,
                              'reason': 'Isolated Neon end-to-end test'})
        assert request('/v2/state')['products'][0]['quantity'] == 2
        print('PASS: HTTP review and stock submission persisted quantity 2 in Neon PostgreSQL', flush=True)
        # Use a second open action to exercise real app deletion.
        aid2 = request('/v2/open', {'request_id': str(uuid.uuid4()), 'kind': 'count', 'products': [1]})['action_id']
        iid2 = str(uuid.uuid4())
        request('/v2/image', dict(upload, action_id=aid2, image_id=iid2))
        state2 = request('/v2/state?action_id=' + aid2)
        with psycopg.connect(isolated_url) as con:
            ref2 = con.execute('SELECT image FROM rice_images WHERE id=%s', (iid2,)).fetchone()[0]
        deletion = {'request_id': str(uuid.uuid4()), 'action_id': aid2,
                    'revision': state2['action']['revision'], 'image_id': iid2}
        request('/v2/delete', deletion)
        request('/v2/delete', deletion)
        from botocore.exceptions import ClientError
        b2, k2 = ref2[len(PREFIX):].split('/', 1)
        try:
            storage.client.head_object(Bucket=b2, Key=k2)
            raise AssertionError('Deleted S3 image still accessible')
        except ClientError as exc:
            assert exc.response['ResponseMetadata']['HTTPStatusCode'] == 404
        print('PASS: HTTP image deletion and retry removed the S3 object', flush=True)
        print('NEON E2E PASS (synthetic inference; real PostgreSQL and object storage)', flush=True)
    finally:
        if process is not None and process.poll() is None:
            process.terminate()
            process.wait(timeout=10)
        with psycopg.connect(isolated_url) as con:
            exists = con.execute("SELECT to_regclass('rice_images')").fetchone()[0]
            if exists:
                for (value,) in con.execute('SELECT image FROM rice_images'):
                    storage.delete(value)
        with psycopg.connect(direct, autocommit=True) as con:
            con.execute(sql.SQL('DROP SCHEMA {} CASCADE').format(sql.Identifier(schema)))
        print('CLEANUP PASS: test schema and test objects removed', flush=True)


if __name__ == '__main__':
    main()
