"""Remove legacy storage locations while preserving balances and history.

SQLite is backed up with its online backup API. PostgreSQL uses pg_dump before
the transactional change. Run with --db PATH, or set DATABASE_URL privately.
"""
import argparse
import json
import os
import shutil
import sqlite3
import subprocess
import uuid
from contextlib import closing
from pathlib import Path

import database

TABLES = ('stock', 'scans', 'adjustments', 'returns', 'movements')
MAX_QUANTITY = 2147483647


def has_locations(con, postgres=False):
    if postgres:
        return bool(con.execute("SELECT 1 FROM information_schema.tables WHERE table_schema=current_schema() AND table_name='locations'").fetchone())
    return bool(con.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='locations'").fetchone())


def without_location(value):
    if isinstance(value, dict):
        return {key: without_location(item) for key, item in value.items()
                if key not in ('location', 'location_id', 'locations')}
    if isinstance(value, list):
        return [without_location(item) for item in value]
    return value


def remove_location_tables(con, postgres=False):
    """Caller owns transaction and backup; SQLite caller disables foreign keys.

    No CASCADE is used: unexpected dependencies abort the migration.
    """
    if not has_locations(con, postgres):
        return {'changed': False}
    records = {table: [dict(row) for row in con.execute('SELECT * FROM ' + table)]
               for table in TABLES}
    totals = {}
    for row in records['stock']:
        merged = totals.setdefault(row['item_id'], {'item_id': row['item_id'], 'quantity': 0, 'version': 0})
        merged['quantity'] += row['quantity']
        merged['version'] = max(merged['version'], row['version'] + 1)
        if merged['quantity'] > MAX_QUANTITY or merged['version'] > MAX_QUANTITY:
            raise ValueError('Merged quantity or version exceeds the supported limit; migration rolled back')
    old_stock_count = len(records['stock'])
    records['stock'] = list(totals.values())
    for table, rows in records.items():
        for row in rows:
            row.pop('location_id', None)
            if table == 'scans':
                row['snapshot'] = json.dumps(without_location(json.loads(row['snapshot'])))
                row['detections'] = json.dumps(without_location(json.loads(row['detections'])))

    if postgres:
        # Drop the composite stock key, merge rows, and use item_id as the key.
        con.execute('DELETE FROM stock')
        for table in TABLES:
            con.execute('ALTER TABLE ' + table + ' DROP COLUMN location_id')
        con.execute('ALTER TABLE stock ADD PRIMARY KEY(item_id)')
        con.execute('ALTER TABLE adjustments ADD UNIQUE(scan_id,item_id)')
        con.execute('DROP TABLE locations')
        for row in records['stock']:
            con.execute('INSERT INTO stock(item_id,quantity,version) VALUES(?,?,?)', tuple(row.values()))
        for row in records['scans']:
            con.execute('UPDATE scans SET snapshot=?,detections=? WHERE id=?', (row['snapshot'], row['detections'], row['id']))
    else:
        if con.execute("SELECT name FROM sqlite_master WHERE type IN ('trigger','index') AND sql IS NOT NULL AND tbl_name IN ('stock','scans','adjustments','returns','movements','locations')").fetchone():
            raise ValueError('Custom indexes or triggers need manual review; migration rolled back')
        schema = Path(__file__).with_name('schema.sql').read_text()
        for table in TABLES:
            statement = next(line for line in schema.splitlines()
                             if line.startswith('CREATE TABLE IF NOT EXISTS ' + table + '('))
            temporary = '__movis_no_location_' + table
            if con.execute("SELECT 1 FROM sqlite_master WHERE name=?", (temporary,)).fetchone():
                raise ValueError('Migration temporary table already exists')
            statement = statement.replace('CREATE TABLE IF NOT EXISTS ' + table + '(', 'CREATE TABLE ' + temporary + '(', 1)
            con.execute(statement)
            for row in records[table]:
                columns = ','.join(row)
                marks = ','.join('?' for _ in row)
                con.execute('INSERT INTO ' + temporary + '(' + columns + ') VALUES(' + marks + ')', tuple(row.values()))
        for table in reversed(TABLES):
            con.execute('DROP TABLE ' + table)
        con.execute('DROP TABLE locations')
        for table in TABLES:
            con.execute('ALTER TABLE __movis_no_location_' + table + ' RENAME TO ' + table)
        if con.execute('PRAGMA foreign_key_check').fetchall():
            raise ValueError('Foreign-key check failed; migration rolled back')

    receipts = list(con.execute('SELECT request_key,result FROM scan_commits'))
    for row in receipts:
        con.execute('UPDATE scan_commits SET result=? WHERE request_key=?',
                    (json.dumps(without_location(json.loads(row['result']))), row['request_key']))
    return {'changed': True, 'stock_rows_before': old_stock_count,
            'stock_rows_after': len(totals),
            'history_rows': {table: len(records[table]) for table in TABLES if table != 'stock'}}


def migrate(target, backup=None):
    postgres = database.is_postgres(target)
    backup = Path(backup) if backup else Path.cwd() / ('movis-before-location-removal-' + uuid.uuid4().hex + ('.dump' if postgres else '.db'))
    if backup.exists():
        raise ValueError('Backup file already exists; choose another filename')
    if postgres:
        executable = shutil.which('pg_dump')
        if not executable:
            raise RuntimeError('Install PostgreSQL pg_dump so a full backup can be saved before migration')
        environment = dict(os.environ, PGDATABASE=str(target))
        result = subprocess.run([executable, '--format=custom', '--file=' + str(backup)],
                                env=environment, capture_output=True)
        if result.returncode:
            raise RuntimeError('PostgreSQL backup failed; database was not changed')
        with database.connection(target) as con:
            con.execute('BEGIN IMMEDIATE')
            # Block changes to legacy tables for the duration of this transaction.
            if has_locations(con, True):
                con.execute('LOCK TABLE locations,stock,scans,adjustments,returns,movements,scan_commits IN ACCESS EXCLUSIVE MODE')
            outcome = remove_location_tables(con, True)
    else:
        path = Path(target).resolve()
        if not path.is_file():
            raise ValueError('Database does not exist')
        if path == backup.resolve():
            raise ValueError('Backup must be a different file')
        con = sqlite3.connect(path)
        con.row_factory = sqlite3.Row
        try:
            con.execute('PRAGMA foreign_keys=OFF')
            con.execute('BEGIN IMMEDIATE')
            # The write lock prevents the source changing during the backup.
            with closing(sqlite3.connect(path.as_uri() + '?mode=ro', uri=True)) as source:
                with closing(sqlite3.connect(backup)) as destination:
                    source.backup(destination)
            outcome = remove_location_tables(con)
            con.commit()
        except Exception:
            con.rollback()
            raise
        finally:
            con.close()
    return {**outcome, 'backup': str(backup.resolve())}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--db', default=os.environ.get('DATABASE_URL'))
    parser.add_argument('--backup', help='New backup filename (SQLite .db or PostgreSQL .dump)')
    args = parser.parse_args()
    if not args.db:
        parser.error('Pass --db or set DATABASE_URL privately')
    try:
        print(json.dumps(migrate(args.db, args.backup), indent=2))
    except (ValueError, RuntimeError) as exc:
        print(str(exc))
        raise SystemExit(1) from None
    except Exception:
        # Connection exceptions may contain credentials; never print them.
        print('Location migration failed. The change was rolled back. Check database access, pg_dump, and backup permissions.')
        raise SystemExit(1) from None


if __name__ == '__main__':
    main()
