"""Real two-class weights must map detections without silently changing stock."""
import base64
import importlib.util
import json
import os
import tempfile
import unittest
import uuid
from types import SimpleNamespace
from pathlib import Path
from unittest.mock import patch

from service import Service, Error
from model_store import prepare_weights
from webapp import Application

ROOT = Path(__file__).resolve().parent.parent
WEIGHTS = ROOT / 'models/best.pt'
PHOTO = ROOT / 'tests/fixtures/magnolia-sack.jpg'


@unittest.skipUnless(WEIGHTS.is_file() and PHOTO.is_file() and importlib.util.find_spec('ultralytics'), 'Real model and inference dependency required')
class MagnoliaIntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        from ultralytics import YOLO
        cls.model = YOLO(str(WEIGHTS))

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / 'test.db'
        # Reuse the trusted model so this suite doesn't load it for every case.
        with patch('ultralytics.YOLO', return_value=self.model):
            self.s = Service(self.path, 'yolo', WEIGHTS)
        self.s.bootstrap('owner', 'magnolia-test-password')
        self.user = self.s.auth(self.s.login({'username': 'owner', 'password': 'magnolia-test-password'})['token'])
        self.r = self.s.rice
        self.state = self.r.state(self.user)
        self.magnolia = next(p for p in self.state['products'] if p['model_class'] == 'magnoliaRice')
        self.pid = self.magnolia['id']

    def req(self, **data):
        return {'request_id': str(uuid.uuid4()), **data}

    def test_model_identity_and_existing_products_are_preserved(self):
        scanner = self.state['scanner']
        self.assertTrue(scanner['ready'])
        self.assertEqual(scanner['classes'], ['buko_pandan', 'magnoliaRice'])
        self.assertTrue(self.state['products'][2]['scan_supported'])
        self.assertEqual(scanner['sha256'], json.loads((ROOT / 'models/model.json').read_text())['sha256'])
        self.assertEqual([(p['id'], p['model_class']) for p in self.state['products'][:3]],
                         [(1, 'sinandomeng'), (2, 'jasmine'), (3, 'buko_pandan')])
        self.assertTrue(self.magnolia['scan_supported'])
        self.assertFalse(self.state['products'][0]['scan_supported'])
        with patch('ultralytics.YOLO', return_value=self.model):
            restarted = Service(self.path, 'yolo', WEIGHTS)
        self.assertEqual(len(restarted.rice.state(self.user)['products']), 4)

    def test_real_photo_review_and_atomic_submission(self):
        aid = self.r.open(self.user, self.req(kind='stock-in', products=[self.pid]))['action_id']
        payload = {'action_id': aid, 'image_id': 'magnolia-photo', 'source': 'camera', 'zoom': 1,
                   'image': base64.b64encode(PHOTO.read_bytes()).decode()}
        self.r.image(self.user, payload)
        action = self.r.state(self.user, aid)['action']
        image = action['images'][0]
        self.assertGreater(len(image['raw']), 0)
        self.assertTrue(all(d['class'] == 'magnoliaRice' and d['product_id'] == self.pid for d in image['raw']))
        self.assertEqual(self.r.state(self.user)['products'][-1]['quantity'], 0)
        with self.assertRaises(Error):
            self.r.submit(self.user, self.req(action_id=aid, revision=action['revision'], unique_sacks=True, reason='Unchecked scan'))
        self.r.review(self.user, self.req(action_id=aid, revision=action['revision'], image_id=image['id'],
                                        checked=[{'product_id': self.pid, 'quantity': 1}], confirmed=True))
        self.assertEqual(self.r.state(self.user)['products'][-1]['quantity'], 0)
        action = self.r.state(self.user, aid)['action']
        request = self.req(action_id=aid, revision=action['revision'], unique_sacks=True, reason='One Magnolia sack verified')
        result = self.r.submit(self.user, request)
        self.assertEqual(self.r.submit(self.user, request), result)
        self.assertEqual(self.r.state(self.user)['products'][-1]['quantity'], 1)

    def test_two_class_predictions_map_to_existing_products_without_changing_stock(self):
        import numpy as np
        buko = self.state['products'][2]
        with self.s.connection() as c:
            c.execute('UPDATE rice_products SET quantity=17 WHERE id=?', (buko['id'],))
        aid = self.r.open(self.user, self.req(kind='stock-in', products=[buko['id'], self.pid]))['action_id']
        prediction = SimpleNamespace(names=self.model.names, boxes=[
            SimpleNamespace(cls=[0], conf=[.9], xyxyn=np.array([[.1, .1, .4, .5]])),
            SimpleNamespace(cls=[1], conf=[.8], xyxyn=np.array([[.5, .1, .9, .5]])),
        ])
        with patch.object(self.model, 'predict', return_value=[prediction]):
            self.r.image(self.user, {'action_id': aid, 'image_id': 'both-products', 'source': 'camera',
                                    'zoom': 1, 'image': base64.b64encode(PHOTO.read_bytes()).decode()})
        detections = self.r.state(self.user, aid)['action']['images'][0]['raw']
        self.assertEqual([(d['product_id'], d['class']) for d in detections],
                         [(buko['id'], 'buko_pandan'), (self.pid, 'magnoliaRice')])
        with patch('ultralytics.YOLO', return_value=self.model):
            restarted = Service(self.path, 'yolo', WEIGHTS)
        products = restarted.rice.state(self.user)['products']
        self.assertEqual(len(products), 4)
        self.assertEqual(products[2]['quantity'], 17)
        self.assertEqual(products[-1]['quantity'], 0)

    def test_android_and_render_expect_the_bundled_model(self):
        manifest = json.loads((ROOT / 'models/model.json').read_text())
        android = json.loads((ROOT / 'android/app/src/main/assets/scanner_model.json').read_text())
        self.assertEqual(manifest, android)
        self.assertEqual(manifest['classes'], list(self.model.names.values()))
        self.assertEqual(manifest['sha256'], self.s.weights_digest)
        self.assertIn('value: ' + manifest['sha256'], (ROOT / 'render.yaml').read_text())

    def test_four_products_can_be_reviewed_and_admin_updated(self):
        products = self.state['products']
        aid = self.r.open(self.user, self.req(kind='count', products=[p['id'] for p in products]))['action_id']
        self.assertEqual(len(self.r.state(self.user, aid)['action']['snapshot']), 4)
        checked = [{'product_id': p['id'], 'quantity': 0} for p in products]
        self.assertEqual(len(self.r.checked(checked, [p['id'] for p in products])), 4)
        self.r.product(self.user, {'product_id': self.pid, 'sku': 'MAGNOLIA-TEST',
                                  'model_class': 'magnoliaRice', 'description': 'Verified Magnolia sack'})
        self.assertEqual(self.r.state(self.user)['products'][-1]['sku'], 'MAGNOLIA-TEST')

    def test_bundled_weights_are_used_when_no_private_path_is_set(self):
        with patch.dict(os.environ, {'MOVIS_MODE': 'yolo'}, clear=True):
            self.assertEqual(Path(prepare_weights()), WEIGHTS)
