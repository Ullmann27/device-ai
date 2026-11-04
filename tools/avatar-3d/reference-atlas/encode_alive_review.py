"""Encode verified avatar motion."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--frames", type=Path, required=True)
    parser.add_argument("--poses", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    poses = json.loads(args.poses.read_text())
    manifest_path = args.frames / "pose-replay-manifest.json"
    manifest = json.loads(manifest_path.read_text())
    assert manifest["status"] == "PASS" and manifest["rendered"]
    assert manifest["poseExportSha256"] == sha(args.poses)
    animation = poses["animation"]
    count = animation["frameCount"]
    assert len(manifest["frames"]) == count
    for index, frame in enumerate(manifest["frames"]):
        assert frame["file"] == f"alive-f{index:03d}.png"
        assert sha(args.frames / frame["file"]) == frame["imageSha256"]
    args.output.mkdir(parents=True, exist_ok=True)
    video = args.output / "Continuum_Avatar_Lebendig.mp4"
    poster = args.output / "Continuum_Avatar_Lebendig.png"
    font = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
    assert font.is_file()
    filters = ["pad=iw:ih+48:0:0:black"]
    for start, end, label in [(0, 2, "Ruhe"), (2, 4, "Zuhören"), (4, 6, "Denken"),
                              (6, 10, "Sprechen"), (10, 12, "Stopp · Ruhe")]:
        filters.append(f"drawtext=fontfile={font}:text='CONTINUUM · {label}':"
                       f"fontcolor=white:fontsize=17:x=20:y=h-31:enable='gte(t,{start})*lt(t,{end})'")
    command = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-framerate",
        str(animation["framesPerSecond"]), "-i", str(args.frames / "alive-f%03d.png"),
        "-frames:v", str(count), "-vf", ",".join(filters), "-c:v", "libx264", "-preset", "medium",
        "-crf", "18", "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(video)]
    subprocess.run(command, check=True)
    shutil.copyfile(args.frames / "alive-f000.png", poster)
    probe = json.loads(subprocess.check_output(["ffprobe", "-v", "error", "-count_frames",
        "-show_entries", "stream=codec_type,codec_name,width,height,r_frame_rate,nb_read_frames,duration",
        "-of", "json", str(video)], text=True))
    assert len(probe["streams"]) == 1
    stream = probe["streams"][0]
    assert stream["codec_type"] == "video" and int(stream["nb_read_frames"]) == count
    assert abs(float(stream["duration"]) - animation["durationSeconds"]) < .001
    result = {"schema": "continuum-avatar-alive-video-v1", "status": "PASS",
        "sourceCommit": poses["sourceCommit"], "assetSha256": manifest["assetSha256"],
        "poseExportSha256": sha(args.poses), "renderManifestSha256": sha(manifest_path),
        "videoSha256": sha(video), "posterSha256": sha(poster), "probe": probe,
        "encoderCommand": command, "evidenceClass": "SILENT_HOST_RENDER_NOT_DEVICE",
        "syntheticInputs": True, "audio": False}
    (args.output / "video-provenance.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({"status": "PASS", "video": str(video), "frames": count, "sha256": sha(video)}))


if __name__ == "__main__":
    main()
