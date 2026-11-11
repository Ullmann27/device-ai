# Original images for review

The six files in [native](native) are unchanged source-bound Robolectric API28
Canvas captures. The eight files in [../original-avatar](../original-avatar)
are unchanged Blender CPU pose replays. Neither backend is a Fold7 test.

The PDF booklet uses three CPU poses and all six native captures. Its generator
pins the pose manifest and all nine image hashes before writing. See
[booklet.json](booklet.json) and [input-validation.json](input-validation.json).

To reproduce from this evidence checkout (Python ReportLab and DejaVu fonts required):

```bash
proof_dir=$(mktemp -d)
mkdir -p "$proof_dir/apk53-a980-delivery"
cp docs/qa/apk53-a980/images/native/*.png "$proof_dir/apk53-a980-delivery/"
cp docs/qa/apk53-a980/original-avatar/ruhe.png "$proof_dir/apk53-a980-delivery/Continuum-Code53-Originalavatar-Ruhe-CPU.png"
cp docs/qa/apk53-a980/original-avatar/fensterblick.png "$proof_dir/apk53-a980-delivery/Continuum-Code53-Originalavatar-Fensterblick-CPU.png"
cp docs/qa/apk53-a980/original-avatar/sprechen-geskriptet.png "$proof_dir/apk53-a980-delivery/Continuum-Code53-Originalavatar-Sprechen-CPU.png"
python3 docs/qa/apk53-a980/images/create-image-proof-pdf.py --artifact-root "$proof_dir" --avatar-manifest docs/qa/apk53-a980/original-avatar/pose-replay-manifest.json
```

PDF hash e9255cfb14e275867e1e1fab05fd949a0a9740ebac55f15951283440f8e5f16b; reproducibility of PDF bytes also depends on
identical ReportLab/fonts. Original PNG identity is independently pinned.
