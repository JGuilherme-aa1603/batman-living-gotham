import unittest
import numpy as np

from cityindex.extract import CATEGORY_CODE, ChunkMetrics, _aggregate_cells
from cityindex.anvil import ChunkLocation
from pathlib import Path


class SegmentationHelperTests(unittest.TestCase):
    def test_cell_aggregation(self):
        categories=np.full((16,16),CATEGORY_CODE['terrain'],dtype=np.uint8)
        categories[:4,:4]=CATEGORY_CODE['building_candidate']
        metric=ChunkMetrics(ChunkLocation(0,0,0,0,0,2,1,0,Path('r.0.0.mca')),3465,'full',
                            np.full((16,16),80,dtype=np.int16),categories,['minecraft:stone']*256,0,False,False)
        mean,var,dominant,artificial,water,road,roof=_aggregate_cells(metric)
        self.assertEqual(mean.shape,(4,4)); self.assertEqual(float(artificial[0,0]),1.0)
        self.assertEqual(float(roof[0,0]),1.0); self.assertEqual(float(var[0,0]),0.0)


if __name__ == '__main__': unittest.main()
