"""Verify that the diagnostic cannot execute an unreviewed runner."""
import hashlib
from pathlib import Path
import tempfile
import unittest

from weight_phase_resume_probe import probe


class ReviewedSourceBoundaryTest(unittest.TestCase):
    def test_rejects_changed_runner_before_module_execution(self):
        with tempfile.TemporaryDirectory(prefix="continuum-probe-boundary-") as temporary:
            root = Path(temporary)
            marker = root / "module-executed.txt"
            runner = root / "changed_runner.py"
            source = ("from pathlib import Path\n"
                      f"Path({str(marker)!r}).write_text('executed', encoding='utf-8')\n")
            runner.write_text(source, encoding="utf-8")
            digest = hashlib.sha256(runner.read_bytes()).hexdigest()

            with self.assertRaisesRegex(ValueError, f"Unreviewed runner SHA-256: {digest}"):
                probe(runner)

            self.assertFalse(marker.exists(), "Unreviewed module code must never execute")


if __name__ == "__main__":
    unittest.main()
