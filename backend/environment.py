"""Load Neon local settings without overriding hosting-provided variables."""
from pathlib import Path
from dotenv import load_dotenv


def load_environment():
    root = Path(__file__).resolve().parent.parent
    load_dotenv(root / '.env.local', override=False)
    load_dotenv(root / '.env', override=False)
