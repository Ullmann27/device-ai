# Continuum self-hosted coding – optional add-on

Stand: 28.10.2026, Research. This is an extension of the optional coding-bridge Draft PR #210, not of the installed Android app. Existing CCC1, local health worker, Termux teamleader, private Git source, model weights and release/signing paths are untouched.

## Built, not merely planned

- `scripts/continuum-coding-bridge.mjs` distinguishes a real local model identity `local/<id>` from the strictly independent remote `:free` allowlist. Neither path can pretend the other provider served the model.
- `scripts/continuum-selfhosted-coding.mjs` uses a directly specified `http://127.0.0.1:<port>/v1/chat/completions` and no third-party API, payment, model installer, account or credential. Exact loopback IP and endpoint, no DNS aliases, URL credentials, redirects, alternate URLs or external host names. The local server must first report the exact requested model ID on `GET /v1/models`.
- Existing Bridge has two strictly known **public** files, a preapproved `read_file` and `run_test` protocol, no arbitrary shell or additional files. It applies only one prereviewed one-line candidate to a fresh, discarded synthetic copy; the real input source is never edited.
- Requests and responses bounded by size and timeout. Explicit owner public-fixture gate `CONTINUUM_SELFHOSTED_TRIAL_APPROVED=SYNTHETIC_ONLY`. No persistent runtime or auto-start, no private GitHub read/write and no generated code execution.

## Actually executed proof

On Node.js v22.16.0, all original 13 bridge tests still pass, plus 9 new tests for the genuine loopback TCP HTTP transport: **22/22 PASS**, `node --check` PASS.

The new tests launch a temporary Node HTTP server on `127.0.0.1`, observe actual requests `GET /v1/models` plus `POST /v1/chat/completions`, and serve an explicit simulated coding transcript. The bridge actually reads two public files and executes the real Node baseline test (2/3 success). Its narrow candidate is verified by a second real Node test in a disposable copy (3/3 success). Tests verify no changes to the original, reject non-loopback URLs, unknown models, mismatched server response, redirect attempts, reported billing, invalid JSON, oversized bodies, and missing owner approval.

Git SHA-1 blobs verified against locally executed files:
- `scripts/continuum-coding-bridge.mjs`: `f9d9a5c7426552a051f884a7597a737e4b0f3c19`
- `scripts/continuum-selfhosted-coding.mjs`: `b2d9259bbbd62137cd55ddeabdf43fe8534a7383`
- `scripts/continuum-selfhosted-coding.test.mjs`: `fa6f712d6c5121d20a85985a4a22745795f48b08`

## What is NOT yet proved

No generative model was actually running in this proof. `local/owned-coder` is a synthetic server advertisement in the test, not Fable, Astra or Gemma. A listener on loopback can itself be an upstream proxy, so `verifiedNoUpstreamProxy=false`, `cloudModelUsed=null`, `verifiedModelWeights=false`. Public model IDs are not cryptographic validation of weights.

The owner's original Gemma 4 E4B `.litertlm` is not a llama.cpp GGUF file. Google LiteRT-LM and llama.cpp's OpenAI-compatible server are different runtimes; native Fold7/Termux and direct Android app integration remain separately unverified. Do not download duplicate weights or assume compatibility. No model, paid API, container or Termux package has been installed.

Even if API costs are zero, local CPU/GPU/electricity and storage are not guaranteed free. Open-weight model licenses and provider terms still apply. We are recreating general workflow functionality, **not** commercial proprietary weights, brand identity, quotas or authentication.

References:
- Google AI Edge Gemma 4: https://developers.google.com/edge/litert-lm/models/gemma-4
- Google LiteRT-LM: https://developers.google.com/edge/litert-lm/overview
- llama.cpp local API server: https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md

## Local reproduction (no payment or network requirement for tests)

```sh
node --test scripts/continuum-coding-bridge.test.mjs scripts/continuum-selfhosted-coding.test.mjs
node scripts/continuum-coding-bridge.mjs --prepare "$HOME/continuum-public-coding-trial"
```

A later **real** model trial requires the owner to inspect a running local server, confirm that it does not forward requests to the cloud, check the model license and then explicitly approve sending the public synthetic fixture. Example invocation ONLY if that exact local model name and endpoint are genuinely available:

```sh
CONTINUUM_SELFHOSTED_TRIAL_APPROVED=SYNTHETIC_ONLY \
CONTINUUM_SELFHOSTED_MODEL='local/owned-coder' \
CONTINUUM_SELFHOSTED_ENDPOINT='http://127.0.0.1:18080/v1/chat/completions' \
node scripts/continuum-selfhosted-coding.mjs --synthetic-trial "$HOME/continuum-public-coding-trial"
```

Neither example name nor port is claimed to exist on the owner's device. Actual tool calling depends on the model's tool-capable chat template. Do not set the local target to a forwarding proxy without separate, explicit consent.

## Subsequent gates and reset

First demonstrate a real owned model producing tool calls and a passing synthetic patch under private CPU/RAM/time limits. Then, only with new owner approval, design least-privilege access to a disposable worktree and existing CCC1 repair gate. Never write directly to main, install an APK, expose owner files or sign anything. Draft PR only.

Undo: close the dependent PR without merge. No account, service, daemon, model installation, key, billing, build or phone configuration was changed. Owner-created synthetic fixtures can be separately reviewed and deleted manually.
