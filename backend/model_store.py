"""Load trusted model files from a container path or verified HTTPS storage."""
import hashlib
import os
import re
import tempfile
from pathlib import Path
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


def configured_mode():
    default = 'yolo' if Path(__file__).resolve().parent.parent.joinpath('models/best.pt').is_file() else 'demo'
    return os.environ.get('MOVIS_MODE', default)


def prepare_weights():
    path=os.environ.get('MOVIS_WEIGHTS')
    url=os.environ.get('MOVIS_WEIGHTS_URL')
    expected=os.environ.get('MOVIS_WEIGHTS_SHA256','').lower()
    if configured_mode()!='yolo':return path
    if not path and not url:
        bundled=Path(__file__).resolve().parent.parent/'models'/'best.pt'
        if bundled.is_file():
            path=str(bundled)
            manifest=bundled.with_name('model.json')
            if not expected and manifest.is_file():
                import json
                expected=json.loads(manifest.read_text())['sha256']
    if path and Path(path).is_file():
        if expected and hashlib.sha256(Path(path).read_bytes()).hexdigest()!=expected:
            raise RuntimeError('Model checksum does not match the configured SHA256')
        return path
    if not url:return path
    if urlsplit(url).scheme!='https' or not re.fullmatch(r'[0-9a-f]{64}',expected):
        raise RuntimeError('Model download requires HTTPS and MOVIS_WEIGHTS_SHA256')
    directory=Path(tempfile.gettempdir())/'movis-models';directory.mkdir(exist_ok=True)
    target=directory/'best.pt';part=directory/'best.pt.part';digest=hashlib.sha256();size=0
    try:
        with urlopen(Request(url,headers={'User-Agent':'MOVIS-model-loader'}),timeout=60) as response,part.open('wb') as out:
            if urlsplit(response.geturl()).scheme!='https':raise RuntimeError('Model download redirected away from HTTPS')
            while block:=response.read(1024*1024):
                size+=len(block)
                if size>250*1024*1024:raise RuntimeError('Model exceeds the 250 MiB download limit')
                digest.update(block);out.write(block)
        if not size or digest.hexdigest()!=expected:raise RuntimeError('Downloaded model checksum mismatch')
        part.replace(target)
        return str(target)
    finally:
        if part.exists():part.unlink()
