"""Regression checks for removing storage locations from existing databases."""
import json
from contextlib import closing
import sqlite3
import tempfile
import unittest
from pathlib import Path

from migrate_remove_locations import migrate, has_locations
from service import Service, Error

LEGACY_SCHEMA = "PRAGMA foreign_keys=ON;\nCREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY, username TEXT UNIQUE NOT NULL, password TEXT NOT NULL, role TEXT NOT NULL CHECK(role IN ('admin','operator','viewer')));\nCREATE TABLE IF NOT EXISTS sessions(token TEXT PRIMARY KEY, user_id INTEGER REFERENCES users(id), expires REAL NOT NULL);\nCREATE TABLE IF NOT EXISTS items(id INTEGER PRIMARY KEY, sku TEXT UNIQUE NOT NULL, name TEXT NOT NULL, model_class TEXT UNIQUE);\nCREATE TABLE IF NOT EXISTS locations(id INTEGER PRIMARY KEY, name TEXT UNIQUE NOT NULL);\nCREATE TABLE IF NOT EXISTS stock(item_id INTEGER REFERENCES items(id), location_id INTEGER REFERENCES locations(id), quantity INTEGER NOT NULL CHECK(quantity>=0), version INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(item_id,location_id));\nCREATE TABLE IF NOT EXISTS scans(id TEXT PRIMARY KEY, user_id INTEGER REFERENCES users(id), location_id INTEGER REFERENCES locations(id), mode TEXT NOT NULL, detections TEXT NOT NULL, snapshot TEXT NOT NULL, created_at TEXT NOT NULL);\nCREATE TABLE IF NOT EXISTS adjustments(id INTEGER PRIMARY KEY, request_key TEXT UNIQUE NOT NULL, payload_hash TEXT NOT NULL, scan_id TEXT REFERENCES scans(id), item_id INTEGER REFERENCES items(id), location_id INTEGER REFERENCES locations(id), previous_quantity INTEGER NOT NULL, verified_quantity INTEGER NOT NULL, difference INTEGER NOT NULL, reason TEXT NOT NULL, user_id INTEGER REFERENCES users(id), created_at TEXT NOT NULL, UNIQUE(scan_id,item_id,location_id));\nCREATE TABLE IF NOT EXISTS returns(id INTEGER PRIMARY KEY, request_key TEXT UNIQUE NOT NULL, payload_hash TEXT NOT NULL, item_id INTEGER REFERENCES items(id), location_id INTEGER REFERENCES locations(id), quantity INTEGER NOT NULL CHECK(quantity>0), reason TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'pending inspection', user_id INTEGER REFERENCES users(id), created_at TEXT NOT NULL);\nCREATE TABLE IF NOT EXISTS return_events(id INTEGER PRIMARY KEY, return_id INTEGER REFERENCES returns(id), previous_status TEXT, new_status TEXT NOT NULL, user_id INTEGER REFERENCES users(id), created_at TEXT NOT NULL);\nCREATE TABLE IF NOT EXISTS movements(id INTEGER PRIMARY KEY, item_id INTEGER REFERENCES items(id), location_id INTEGER REFERENCES locations(id), difference INTEGER NOT NULL, resulting_quantity INTEGER NOT NULL, source_type TEXT NOT NULL, source_id INTEGER NOT NULL, user_id INTEGER REFERENCES users(id), created_at TEXT NOT NULL, UNIQUE(source_type,source_id));\nCREATE TABLE IF NOT EXISTS scan_commits(request_key TEXT PRIMARY KEY, payload_hash TEXT NOT NULL, scan_id TEXT UNIQUE REFERENCES scans(id), result TEXT NOT NULL);\nCREATE TABLE IF NOT EXISTS session_activity(token TEXT PRIMARY KEY REFERENCES sessions(token) ON DELETE CASCADE, last_seen REAL NOT NULL);\nCREATE TABLE IF NOT EXISTS auth_attempts(bucket TEXT NOT NULL, attempted_at REAL NOT NULL);\nCREATE INDEX IF NOT EXISTS auth_attempts_lookup ON auth_attempts(bucket,attempted_at);\n"


