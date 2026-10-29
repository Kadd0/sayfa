# SPDX-License-Identifier: GPL-3.0-or-later
"""Merge WikDict en-tr and FreeDict eng-tur into one small SQLite file the app ships in assets.

Usage:
  python tools/build_dict.py <en-tr.sqlite3> <freedict-eng-tur-dir> app/src/main/assets/dict.db

Sources (download into dictsrc/, which git ignores):
  en-tr.sqlite3   WikDict, https://www.wikdict.com/  (CC BY-SA 4.0)
  eng-tur/        FreeDict StarDict build (eng-tur.idx.gz, eng-tur.dict.dz),
                  https://freedict.org/downloads/  (GPL-2.0-or-later)
The result is a combined work under GPL-3.0-or-later.


Table entries(w TEXT PRIMARY KEY, tr TEXT, senses TEXT):
  w      lower-case English headword
  tr     Turkish meanings joined with " | ", best first
  senses up to 4 lines "English definition\tTurkish words", from WikDict
"""
import gzip, html, re, sqlite3, struct, sys, os

wikdict, freedict_dir, out = sys.argv[1], sys.argv[2], sys.argv[3]

entries = {}   # w -> [tr list, senses list]

def tidy(t):
    """Repairs items cut mid-parenthesis: "(çoğ.) wolves)  kurt" -> "kurt"; drops "( (İng.) (k"."""
    t = re.sub(r"\s+", " ", t).strip()
    if t.count("(") < t.count(")"):
        t = t.rsplit(")", 1)[1].strip()
    elif t.count("(") > t.count(")"):
        t = ""
    # Only grammar labels, no meaning: "(tek.) ve (çoğ.)"
    if re.sub(r"\([^)]*\)", "", t).strip() in ("", "ve", "veya", "ya da"):
        t = ""
    return t


LABEL = re.compile(r"\([^)]*\)")
PARTICLES = {"a", "an", "the", "of", "on", "off", "in", "into", "out", "up", "down", "for", "to", "so", "as",
             "about", "over", "away", "back", "with", "by", "at", "and", "or", "upon", "after", "now", "it"}


def drop_example(w, t):
    """FreeDict items often run on into an English example: "fil white elephant elde ..." -> "fil",
    "ile And howl (k.dili) Hem de nasil" -> "ile". Returns "" when the item is only an English phrase."""
    m = re.search(r"(?<![\w])" + re.escape(w) + r"(?![\w])", t, re.I)
    if not m:
        return t
    if m.start() == 0:
        rest = t[m.end():].split()
        return "" if rest and rest[0].lower().strip(".,") in PARTICLES else t
    head = t[:m.start()].strip(" .:;,")
    if not LABEL.sub("", head).strip() or re.search(r"[A-Z]\w*$", LABEL.sub("", head).strip()) and " " not in LABEL.sub("", head).strip():
        return t  # "(zool.) Felis serval", "(müz.) atonal": a label and the name itself
    # Drop English words left just before the phrase ("fil white", "sincap European").
    while " " in head and is_english(head.split()[-1]):
        head = head.rsplit(None, 1)[0].rstrip(" .:;,")
    return head


def is_english(token):
    """An English headword that never appears inside a Turkish meaning."""
    t = token.lower().strip(".,:;")
    return t in PARTICLES or (t in ENGLISH and t not in TURKISH)


def add_tr(w, items):
    e = entries.setdefault(w, [[], []])
    for t in items:
        t = tidy(t)
        if t and t not in e[0]:
            e[0].append(t)

src = sqlite3.connect(wikdict)
for rep, trans, score in src.execute(
        "select written_rep, trans_list, max_score from simple_translation order by max_score desc"):
    if rep and trans:
        add_tr(rep.strip().lower(), trans.split(" | "))

for rep, sense, trans in src.execute(
        "select written_rep, sense, trans_list from translation where sense is not null order by written_rep, sense_num"):
    if not rep or not trans:
        continue
    e = entries.get(rep.strip().lower())
    if e is not None and len(e[1]) < 4:
        line = "%s\t%s" % (sense.strip(), trans.strip())
        if line not in e[1]:
            e[1].append(line)

# Words seen on each side of WikDict, for telling English leftovers from Turkish in FreeDict items.
ENGLISH = set(entries)
TURKISH = {tok.lower() for e in entries.values() for t in e[0] for tok in re.findall(r"[\w]+", t)}

# FreeDict StarDict: .idx.gz holds (word\0, offset u32 BE, size u32 BE); .dict.dz is gzip-compatible.
base = os.path.join(freedict_dir, "eng-tur")
idx = gzip.open(base + ".idx.gz").read()
data = gzip.open(base + ".dict.dz").read()
pos, added = 0, 0
while pos < len(idx):
    end = idx.index(b"\0", pos)
    word = idx[pos:end].decode("utf-8", "replace").strip().lower()
    off, size = struct.unpack(">II", idx[end + 1:end + 9])
    pos = end + 9
    text = data[off:off + size].decode("utf-8", "replace")
    items = []
    for item in re.findall(r"<div>([^<]*)</div>", text):
        item = html.unescape(item).strip()
        # "kandırmak. persuadable  kandırılabilir": text after ". " belongs to a derived word
        derived = ". " in item
        item = item.split(". ")[0].rstrip(".").strip()
        item = tidy(drop_example(word, item))
        if item and item.lower() != word:
            items.append(item)
        if derived:
            break
    if word and items:
        had = word in entries
        add_tr(word, items[:8])
        added += not had

dst_path = out
if os.path.exists(dst_path):
    os.remove(dst_path)
dst = sqlite3.connect(dst_path)
dst.execute("create table entries(w text primary key, tr text, senses text)")
dst.execute("create table android_metadata(locale text)")
dst.execute("insert into android_metadata values('en_US')")
dst.executemany("insert into entries values(?,?,?)",
                ((w, " | ".join(e[0][:10]), "\n".join(e[1])) for w, e in entries.items() if e[0]))
dst.commit()
dst.execute("vacuum")
n = dst.execute("select count(*) from entries").fetchone()[0]
dst.close()
print("entries:", n, "from freedict only:", added, "size MB: %.1f" % (os.path.getsize(dst_path) / 1e6))
