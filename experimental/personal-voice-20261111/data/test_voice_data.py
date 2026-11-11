import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("voice_data", Path(__file__).with_name("voice_data.py"))
voice = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(voice)


class VoiceDataTests(unittest.TestCase):
    def setUp(self):
        self.records = voice.contrasts()
        self.replay = voice.supervised_replay()
        self.pipeline = voice.pipeline_examples()

    def personal(self):
        record = copy.deepcopy(self.records[0])
        record["origin"] = {"kind": "owner_corrected", "source_ref": "local-session:example", "rights": "owner_granted_local_training", "uses_private_content": True}
        record["storage"] = {"classification": "private_local", "encryption_required": True}
        record["consent"]["text_training"] = True
        record["review"] = {"status": "owner_confirmed", "reviewer_kind": "owner"}
        record["consent"]["scope_digest"] = voice.protected_content_digest(record)
        return record

    def test_registered_balanced_contrasts_and_bow_bound(self):
        report = voice.audit(self.records, self.replay, self.pipeline)
        self.assertEqual(report["metrics"]["train"]["labels"], {"0": 32, "1": 32, "2": 32, "3": 32})
        self.assertEqual(report["metrics"]["dev"]["labels"], {"0": 8, "1": 8, "2": 8, "3": 8})
        self.assertLessEqual(report["metrics"]["train"]["bow_oracle_accuracy_ceiling"], .5)
        self.assertEqual(report["real_audio_records"], 0)

    def test_single_label_marker_is_detected(self):
        self.records[0]["lm1"]["prompt"] += " nurpositiv"
        self.records[0]["recognized_transcript"] = self.records[0]["lm1"]["prompt"]
        with self.assertRaisesRegex(ValueError, "BOW_PAIR_MISMATCH"):
            voice.audit(self.records, self.replay, self.pipeline)

    def test_pair_split_leak_is_detected(self):
        self.records[0]["split"] = "dev"
        with self.assertRaisesRegex(ValueError, "PAIR_SPLIT_LEAK"):
            voice.audit(self.records, self.replay, self.pipeline)

    def test_family_split_leak_is_detected(self):
        family = self.records[0]["family_id"]
        for record in self.records:
            if record["split"] == "dev":
                record["family_id"] = family
        with self.assertRaisesRegex(ValueError, "FAMILY_SPLIT_LEAK"):
            voice.audit(self.records, self.replay, self.pipeline)

    def test_private_record_cannot_be_published_even_with_cloud_flag(self):
        record = self.personal()
        record["consent"]["cloud_export"] = True
        voice.validate_record(record)
        with self.assertRaisesRegex(ValueError, "PRIVATE_PUBLICATION_DENIED"):
            voice.validate_record(record, public_export=True)

    def test_text_training_needs_separate_opt_in(self):
        record = self.personal()
        record["consent"]["text_training"] = False
        with self.assertRaisesRegex(ValueError, "OWNER_TRAINING_DENIED"):
            voice.validate_record(record)

    def test_changed_context_invalidates_old_consent(self):
        record = self.personal()
        record["context"]["turns"] = [{"turn_id": "t1", "role": "owner", "text": "Neue private Aussage."}]
        with self.assertRaisesRegex(ValueError, "STALE_CONTENT_CONSENT"):
            voice.validate_record(record)

    def test_changed_meaning_invalidates_old_consent(self):
        record = self.personal()
        record["meaning"]["intended_instruction"] = "Geänderter Auftrag"
        with self.assertRaisesRegex(ValueError, "STALE_CONTENT_CONSENT"):
            voice.validate_record(record)

    def test_text_consent_does_not_authorize_audio(self):
        record = self.personal()
        record["audio"] = {"local_ref": "private-audio:sample", "sha256": "a" * 64, "third_party_voice": False}
        record["consent"]["scope_digest"] = voice.protected_content_digest(record)
        with self.assertRaisesRegex(ValueError, "AUDIO_RETENTION_DENIED"):
            voice.validate_record(record)

    def test_third_party_audio_needs_own_permission(self):
        record = self.personal()
        record["consent"]["audio_retention"] = True
        record["audio"] = {"local_ref": "private-audio:sample", "sha256": "a" * 64, "third_party_voice": True}
        record["consent"]["scope_digest"] = voice.protected_content_digest(record)
        with self.assertRaisesRegex(ValueError, "THIRD_PARTY_AUDIO_DENIED"):
            voice.validate_record(record)

    def test_unconfirmed_asr_edit_cannot_be_truth(self):
        record = copy.deepcopy(self.records[0])
        record["corrected_transcript"] = "Ich wurde anders verstanden."
        with self.assertRaisesRegex(ValueError, "UNCONFIRMED_WORDING"):
            voice.validate_record(record)

    def test_confirmed_wording_does_not_confirm_meaning(self):
        record = copy.deepcopy(self.records[0])
        record["corrected_transcript"] = record["recognized_transcript"]
        record["transcript_correction_confirmed"] = True
        record["meaning"]["confirmed"] = False
        with self.assertRaisesRegex(ValueError, "UNREVIEWED_TRAINING"):
            voice.validate_record(record)

    def test_unreviewed_sensitive_content_is_not_trainable(self):
        record = self.personal()
        record["sensitivity"] = {"reviewed": False, "approved": False, "categories": ["health"]}
        with self.assertRaisesRegex(ValueError, "UNREVIEWED_TRAINING"):
            voice.validate_record(record)

    def test_multi_action_and_ambiguous_execution_rejected(self):
        record = copy.deepcopy(self.records[0])
        record["meaning"]["actions"] = [voice.action("1", "Uhr"), voice.action("2", "Plan")]
        with self.assertRaisesRegex(ValueError, "MULTI_OR_AMBIGUOUS_LM1"):
            voice.validate_record(record)
        record["meaning"]["needs_clarification"] = True
        with self.assertRaisesRegex(ValueError, "AMBIGUOUS_EXECUTION"):
            voice.validate_record(record)

    def test_replay_retention_leak_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            retention = root / "retention.tsv"
            voice.write_tsv(retention, [self.replay[0]])
            with self.assertRaisesRegex(ValueError, "HISTORICAL_RETENTION_LEAK"):
                voice.generate(root / "output", retention)
            self.assertFalse((root / "output").exists())

    def test_generation_reproducible_and_existing_destination_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            voice.generate(root / "one")
            voice.generate(root / "two")
            first = {p.name: p.read_bytes() for p in (root / "one").iterdir()}
            second = {p.name: p.read_bytes() for p in (root / "two").iterdir()}
            self.assertEqual(first, second)
            with self.assertRaisesRegex(ValueError, "DESTINATION_ALREADY_EXISTS"):
                voice.generate(root / "one")
            self.assertEqual(first, {p.name: p.read_bytes() for p in (root / "one").iterdir()})

    def test_pipeline_diagnostics_cannot_enter_lm1_tsv_as_single_labels(self):
        self.assertTrue(all(not r["lm1"]["eligible"] for r in self.pipeline))
        self.assertEqual({r["meaning"]["speech_act"] for r in self.pipeline}, {"question", "statement", "request", "correction"})
        self.assertTrue(any(r["corrected_transcript"] for r in self.pipeline))
        self.assertTrue(any(r["meaning"]["needs_clarification"] for r in self.pipeline))


if __name__ == "__main__":
    unittest.main()
