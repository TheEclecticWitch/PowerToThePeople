"""Cuts the simulator recording from `sh tools/store-shots.sh testVideo` into the App Store videos.

    python tools/app_preview.py

- app-preview-iphone.mp4: Apple's App Preview, 886 x 1920 portrait, under 30 seconds, 30 fps, with a
  silent stereo track (App Store Connect turns down previews without one).
- walkthrough.mp4: the whole walk-through at 1080 wide, for App Review or anyone who asks to see the app.

The SEGMENTS are seconds into screen.mp4 and need choosing again after a new recording: the tour takes
a different time each run (keep-seconds, next to the recording, says roughly where it starts and ends). Needs ffmpeg from `pip install imageio-ffmpeg`.
"""
import subprocess
from pathlib import Path
import imageio_ffmpeg

RAW = Path("docs/store/apple/raw/iPhone 17 Pro Max/screen.mp4")
OUT = Path("docs/store/apple/video")
FF = imageio_ffmpeg.get_ffmpeg_exe()

# (start, end) in seconds, read off frames taken with `ffmpeg -ss <t>` (exact seeking; a recording from
# the simulator has an uneven frame rate, and the trim filter's idea of time drifts from it):
# opening the app, a member, a vote, elections, a bill, then civics and the citizenship test.
SEGMENTS = [(36.0, 41.5), (45.5, 50.5), (72.8, 77.3), (101.8, 106.0), (120.0, 124.5), (126.8, 132.3)]
WALKTHROUGH = (36.0, 133.0)


def run(*args):
    subprocess.run([FF, "-v", "error", "-y", *args], check=True)


def preview():
    inputs = [arg for a, b in SEGMENTS for arg in ("-ss", str(a), "-to", str(b), "-i", str(RAW))]
    n = len(SEGMENTS)
    parts = "".join(f"[{i}:v]setpts=PTS-STARTPTS,fps=30,scale=886:-2,crop=886:1920,setsar=1[v{i}];" for i in range(n))
    joined = "".join(f"[v{i}]" for i in range(n))
    total = sum(b - a for a, b in SEGMENTS)
    assert 15 <= total <= 30, f"App Previews must be 15 to 30 seconds; this is {total}"
    run(*inputs, "-f", "lavfi", "-i", "anullsrc=channel_layout=stereo:sample_rate=44100",
        "-filter_complex", parts + f"{joined}concat=n={n}:v=1:a=0,format=yuv420p[v]",
        "-map", "[v]", "-map", f"{n}:a", "-t", str(total),
        "-c:v", "libx264", "-profile:v", "high", "-level", "4.0", "-b:v", "10M", "-r", "30",
        "-c:a", "aac", "-b:a", "256k", "-movflags", "+faststart",
        str(OUT / "app-preview-iphone.mp4"))
    print(f"app-preview-iphone.mp4: {total:.1f} s")


def walkthrough():
    start, end = WALKTHROUGH
    run("-ss", str(start), "-to", str(end), "-i", str(RAW), "-f", "lavfi",
        "-i", "anullsrc=channel_layout=stereo:sample_rate=44100",
        "-vf", "fps=30,scale=1080:-2,format=yuv420p", "-map", "0:v", "-map", "1:a", "-shortest",
        "-c:v", "libx264", "-crf", "20", "-c:a", "aac", "-movflags", "+faststart",
        str(OUT / "walkthrough.mp4"))
    print(f"walkthrough.mp4: {end - start:.0f} s")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    preview()
    walkthrough()
