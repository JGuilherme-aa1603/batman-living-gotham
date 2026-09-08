import unittest

from cityindex.coords import GridTransform, TileGrid, chunk_region_local, chunk_to_region, spatial_chunk_bounds, world_to_chunk, world_to_region


class CoordinateTests(unittest.TestCase):
    def test_world_chunk_flooring_including_negative(self):
        self.assertEqual([world_to_chunk(v) for v in (-17,-16,-1,0,15,16)], [-2,-1,-1,0,0,1])

    def test_region_conversions(self):
        self.assertEqual(chunk_to_region(-1), -1)
        self.assertEqual(chunk_region_local(-1), 31)
        self.assertEqual(world_to_region(-513), -2)

    def test_pixel_round_trip_bounds(self):
        transform=GridTransform(-3584,-3584,4)
        self.assertEqual(transform.world_to_pixel(-3581,-3577),(0,1))
        self.assertEqual(transform.pixel_bounds(0,1),(-3584,-3580,-3581,-3577))

    def test_tile_bounds_and_negative_origin(self):
        tiles=TileGrid(-3584,-3584,512)
        self.assertEqual(tiles.world_to_tile(-3072,-3584),(1,0))
        self.assertEqual(tiles.tile_bounds(1,0),(-3072,-3584,-2561,-3073))

    def test_spatial_bounds(self):
        self.assertEqual(spatial_chunk_bounds(-1,-17,16,31),(-1,-2,1,1))


if __name__ == '__main__': unittest.main()
