import unittest
import numpy as np

from cityindex.geometry import component_bounds, connected_components, connected_components_by_value, graph_nodes
from cityindex.pass2 import ring_values, shifted


class GeometryTests(unittest.TestCase):
    def test_connected_components_and_minimum(self):
        mask=np.array([[1,1,0,0],[0,1,0,1],[0,0,0,1]],dtype=bool)
        components=connected_components(mask,minimum_cells=2)
        self.assertEqual(sorted(map(len,components)),[2,3])
        self.assertEqual(component_bounds(components[0]),(0,0,1,1))

    def test_four_connected_diagonal_is_separate(self):
        mask=np.eye(3,dtype=bool)
        self.assertEqual(len(connected_components(mask)),3)

    def test_height_transition_splits_touching_buildings(self):
        mask=np.ones((2,4),dtype=bool)
        heights=np.array([[80,80,95,95],[80,80,95,95]],dtype=float)
        components=connected_components_by_value(mask,heights,3,minimum_cells=2)
        self.assertEqual(sorted(map(len,components)),[4,4])

    def test_graph_nodes(self):
        mask=np.array([[0,1,0],[1,1,1],[0,1,0]],dtype=bool)
        nodes=graph_nodes(mask)
        self.assertIn((1,1,4),nodes)

    def test_shift_has_no_wrap(self):
        mask=np.zeros((3,3),dtype=bool); mask[0,0]=True
        moved=shifted(mask,1,1)
        self.assertTrue(moved[1,1]); self.assertFalse(moved[2,2])

    def test_ring_excludes_component(self):
        array=np.arange(25,dtype=float).reshape(5,5)
        values=ring_values(array,[(2,2)],1)
        self.assertEqual(len(values),8)
        self.assertNotIn(12,values)


if __name__ == '__main__': unittest.main()
