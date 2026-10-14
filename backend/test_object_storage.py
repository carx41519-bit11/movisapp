import base64
import os
import unittest
from unittest.mock import Mock, patch
from object_storage import ImageStorage
from service import Error


class ImageStorageTests(unittest.TestCase):
    def setUp(self):
        with patch.dict(os.environ, {}, clear=True):
            self.storage = ImageStorage()

    def test_legacy_inline_images_remain_readable(self):
        photo = base64.b64encode(b'legacy photo').decode()
        self.assertEqual(self.storage.get(photo), photo)
        self.assertEqual(self.storage.put('a', 'i', b'legacy photo', 'image/jpeg'), photo)

    def test_storage_errors_do_not_fall_back_to_inline(self):
        self.storage.client = Mock()
        self.storage.client.put_object.side_effect = RuntimeError('unavailable')
        with self.assertRaises(Error) as caught:
            self.storage.put('a', 'i', b'photo', 'image/jpeg')
        self.assertEqual(caught.exception.status, 503)

    def test_storage_reference_without_credentials_fails_closed(self):
        with self.assertRaises(Error) as caught:
            self.storage.get('neon-s3:assets/scans/key')
        self.assertEqual(caught.exception.status, 503)

    def test_partial_credentials_rejected(self):
        with patch.dict(os.environ, {'AWS_ENDPOINT_URL_S3': 'https://example.invalid'}, clear=True):
            with self.assertRaises(RuntimeError):
                ImageStorage()

    def test_storage_delete_failure_is_retryable(self):
        self.storage.client = Mock()
        self.storage.client.delete_object.side_effect = [RuntimeError('unavailable'), {}]
        with self.assertRaises(Error) as caught:
            self.storage.delete('neon-s3:assets/scans/key')
        self.assertEqual(caught.exception.status, 503)
        self.storage.delete('neon-s3:assets/scans/key')
        self.assertEqual(self.storage.client.delete_object.call_count, 2)


if __name__ == '__main__':
    unittest.main()
