#!/usr/bin/env python3
from pathlib import Path
import re, xml.etree.ElementTree as ET
D=Path(__file__).resolve().parents[2]/"app/src/main/assets/muslim_v19/static"
svg=["charity","dhikr","eid","evening_azkar","fasting","hadith","hijri_calendar","islamic_events","morning_azkar","mosque","prayer_times","qibla","quran_audio","quran_bookmark","ramadan","tasbih","wudu","zakat"]
png=["file_000000001218820a8a39eaae196b66e0.png","file_000000007dc0820aa6c0f334ad42bca4.png","quran_icon.png","qaris_icon.png"]
expected={x+".svg" for x in svg}|set(png)
assert len(expected)==22 and len(svg)==18 and len(png)==4
actual={x.name for x in D.iterdir() if x.is_file()}
assert actual==expected,(sorted(expected-actual),sorted(actual-expected))
for name in svg:
 raw=(D/(name+".svg")).read_text(encoding="utf-8")
 assert ET.fromstring(raw).tag.lower().endswith("svg")
 assert not re.search(r"<animate(?:Transform|Motion)?\b",raw,re.I),name
 assert "<script" not in raw.lower(),name
 assert not re.search(r"""(?:xlink:)?href\s*=\s*['"]https?://""",raw,re.I),name
for name in png:
 raw=(D/name).read_bytes()
 assert raw.startswith(b"\x89PNG\r\n\x1a\n") and len(raw)>1024,name
print("22 V19 static icons verified; no animated source included.")