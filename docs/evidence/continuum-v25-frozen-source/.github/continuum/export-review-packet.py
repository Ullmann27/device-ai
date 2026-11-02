#!/usr/bin/env python3
"""Export all tracked reviewable source files to a searchable PDF and exact ZIP."""
import hashlib
import json
import os
import subprocess
import zipfile
from pathlib import Path
from xml.sax.saxutils import escape
from reportlab.platypus import SimpleDocTemplate, Paragraph, Preformatted, PageBreak
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/"review-packet"
OUT.mkdir(exist_ok=True)
HEAD=subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT,text=True).strip()
TRACKED=subprocess.check_output(["git","ls-files","-z"],cwd=ROOT).split(b"\0")
EXT={".kt",".java",".mjs",".js",".json",".xml",".gradle",".kts",".yml",".yaml",".md",".properties",".toml",".py",".sh",".txt",".pro"}
EXCLUDE={".env",".key",".pem",".p12",".jks",".keystore"}
files=[]
for raw in TRACKED:
    if not raw:continue
    rel=os.fsdecode(raw)
    path=ROOT/rel
    if not path.is_file() or path.is_symlink():continue
    if path.suffix.lower() not in EXT and path.name not in {"gradlew","Dockerfile",".gitignore"}:continue
    if path.suffix.lower() in EXCLUDE or path.stat().st_size>600_000:continue
    data=path.read_bytes()
    if b"\0" in data:continue
    try:content=data.decode("utf-8")
    except UnicodeDecodeError:continue
    files.append((rel,data,content))
files.sort()
manifest={"repository":"Ullmann27/device-ai","branch":"copilot/pr-12-working-first-vertical-slice",
          "commit":HEAD,"files":[{"path":p,"bytes":len(b),"sha256":hashlib.sha256(b).hexdigest()}
                                   for p,b,_ in files]}
(OUT/"MANIFEST.json").write_text(json.dumps(manifest,indent=2,ensure_ascii=False),encoding="utf-8")
prompt="""YOU ARE GOOGLE GEMINI / ARAGON: ACT AS PRINCIPAL AI RESEARCH AND ANDROID ENGINEER.
This packet includes complete, unmodified tracked UTF-8 source files from a fixed commit.
Review ALL code, trace call sites, identify security and learning defects, and propose
substantial tested implementations for autonomous curriculum discovery, source deduplication,
training orchestration, model/package provenance, Android background jobs, web consent,
neural dialogue, and unbiased holdout benchmarks. No paid API is mandatory.
RETURN A PDF AND A ZIP OF FULL REPLACEMENT/NEW FILES, NOT DIFFS OR SNIPPETS.
START WITH A MACHINE-READABLE CHANGE INDEX: file path, action (ADD/MODIFY/DELETE),
affected classes/functions, purpose, dependency files, and test path. Then print every
changed file's COMPLETE UTF-8 CONTENT with no omissions or ellipses. Include tests,
Gradle/manifest edits, migration steps, reproducible build commands and limitations.
Never fabricate tests or performance. Keep owner data local and consent-gated.
Do not replace production chat with an unvalidated experiment. Work from this exact
commit and flag any mismatch. Prioritize four coherent implementation waves.
"""
(OUT/"GEMINI_TASK.txt").write_text(prompt,encoding="utf-8")
with zipfile.ZipFile(OUT/"Continuum_Source_Review.zip","w",compression=zipfile.ZIP_DEFLATED) as z:
    for rel,data,_ in files:z.writestr(rel,data)
    z.write(OUT/"MANIFEST.json","MANIFEST.json")
    z.write(OUT/"GEMINI_TASK.txt","GEMINI_TASK.txt")
regular="/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
mono="/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"
if Path(regular).exists():
    pdfmetrics.registerFont(TTFont("Body",regular))
    pdfmetrics.registerFont(TTFont("Mono",mono))
else:
    regular="Helvetica";mono="Courier"
body="Body" if regular.endswith(".ttf") else regular
codefont="Mono" if mono.endswith(".ttf") else mono
heading=ParagraphStyle("H",fontName=body,fontSize=13,leading=18,spaceAfter=9)
normal=ParagraphStyle("N",fontName=body,fontSize=8,leading=12,spaceAfter=5)
source=ParagraphStyle("S",fontName=codefont,fontSize=6,leading=8)
story=[Paragraph("CONTINUUM: COMPLETE SOURCE REVIEW PACKET",heading),
       Paragraph("Immutable commit: "+HEAD,normal),
       Paragraph("Included UTF-8 source files: "+str(len(files)),normal),
       Paragraph("TASK FOR GEMINI / ARAGON",heading)]
for line in prompt.splitlines():story.append(Paragraph(escape(line or " "),normal))
story.append(PageBreak())
story.append(Paragraph("FILE INDEX (path | bytes | SHA-256)",heading))
for item in manifest["files"]:
    story.append(Paragraph(escape(item["path"]+" | "+str(item["bytes"])+" | "+item["sha256"]),normal))
story.append(PageBreak())
for i,(rel,data,content) in enumerate(files):
    story.append(Paragraph(escape(f"FILE {i+1}/{len(files)}: {rel}"),heading))
    story.append(Paragraph("SHA-256: "+hashlib.sha256(data).hexdigest(),normal))
    for line in content.splitlines():
        # Preserve source exactly in ZIP; PDF is human-readable review companion.
        # Split long lines for page layout without changing source archive.
        while len(line)>108:
            story.append(Preformatted(line[:108],source))
            line=line[108:]
        story.append(Preformatted(line or " ",source))
    story.append(PageBreak())
pdf=OUT/"Continuum_Complete_Source_Review.pdf"
SimpleDocTemplate(str(pdf),pagesize=A4,leftMargin=30,rightMargin=30,
                  topMargin=32,bottomMargin=32,title="Continuum Complete Source Review").build(story)
print(json.dumps({"head":HEAD,"files":len(files),"pdf_bytes":pdf.stat().st_size}))
