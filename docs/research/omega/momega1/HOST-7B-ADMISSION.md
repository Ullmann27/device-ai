# Separate approved 7B host comparison

The owner explicitly replied **“7B-Hostversuch freigeben”** after reviewing the
1.5B baseline and the additional download/resource request. This authorizes the
following exact model on the existing host, with at most 8 GiB for the model job,
no cloud inference and no new paid resources. It is not Android/Gemma/Fold7 or
release acceptance. The earlier model and failed benchmark remain preserved.

| Item | Pin |
|---|---|
| Repository | Qwen/Qwen2.5-Coder-7B-Instruct-GGUF |
| Revision | 13fb94bfda8c8cf22497dc57b78f391a9acb426a |
| File | qwen2.5-coder-7b-instruct-q4_k_m.gguf |
| Bytes | 4683073536 |
| SHA-256 | 509287f78cb4d4cf6b3843734733b914b2c158e43e22a7f4bf5e963800894d3c |
| License | Apache-2.0 |
| Existing native runtime source | ggml-org/llama.cpp 6cb3e672e8d10fd4feab1a048bf2967c40623133 |
| Existing native executable SHA-256 | c2c13a2a27c5a85137b6d083074fa83e0d990db129e715f8452352e3730cbf5a |

Official metadata was read before consent. Actual download/format/hash,
resource-specific confinement and native inference are pending. The 1.5B
runtime's current 2 GiB file and 4 GiB address-space limits cannot admit this
larger model. They must be extended narrowly for this reviewed exact pair and
independently tested; no generic environment override or fallback is authorized.

Candidate registration must remain unadmitted until actual independent runtime
observation. Every task still binds one model/runtime identity throughout, and
the same frozen task source, acceptance criteria and test identities remain
authoritative. Model quality and a successful correction remain measured outcomes,
never consequences of choosing a larger file.
