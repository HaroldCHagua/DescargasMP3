import os
import yt_dlp

def get_version():
    return yt_dlp.version.__version__

def download(url, output_dir):
    os.makedirs(output_dir, exist_ok=True)
    opts = {
        "format": "bestaudio/best",
        "outtmpl": os.path.join(output_dir, "%(title).120B [%(id)s].%(ext)s"),
        "noplaylist": True,
        "writethumbnail": True,
        "writeinfojson": True,
        "quiet": True,
        "no_warnings": False,
        "retries": 3,
        "fragment_retries": 3,
    }
    with yt_dlp.YoutubeDL(opts) as ydl:
        ydl.download([url])
    return "ok"
