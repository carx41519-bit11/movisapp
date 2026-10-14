import base64
import io
import json
import unittest
import uuid
from unittest.mock import patch
from PIL import Image
from test_webapp import CloudTests


class FastActionTests(unittest.TestCase):
    setUp = CloudTests.setUp
    tearDown = CloudTests.tearDown

    def request(self, path, data, token='', known='', combined=True):
        raw = json.dumps(data).encode()
        status = []
        env = {'REQUEST_METHOD': 'POST', 'PATH_INFO': path,
               'CONTENT_LENGTH': str(len(raw)), 'CONTENT_TYPE': 'application/json',
               'wsgi.input': io.BytesIO(raw), 'HTTP_HOST': 'movis.example',
               'HTTP_AUTHORIZATION': 'Bearer ' + token,
               'HTTP_X_MOVIS_KNOWN_IMAGES': known,
               'HTTP_X_MOVIS_STATE': 'action' if combined else ''}
        body = b''.join(self.app(env, lambda s, h: status.append(int(s.split()[0]))))
        return status[0], json.loads(body)

    def login(self):
        code, result = self.request('/login', {'username': 'cloudadmin', 'password': 'private-password-2026'})
        self.assertEqual(code, 200)
        return result['token']

    def test_combined_stock_in_keeps_photos_local_and_retry_adds_stock_once(self):
        token = self.login()
        def post(path, data, known=''):
            code, result = self.request('/v2/' + path, data, token, known)
            self.assertEqual(code, 200, result)
            return result
        receipt = post('open', {'kind': 'stock-in', 'products': [1], 'request_id': str(uuid.uuid4())})
        aid = receipt['action_id']
        state = receipt['action_state']
        self.assertEqual(state['action']['id'], aid)
        before = state['products'][0]['quantity']
        image = io.BytesIO()
        Image.new('RGB', (32, 32), 'white').save(image, format='JPEG')
        iid = str(uuid.uuid4())
        photo = base64.b64encode(image.getvalue()).decode()
        receipt = post('image', {'action_id': aid, 'image_id': iid, 'source': 'camera', 'zoom': 1, 'image': photo})
        action = receipt['action_state']['action']
        self.assertTrue(action['images'][0]['image'])
        receipt = post('review', {'action_id': aid, 'revision': action['revision'], 'image_id': iid,
                                'checked': [{'product_id': 1, 'quantity': 3}], 'confirmed': True,
                                'request_id': str(uuid.uuid4())}, iid)
        action = receipt['action_state']['action']
        self.assertNotIn('image', action['images'][0])
        self.assertEqual(action['images'][0]['reviewed'], 1)
        payload = {'action_id': aid, 'revision': action['revision'], 'request_id': str(uuid.uuid4()),
                   'reason': 'Verified receipt', 'unique_sacks': True}
        for _ in range(2):
            receipt = post('submit', payload, iid)
            self.assertEqual(receipt['action_state']['products'][0]['quantity'], before + 3)
            self.assertNotIn('image', receipt['action_state']['action']['images'][0])
        user = self.service.auth(token)
        self.assertTrue(self.service.rice.state(user, aid, True)['action']['images'][0]['image'])
        with self.assertRaises(Exception):
            self.service.rice.state({'id': -999, 'role': 'operator'}, aid, True, [iid])

    def test_receipt_survives_refresh_failure_and_old_clients_remain_compatible(self):
        token = self.login()
        payload = {'kind': 'stock-in', 'products': [1], 'request_id': str(uuid.uuid4())}
        with patch.object(self.service.rice, 'state', side_effect=RuntimeError('refresh unavailable')):
            with self.assertLogs(level='ERROR'):
                code, receipt = self.request('/v2/open', payload, token)
        self.assertEqual(code, 200)
        self.assertIn('action_id', receipt)
        code, retried = self.request('/v2/open', payload, token)
        self.assertEqual(code, 200)
        self.assertEqual(retried['action_id'], receipt['action_id'])
        code, legacy = self.request('/v2/open', payload, token, combined=False)
        self.assertEqual(code, 200)
        self.assertNotIn('action_state', legacy)
        self.assertEqual(legacy['action_id'], receipt['action_id'])
