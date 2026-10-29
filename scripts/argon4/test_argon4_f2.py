#!/usr/bin/env python3
"""Tests for the ARGON4 F2 prototypes: python3 -I -m unittest discover -s scripts/argon4 -p 'test_*.py'"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bandwidth_model as bm  # noqa: E402
import expert_cache_sim as sim  # noqa: E402
import stream_bench as sb  # noqa: E402


class BandwidthModelTest(unittest.TestCase):
    def test_io_bound_lower_bound_is_bytes_over_bandwidth(self):
        dev = bm.Device("toy", 1.0, 100.0, 2.0, 0.0, 1e6, 1.0, 1.0, "toy")
        model = bm.Model("toy dense", 10e9, 10e9, 0.0, 0.0, 0.0, "toy")
        r = bm.per_token(model, dev, 8, 0.0, context_tokens=0)
        # 10 GB active, 1 GB fits in RAM -> 9 GB from storage at 2 GB/s
        self.assertAlmostEqual(r["t_io_s"], 4.5, places=3)
        self.assertEqual(r["bound_by"], "io")

    def test_fewer_bits_and_more_hits_never_slow_a_token_down(self):
        for dev in (bm.FOLD7, bm.PC):
            last = None
            for bits in (16, 8, 6, 4, 3, 2):
                t = bm.per_token(bm.DSV3, dev, bits, 0.5)["t_lower_s"]
                if last is not None:
                    self.assertLessEqual(t, last + 1e-9)
                last = t
            ts = [bm.per_token(bm.DSV3, dev, 2, h)["t_lower_s"] for h in (0.0, 0.5, 0.8, 0.9)]
            self.assertEqual(ts, sorted(ts, reverse=True))

    def test_moe_moves_far_fewer_bytes_than_dense_of_similar_size(self):
        moe = bm.per_token(bm.DSV3, bm.FOLD7, 4, 0.0)["flash_gb_per_token"]
        dense = bm.per_token(bm.DENSE, bm.FOLD7, 4, 0.0)["flash_gb_per_token"]
        self.assertLess(moe * 10, dense)

    def test_invalid_inputs_are_rejected(self):
        with self.assertRaises(AssertionError):
            bm.per_token(bm.DSV3, bm.FOLD7, 4, 1.5)
        with self.assertRaises(AssertionError):
            bm.per_token(bm.DSV3, bm.FOLD7, 0, 0.5)

    def test_split_moe_keeps_weights_off_the_link(self):
        r = bm.split_moe(bm.DSV3, 2)
        self.assertLess(r["activation_mb_per_token"], 5.0)   # activations only, not gigabytes of weights
        self.assertTrue(r["phone_fits"])


class ExpertCacheSimTest(unittest.TestCase):
    def test_trace_is_deterministic_and_well_formed(self):
        a = sim.trace(tokens=200, seed=3)
        self.assertEqual(a, sim.trace(tokens=200, seed=3))
        self.assertTrue(all(len(s) == 8 and len(set(s)) == 8 for s in a))

    def test_belady_is_an_upper_bound_for_every_online_policy(self):
        tr = sim.trace(tokens=600, persistence=0.35, seed=5)
        for cap in (8, 16, 32, 64):
            best = sim.simulate(tr, cap, "BELADY")["hit_rate"]
            for policy in ("LRU", "LFU"):
                self.assertLessEqual(sim.simulate(tr, cap, policy)["hit_rate"], best + 1e-9)

    def test_prefetch_never_reduces_total_fetches_below_demand(self):
        tr = sim.trace(tokens=400, persistence=0.35, seed=9)
        p = sim.simulate(tr, 64, "PREFETCH")
        self.assertGreaterEqual(p["fetches_per_token"], p["stall_fetches_per_token"])

    def test_capacity_must_be_positive(self):
        with self.assertRaises(AssertionError):
            sim.simulate(sim.trace(tokens=10), 0, "LRU")


class StreamBenchTest(unittest.TestCase):
    def test_small_run_reports_every_pattern(self):
        r = sb.bench(size_mib=64)
        for key in ("seq_8m_gbps", "expert_22m_gbps", "rand_4k_gbps", "mmap_touch_gbps"):
            self.assertIn(key, r)
            self.assertGreater(r[key], 0)


if __name__ == "__main__":
    unittest.main()
