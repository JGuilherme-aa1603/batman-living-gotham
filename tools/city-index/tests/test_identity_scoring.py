import unittest

from cityindex.identity import geometry_hash, stable_id
from cityindex.scoring import weighted_score


class IdentityScoringTests(unittest.TestCase):
    def test_geometry_hash_is_order_independent(self):
        a=geometry_hash('building','minecraft:overworld',[(2,1),(1,1)],(0,0,3,3))
        b=geometry_hash('building','minecraft:overworld',[(1,1),(2,1)],(0,0,3,3))
        self.assertEqual(a,b)

    def test_ids_change_with_geometry(self):
        self.assertNotEqual(stable_id('building','minecraft:overworld',[(1,1)],(0,0,3,3)),
                            stable_id('building','minecraft:overworld',[(1,2)],(0,0,3,3)))

    def test_scoring_is_bounded_and_explained(self):
        score,reasons=weighted_score('wayne_manor',{'property_size':2,'isolation':1,'road_access':.5})
        self.assertGreaterEqual(score,0); self.assertLessEqual(score,1)
        self.assertEqual(len(reasons),6)

    def test_scoring_reproducible(self):
        features={'urbanity':.8,'building_size':.7,'road_access':1,'centrality':.9,'open_access':.2}
        self.assertEqual(weighted_score('gcpd',features),weighted_score('gcpd',features))


if __name__ == '__main__': unittest.main()
