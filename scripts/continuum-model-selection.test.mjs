import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const config = JSON.parse(readFileSync(new URL("../config/continuum-selected-models.json", import.meta.url), "utf8"));

test("the recorded checkpoint is the exact live-pinned DeepSeek snapshot", () => {
  assert.equal(config.schema, "CONTINUUM_MODEL_SELECTION_V1");
  assert.equal(config.roles.research_teacher.model_id, "deepseek-ai/DeepSeek-V4.1-Flash-1017");
  assert.equal(config.roles.research_teacher.pinned_hub_revision,
    "1bae0e1088993b9b82de08f59de5be5a34e42170");
  assert.equal(config.roles.research_teacher.declared_weight_shards, 48);
  assert.equal(config.roles.research_teacher.declared_weight_bytes, 510296708312);
  assert.equal(config.roles.research_teacher.hub_metadata_state, "LIVE_PINNED_METADATA_READ_ONLY");
});

test("metadata inspection never implies installed weights, available compute or owner approval", () => {
  const teacher = config.roles.research_teacher;
  assert.equal(teacher.download_state, "NOT_DOWNLOADED");
  assert.equal(teacher.runtime_state, "NOT_DEPLOYED");
  assert.equal(teacher.weight_sha256, null);
  assert.equal(teacher.android_import_compatible, false);
  assert.equal(config.constraints.no_unauthorized_external_compute, true);
  assert.equal(config.constraints.production_branch_untouched, true);
  assert.equal(config.roles.on_device.device_load_test, "NOT_RUN");
  assert.equal(config.roles.retired_candidate.deletion_state, "NOT_PERFORMED");
  assert.equal(config.constraints.existing_app_package, "at.heinz.deviceai.mentorpaired");
});
