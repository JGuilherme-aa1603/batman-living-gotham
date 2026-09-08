import json
from pathlib import Path
import sqlite3
import tempfile
import unittest

from cityindex.anvil import decode_packed, region_coords
from cityindex.cache import RegionSignature, reusable, signature
from cityindex.storage import connect, require_complete, start_scan, transition_scan


class StorageCacheAnvilTests(unittest.TestCase):
    def test_packed_values_cross_long_boundary(self):
        values=list(range(20)); bits=5; longs=[0,0]
        for index,value in enumerate(values):
            start=index*bits; li,shift=divmod(start,64); longs[li]|=value<<shift
            if shift+bits>64: longs[li+1]|=value>>(64-shift)
        self.assertEqual(decode_packed(longs,len(values),bits),values)

    def test_padded_packing(self):
        values=list(range(12))*2; bits=5; longs=[]
        for offset in range(0,len(values),12):
            packed=0
            for index,value in enumerate(values[offset:offset+12]): packed|=value<<(index*bits)
            longs.append(packed)
        self.assertEqual(decode_packed(longs,len(values),bits,padded=True),values)

    def test_region_filename(self):
        self.assertEqual(region_coords(Path('r.-7.10.mca')),(-7,10))

    def test_schema_and_json_serialization(self):
        with tempfile.TemporaryDirectory() as directory:
            db=Path(directory)/'index.sqlite'; connection=connect(db)
            names={row[0] for row in connection.execute("select name from sqlite_master where type='table'")}
            self.assertTrue({'world','chunks','features','candidates','cache_regions','scan_state'} <= names)
            payload=json.dumps({'bounds':[1,2,3,4]},sort_keys=True)
            self.assertEqual(json.loads(payload)['bounds'][3],4)
            connection.close()

    def test_incomplete_scan_is_not_queryable(self):
        with tempfile.TemporaryDirectory() as directory:
            connection=connect(Path(directory)/'index.sqlite')
            start_scan(connection,10)
            with self.assertRaisesRegex(RuntimeError,'RUNNING_PASS1'):
                require_complete(connection)
            transition_scan(connection,'COMPLETE',recorded_chunks=10)
            connection.commit()
            self.assertEqual(require_complete(connection)['recorded_chunks'],10)
            connection.close()

    def test_cache_invalidation(self):
        a=RegionSignature(1,2,'abc'); b=RegionSignature(1,2,'abc'); changed=RegionSignature(1,3,'def')
        self.assertTrue(reusable(a,b)); self.assertFalse(reusable(a,changed)); self.assertFalse(reusable(None,b))

    def test_file_signature_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'fixture'; path.write_bytes(b'a'); before=signature(path)
            path.write_bytes(b'b'); after=signature(path)
            self.assertNotEqual(before.sha256,after.sha256)


if __name__ == '__main__': unittest.main()