def seed_legacy(path, quantities=(10, 7)):
    with closing(sqlite3.connect(path)) as c, c:
        c.executescript(LEGACY_SCHEMA)
        c.execute("INSERT INTO users VALUES(1,'owner','fixture-password-hash','admin')")
        c.execute("INSERT INTO items VALUES(1,'SIN','Sinandomeng','sinandomeng')")
        c.execute("INSERT INTO items VALUES(2,'CUSTOM','Unmatched product','custom')")
        c.execute("INSERT INTO locations VALUES(1,'Shelf A')")
        c.execute("INSERT INTO locations VALUES(2,'Shelf B')")
        c.execute('INSERT INTO stock VALUES(1,1,?,2)', (quantities[0],))
        c.execute('INSERT INTO stock VALUES(1,2,?,4)', (quantities[1],))
        c.execute('INSERT INTO stock VALUES(2,1,3,0)')
        snapshot = json.dumps([{'item_id': 1, 'location_id': 2, 'quantity': 7, 'version': 4}])
        c.execute("INSERT INTO scans VALUES('scan-1',1,2,'demo','[]',?,'2026-01-01')", (snapshot,))
        c.execute("INSERT INTO adjustments VALUES(1,'adjust-key','digest','scan-1',1,2,8,7,-1,'Verified',1,'2026-01-01')")
        c.execute("INSERT INTO movements VALUES(1,1,2,-1,7,'adjustment',1,1,'2026-01-01')")
        c.execute("INSERT INTO returns VALUES(1,'return-key','digest',1,2,2,'Customer return','pending inspection',1,'2026-01-01')")
        c.execute("INSERT INTO return_events VALUES(1,1,NULL,'pending inspection',1,'2026-01-01')")
        receipt = json.dumps({'changes': [{'item_id': 1, 'location_id': 2, 'difference': -1}]})
        c.execute("INSERT INTO scan_commits VALUES('receipt-key','digest','scan-1',?)", (receipt,))


class RemoveLocationsTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.db = Path(self.temp.name) / 'legacy.db'
        self.backup = Path(self.temp.name) / 'before.db'
        seed_legacy(self.db)

    def test_merge_preserves_history_foreign_keys_and_backup(self):
        result = migrate(self.db, self.backup)
        self.assertEqual((result['stock_rows_before'], result['stock_rows_after']), (3, 2))
        service = Service(self.db)
        with service.connection() as c:
            self.assertFalse(has_locations(c))
            self.assertEqual(dict(c.execute('SELECT * FROM stock WHERE item_id=1').fetchone()),
                             {'item_id': 1, 'quantity': 17, 'version': 5})
            for table in ('stock','scans','adjustments','returns','movements'):
                self.assertNotIn('location_id', [r['name'] for r in c.execute('PRAGMA table_info(' + table + ')')])
            self.assertEqual(list(c.execute('PRAGMA foreign_key_check')), [])
            self.assertEqual(c.execute('SELECT count(*) FROM return_events').fetchone()[0], 1)
            self.assertEqual(c.execute('SELECT password FROM users').fetchone()[0], 'fixture-password-hash')
            self.assertEqual(c.execute('SELECT quantity FROM rice_products WHERE id=1').fetchone()[0], 17)
            self.assertNotIn('location_id', c.execute('SELECT snapshot FROM scans').fetchone()[0])
            self.assertNotIn('location_id', c.execute('SELECT result FROM scan_commits').fetchone()[0])
        self.assertNotIn('locations', service.inventory())
        for kind in ('inventory','scans','adjustments','returns','movements','return_events'):
            self.assertNotIn('location_id', json.dumps(service.report(kind)))
            self.assertNotIn('"location"', json.dumps(service.report(kind)))
        with closing(sqlite3.connect(self.backup)) as c, c:
            self.assertTrue(has_locations(c))
            self.assertEqual(c.execute('SELECT count(*) FROM stock').fetchone()[0], 3)
            self.assertEqual(c.execute('SELECT count(*) FROM locations').fetchone()[0], 2)
        # History quantities retain their original audit values.
        self.assertEqual(service.report('movements')[0]['resulting_quantity'], 7)

    def test_existing_rice_balance_and_evidence_are_not_reseeded(self):
        with closing(sqlite3.connect(self.db)) as c, c:
            c.executescript(Path(__file__).with_name('rice_schema.sql').read_text())
            c.execute("INSERT INTO rice_products VALUES(1,'SIN','Sinandomeng','sinandomeng','25 kg',23,8)")
            c.execute("INSERT INTO rice_actions(id,user_id,kind,snapshot,created_at) VALUES('rice-action',1,'count','[]','2026-01-01')")
            c.execute("INSERT INTO rice_images VALUES('image-1','rice-action',0,1,'photo-evidence','hash','[]','[]','10','demo')")
            c.execute("INSERT INTO rice_requests VALUES('rice-request','hash',?)", (json.dumps({'saved': True}),))
        migrate(self.db, self.backup)
        service = Service(self.db)
        with service.connection() as c:
            self.assertEqual(tuple(c.execute('SELECT quantity,version FROM rice_products WHERE id=1').fetchone()), (23, 8))
            self.assertEqual(c.execute('SELECT image FROM rice_images').fetchone()[0], 'photo-evidence')
            self.assertEqual(json.loads(c.execute('SELECT result FROM rice_requests').fetchone()[0]), {'saved': True})

    def test_overflow_rolls_back_without_losing_original_data(self):
        with closing(sqlite3.connect(self.db)) as c, c:
            c.execute('UPDATE stock SET quantity=2147483647 WHERE item_id=1 AND location_id=1')
        with self.assertRaises(ValueError):
            migrate(self.db, self.backup)
        with closing(sqlite3.connect(self.db)) as c, c:
            self.assertTrue(has_locations(c))
            self.assertEqual(c.execute('SELECT count(*) FROM stock').fetchone()[0], 3)
        self.assertTrue(self.backup.is_file())

    def test_unexpected_foreign_key_dependency_rolls_back(self):
        with closing(sqlite3.connect(self.db)) as c, c:
            c.execute('CREATE TABLE custom_location_reference(id INTEGER REFERENCES locations(id))')
            c.execute('INSERT INTO custom_location_reference VALUES(1)')
        with self.assertRaises(ValueError):
            migrate(self.db, self.backup)
        with closing(sqlite3.connect(self.db)) as c, c:
            self.assertTrue(has_locations(c))
            self.assertEqual(c.execute('SELECT count(*) FROM stock').fetchone()[0], 3)

    def test_custom_index_is_not_silently_discarded(self):
        with closing(sqlite3.connect(self.db)) as c, c:
            c.execute('CREATE INDEX custom_stock_lookup ON stock(quantity)')
        with self.assertRaises(ValueError):
            migrate(self.db, self.backup)
        with closing(sqlite3.connect(self.db)) as c, c:
            self.assertTrue(has_locations(c))
            self.assertTrue(c.execute("SELECT 1 FROM sqlite_master WHERE name='custom_stock_lookup'").fetchone())

    def test_startup_requires_migration_and_rerun_is_safe(self):
        with self.assertRaisesRegex(RuntimeError, 'migrate_remove_locations'):
            Service(self.db)
        migrate(self.db, self.backup)
        other = Path(self.temp.name) / 'second-backup.db'
        self.assertFalse(migrate(self.db, other)['changed'])
        with closing(sqlite3.connect(self.db)) as c, c:
            self.assertEqual(c.execute('SELECT quantity,version FROM stock WHERE item_id=1').fetchone(), (17, 5))
        with self.assertRaises(ValueError):
            migrate(self.db, self.backup)

    def test_old_partial_scan_cannot_replace_combined_stock(self):
        # Remove the already committed adjustment so the stale-snapshot check is reached.
        with closing(sqlite3.connect(self.db)) as c, c:
            c.execute('DELETE FROM adjustments')
            c.execute('DELETE FROM scan_commits')
            c.execute('DELETE FROM movements')
        migrate(self.db, self.backup)
        service = Service(self.db)
        with self.assertRaisesRegex(Error, 'Stock changed'):
            service.adjust({'id': 1, 'role': 'admin'}, {'scan_id': 'scan-1', 'item_id': 1,
                'verified_quantity': 7, 'reason': 'Old partial scan', 'confirmed': True,
                'complete_count': True, 'request_key': 'old-scan'})

    def test_missing_source_does_not_create_a_database(self):
        missing = Path(self.temp.name) / 'missing.db'
        with self.assertRaises(ValueError):
            migrate(missing, self.backup)
        self.assertFalse(missing.exists())


if __name__ == '__main__':
    unittest.main()
