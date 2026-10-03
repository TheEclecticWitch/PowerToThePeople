"""
Builds the app's citizenship-test quiz from USCIS's official list: "128 Civics Questions and Answers (2025
version)", form M-1778. Run from the project root after downloading the PDF:

  python tools/civics/build_civics_test.py path/to/2025-Civics-Test-128-Questions-and-Answers.pdf

Writes shared/src/commonMain/composeResources/files/civics_test.json. Questions and answers are copied word
for word. Answers that depend on where the reader lives or who holds an office now are tagged so the app can
fill them in; USCIS's own note for each is kept.
"""
import json
import re
import sys

from pypdf import PdfReader

SOURCE = "https://www.uscis.gov/sites/default/files/document/questions-and-answers/2025-Civics-Test-128-Questions-and-Answers.pdf"
OUT = "shared/src/commonMain/composeResources/files/civics_test.json"

# Questions whose answer the app can supply or must point elsewhere for, by number.
DYNAMIC = {23: "senators", 29: "representative", 30: "speaker", 38: "president", 39: "vicePresident",
           57: "chiefJustice", 61: "governor", 62: "capital"}
STAR = ""  # how the PDF's asterisk mark comes out of the text layer


def main(pdf):
    text = "\n".join(page.extract_text() for page in PdfReader(pdf).pages)
    lines = []
    for raw in text.split("\n"):
        line = raw.strip()
        # Page furniture repeated on every page.
        if not line or re.fullmatch(r"\d+ of \d+", line) or line == "uscis.gov/citizenship" or line == "*":
            continue
        lines.append(line)

    start = lines.index("AMERICAN GOVERNMENT")
    section = subsection = None
    questions, current = [], None
    for line in lines[start:]:
        if line in ("AMERICAN GOVERNMENT", "AMERICAN HISTORY", "SYMBOLS AND HOLIDAYS"):
            section = line.title().replace(" And ", " and ")
            continue
        sub = re.fullmatch(r"[A-C]: (.+)", line)
        if sub:
            subsection = sub.group(1)
            continue
        q = re.fullmatch(r"(\d{1,3})\.\s+(.+)", line)
        if q and int(q.group(1)) == len(questions) + 1:
            current = {"n": int(q.group(1)), "section": section, "part": subsection, "q": q.group(2), "answers": []}
            questions.append(current)
            continue
        if line.startswith("•"):
            current["answers"].append(line.lstrip("• ").strip())
        elif current is not None and current["answers"]:
            current["answers"][-1] += " " + line          # an answer wrapped onto the next line
        elif current is not None:
            current["q"] += " " + line                    # a question wrapped onto the next line

    for q in questions:
        star = "*" in q["q"] or STAR in q["q"]
        q["q"] = " ".join(q["q"].replace("*", "").replace(STAR, "").split())
        q["answers"] = [" ".join(a.split()) for a in q["answers"]]
        if star:
            q["senior"] = True
        if q["n"] in DYNAMIC:
            q["dynamic"] = DYNAMIC[q["n"]]

    assert len(questions) == 128, f"expected 128 questions, got {len(questions)}"
    assert all(q["answers"] for q in questions), [q["n"] for q in questions if not q["answers"]]
    seniors = sum(1 for q in questions if q.get("senior"))
    assert seniors == 20, f"expected 20 starred questions, got {seniors}"
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump({
            "title": "128 Civics Questions and Answers (2025 version)",
            "form": "M-1778 (09/25)",
            "source": SOURCE,
            "updates": "https://www.uscis.gov/citizenship/testupdates",
            "asked": 20, "toPass": 12, "seniorAsked": 10, "seniorToPass": 6,
            "questions": questions,
        }, f, ensure_ascii=False, indent=1)
    print(f"{len(questions)} questions, {seniors} for the 65/20 rule, written to {OUT}")


if __name__ == "__main__":
    main(sys.argv[1])
