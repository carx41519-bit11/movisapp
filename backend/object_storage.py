"""Private scan images in Neon S3; legacy inline images remain readable."""
import base64
import hashlib
import os
from service import Error

PREFIX = 'neon-s3:'


class ImageStorage:
    def __init__(self):
        self.bucket = os.environ.get('MOVIS_IMAGE_BUCKET', 'assets')
        self.client = None
        endpoint = os.environ.get('AWS_ENDPOINT_URL_S3')
        if endpoint:
            import boto3
            from botocore.config import Config
            required = ('AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'AWS_REGION')
            if not all(os.environ.get(key) for key in required):
                raise RuntimeError('Neon storage requires all AWS credentials and AWS_REGION')
            self.client = boto3.client(
                's3', endpoint_url=endpoint, region_name=os.environ['AWS_REGION'],
                config=Config(signature_version='s3v4', s3={'addressing_style': 'path'},
                              connect_timeout=10, read_timeout=30,
                              retries={'max_attempts': 2, 'mode': 'standard'}))

    def put(self, action_id, image_id, raw, content_type):
        if self.client is None:
            return base64.b64encode(raw).decode('ascii')
        # Hash client-supplied IDs so they cannot control bucket paths.
        identity = hashlib.sha256((action_id + '\0' + image_id).encode()).hexdigest()
        key = 'scans/' + identity + '/' + hashlib.sha256(raw).hexdigest()
        try:
            self.client.put_object(Bucket=self.bucket, Key=key, Body=raw,
                                   ContentType=content_type, CacheControl='private, no-store')
        except Exception as exc:
            raise Error('Image storage unavailable. Retry this image; stock is unchanged.', 503) from exc
        return PREFIX + self.bucket + '/' + key

    def get(self, value):
        if not value.startswith(PREFIX):
            return value
        if self.client is None:
            raise Error('Image storage is not configured', 503)
        bucket, key = value[len(PREFIX):].split('/', 1)
        try:
            response = self.client.get_object(Bucket=bucket, Key=key)
            with response['Body'] as stream:
                return base64.b64encode(stream.read()).decode('ascii')
        except Exception as exc:
            raise Error('Image storage unavailable. Retry loading the image.', 503) from exc

    def delete(self, value):
        if not value.startswith(PREFIX):
            return
        if self.client is None:
            raise Error('Image storage is not configured', 503)
        bucket, key = value[len(PREFIX):].split('/', 1)
        try:
            self.client.delete_object(Bucket=bucket, Key=key)
        except Exception as exc:
            raise Error('Image hidden; storage cleanup failed. Retry deletion.', 503) from exc
