from pytubefix import YouTube
from pytubefix.cli import on_progress

url = "https://www.youtube.com/watch?v=S-z6vyR89Ig"

yt = YouTube(url, use_oauth=True, on_progress_callback=on_progress, token_file="token.json")

ys = yt.streams.get_audio_only()
ys.download()