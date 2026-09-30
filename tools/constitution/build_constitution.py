"""
Builds shared/src/commonMain/composeResources/files/constitution.json from the National Archives
transcripts in sources/.

The founding text is taken word for word from archives.gov, original spelling and all ("chuse",
"defence"). Only the headings, the one-line plain-English summaries and the "changed by" notes are
ours, and they describe what the text says, never how courts have read it, so the app stays neutral.

Run from the project root:  python tools/constitution/build_constitution.py
The transcripts were saved on 2026-09-29 from:
  https://www.archives.gov/founding-docs/constitution-transcript
  https://www.archives.gov/founding-docs/bill-of-rights-transcript
  https://www.archives.gov/founding-docs/amendments-11-27
"""
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCES = os.path.join(HERE, "sources")
OUT = os.path.join(HERE, "..", "..", "shared", "src", "commonMain", "composeResources", "files", "constitution.json")

ROMAN = ["", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV",
         "XVI", "XVII", "XVIII", "XIX", "XX", "XXI", "XXII", "XXIII", "XXIV", "XXV", "XXVI", "XXVII"]

ARTICLES = {
    1: ("The Legislative Branch",
        "Creates Congress - the House and the Senate - and lists what it may and may not do."),
    2: ("The Executive Branch",
        "Creates the office of President, how the President is chosen, and the President's powers and duties."),
    3: ("The Judicial Branch",
        "Creates the Supreme Court and federal courts, and defines treason."),
    4: ("The States",
        "How the states treat each other's laws and citizens, and how new states join."),
    5: ("Amending the Constitution",
        "The two ways amendments can be proposed and ratified."),
    6: ("Debts, Supreme Law and Oaths",
        "Makes the Constitution the supreme law of the land and bans religious tests for office."),
    7: ("Ratification",
        "The Constitution took effect once nine states approved it."),
}

SECTIONS = {
    (1, 1): "Congress", (1, 2): "The House of Representatives", (1, 3): "The Senate",
    (1, 4): "Elections and Meetings", (1, 5): "Rules of Each House", (1, 6): "Pay, Privileges and Limits",
    (1, 7): "How a Bill Becomes Law", (1, 8): "Powers of Congress", (1, 9): "Limits on Congress",
    (1, 10): "Limits on the States",
    (2, 1): "The President and Vice President", (2, 2): "Powers of the President",
    (2, 3): "Duties of the President", (2, 4): "Impeachment",
    (3, 1): "The Federal Courts", (3, 2): "Cases the Courts Hear, and Jury Trials", (3, 3): "Treason",
    (4, 1): "Full Faith and Credit", (4, 2): "Rights of Citizens in Other States",
    (4, 3): "New States and Federal Land", (4, 4): "A Republican Form of Government",
}

# (article, section, clause) -> note. Clause numbers count paragraphs within a section, from 1.
CLAUSE_NOTES = {
    (1, 2, 3): ("Changed by the 14th Amendment, Section 2.", 14),
    (1, 3, 1): ("Changed by the 17th Amendment: senators are now elected by the people.", 17),
    (1, 3, 2): ("The part about filling vacancies was changed by the 17th Amendment.", 17),
    (1, 4, 2): ("Changed by the 20th Amendment, Section 2: Congress now meets on January 3.", 20),
    (1, 9, 1): ("This clause expired in 1808, by its own terms.", None),
    (1, 9, 4): ("Changed by the 16th Amendment, which allows an income tax.", 16),
    (2, 1, 3): ("Replaced by the 12th Amendment.", 12),
    (2, 1, 6): ("Affected by the 25th Amendment.", 25),
    (3, 2, 1): ("Changed by the 11th Amendment.", 11),
    (4, 2, 3): ("Replaced by the 13th Amendment, which abolished slavery.", 13),
}

AMENDMENTS = {
    1: ("Freedom of Religion, Speech, Press, Assembly and Petition",
        "Congress may not establish a religion, stop people from practicing one, or limit speech, the press, peaceful assembly, or petitioning the government."),
    2: ("The Right to Keep and Bear Arms",
        "The right of the people to keep and bear arms shall not be infringed."),
    3: ("Housing of Soldiers",
        "Soldiers may not be housed in private homes without the owner's consent."),
    4: ("Searches and Seizures",
        "Protects against unreasonable searches and seizures; warrants require probable cause."),
    5: ("Rights of Persons",
        "Grand juries, no double jeopardy, no forced self-incrimination, due process of law, and payment when private property is taken for public use."),
    6: ("Rights in Criminal Trials",
        "A speedy public trial by an impartial jury, the right to know the charges, face witnesses, call witnesses, and have a lawyer."),
    7: ("Jury Trials in Civil Cases",
        "Jury trials in civil lawsuits over more than twenty dollars."),
    8: ("Bail, Fines and Punishment",
        "No excessive bail or fines, and no cruel and unusual punishment."),
    9: ("Rights Kept by the People",
        "Listing some rights in the Constitution does not deny other rights the people keep."),
    10: ("Powers Kept by the States and the People",
         "Powers not given to the federal government, nor denied to the states, belong to the states or the people."),
    11: ("Lawsuits Against States",
         "Limits federal courts from hearing certain lawsuits against a state by people of another state or country."),
    12: ("Electing the President and Vice President",
         "Electors cast separate votes for President and Vice President."),
    13: ("Abolition of Slavery",
         "Abolishes slavery and involuntary servitude, except as punishment for a crime."),
    14: ("Citizenship, Due Process and Equal Protection",
         "Defines citizenship, requires states to give due process and equal protection of the laws, and sets rules on representation, office-holding and the public debt."),
    15: ("Voting Rights Regardless of Race",
         "The right to vote may not be denied because of race, color, or previous servitude."),
    16: ("Income Tax",
         "Congress may tax incomes without dividing the tax among the states by population."),
    17: ("Direct Election of Senators",
         "Senators are elected directly by the people of each state."),
    18: ("Prohibition of Alcohol",
         "Banned making, selling or transporting alcoholic drinks. Repealed by the 21st Amendment."),
    19: ("Women's Right to Vote",
         "The right to vote may not be denied because of sex."),
    20: ("Terms of Office",
         "Sets when terms begin - January 20 for the President, January 3 for Congress - and what happens if a President-elect dies or is not chosen."),
    21: ("Repeal of Prohibition",
         "Repeals the 18th Amendment."),
    22: ("Two-Term Limit for Presidents",
         "No person may be elected President more than twice."),
    23: ("Presidential Electors for Washington, D.C.",
         "Gives the District of Columbia electors in presidential elections."),
    24: ("Ban on Poll Taxes",
         "Voting in federal elections may not depend on paying a poll tax or other tax."),
    25: ("Presidential Succession and Disability",
         "The Vice President becomes President if the office is vacant, and sets rules for when a President cannot serve."),
    26: ("Voting Age of 18",
         "Citizens 18 or older may not be denied the vote because of age."),
    27: ("Changes to Congressional Pay",
         "A change in Congress's pay cannot take effect until after the next House election."),
}

# Amendments I-X share one date; the transcript page gives it.
BILL_OF_RIGHTS_DATES = ("Passed by Congress September 25, 1789.", "Ratified December 15, 1791.")


def lines_of(name):
    with open(os.path.join(SOURCES, name), encoding="utf-8") as f:
        return [line.strip() for line in f.read().splitlines()]


def constitution():
    lines = lines_of("constitution-transcript.txt")
    start = lines.index(next(l for l in lines if l.startswith("We the People")))
    preamble = lines[start]
    articles, article, section = [], None, None
    closing, signers = [], []
    i = start + 1
    while i < len(lines):
        line = lines[i]
        m = re.fullmatch(r"Article\. ([IVX]+)\.", line)
        s = re.fullmatch(r"Section\. (\d+)\.", line)
        if m:
            n = ROMAN.index(m.group(1))
            title, summary = ARTICLES[n]
            article = {"number": n, "roman": m.group(1), "title": title, "summary": summary, "sections": []}
            articles.append(article)
            section = None
        elif s:
            n = int(s.group(1))
            section = {"number": n, "title": SECTIONS[(article["number"], n)], "clauses": []}
            article["sections"].append(section)
        elif line.startswith("The Word, \"the,\" being interlined"):
            break
        elif line:
            if section is None:
                # Articles V, VI and VII have no sections: their paragraphs sit in one untitled one.
                section = {"number": 0, "title": "", "clauses": []}
                article["sections"].append(section)
            clause = {"text": line}
            key = (article["number"], section["number"], len(section["clauses"]) + 1)
            if key in CLAUSE_NOTES:
                clause["note"], clause["changedBy"] = CLAUSE_NOTES[key]
                if clause["changedBy"] is None:
                    del clause["changedBy"]
            section["clauses"].append(clause)
        i += 1
    # The attestation and signatures, kept as the Archives set them out.
    while i < len(lines) and not lines[i].startswith("For biographies"):
        if lines[i]:
            closing.append(lines[i])
        i += 1
    return preamble, articles, closing


def split_sections(body):
    """Paragraphs grouped under 'Section N.' headings; one untitled section if there are none."""
    sections, current = [], None
    for line in body:
        s = re.fullmatch(r"Section (\d+)\.", line)
        if s:
            current = {"number": int(s.group(1)), "paragraphs": []}
            sections.append(current)
        else:
            if current is None:
                current = {"number": 0, "paragraphs": []}
                sections.append(current)
            current["paragraphs"].append(line)
    return sections


def bill_of_rights():
    lines = lines_of("bill-of-rights-transcript.txt")
    out = []
    for n in range(1, 11):
        head = lines.index(f"Amendment {ROMAN[n]}")
        text = lines[head + 1]
        title, summary = AMENDMENTS[n]
        out.append({
            "number": n, "roman": ROMAN[n], "title": title, "summary": summary,
            "proposed": BILL_OF_RIGHTS_DATES[0], "ratified": BILL_OF_RIGHTS_DATES[1],
            "sections": [{"number": 0, "paragraphs": [text]}],
        })
    return out


def later_amendments():
    lines = lines_of("amendments-11-27.txt")
    first = lines.index("AMENDMENT XI")
    end = lines.index("Back to Constitution Main Page")
    lines = [l for l in lines[first:end] if l]
    out, block = [], []

    def flush():
        if not block:
            return
        n = ROMAN.index(block[0].split()[1])
        dates = block[1]
        rest = block[2:]
        notes = [l for l in rest if l.startswith("Note:") or l.startswith("*")]
        body = [l for l in rest if l not in notes]
        d = re.fullmatch(r"((?:Passed by Congress|Originally proposed) .*?\d{4}\.) (Ratified .*?\d{4}\.)\s*(.*)", dates)
        assert d, dates
        title, summary = AMENDMENTS[n]
        entry = {
            "number": n, "roman": ROMAN[n], "title": title, "summary": summary,
            "proposed": d.group(1), "ratified": d.group(2),
            "sections": split_sections(body),
        }
        # "Repealed by amendment 21." rides on the end of the dates line.
        notes = ([d.group(3)] if d.group(3) else []) + notes
        if notes:
            # A leading * ties a footnote to the starred words in the text, so it is kept.
            entry["notes"] = [re.sub(r"^\*\s*", "* ", re.sub(r"^Note:\s*", "", x)).strip() for x in notes]
        out.append(entry)

    for line in lines:
        if re.fullmatch(r"AMENDMENT [IVX]+", line):
            flush()
            block = [line]
        else:
            block.append(line)
    flush()
    return out


def main():
    preamble, articles, closing = constitution()
    amendments = bill_of_rights() + later_amendments()
    assert len(articles) == 7, len(articles)
    assert [a["number"] for a in amendments] == list(range(1, 28)), [a["number"] for a in amendments]
    for key in CLAUSE_NOTES:
        a, s, c = key
        section = next(x for x in articles[a - 1]["sections"] if x["number"] == s)
        assert len(section["clauses"]) >= c, key
    doc = {
        "source": "National Archives transcription",
        "sourceUrl": "https://www.archives.gov/founding-docs/constitution-transcript",
        "amendmentsSourceUrl": "https://www.archives.gov/founding-docs/amendments-11-27",
        "billOfRightsSourceUrl": "https://www.archives.gov/founding-docs/bill-of-rights-transcript",
        "spellingNote": "The text keeps the original spelling and capitalization of the signed documents.",
        "signed": "September 17, 1787",
        "preamble": preamble,
        "articles": articles,
        "closing": closing,
        "amendments": amendments,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    tmp = OUT + ".tmp"
    with open(tmp, "w", encoding="utf-8", newline="\n") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
    os.replace(tmp, OUT)
    words = sum(len(c["text"].split()) for a in articles for s in a["sections"] for c in s["clauses"])
    print(f"wrote {OUT}: 7 articles, {len(amendments)} amendments, {words} words in the articles")


main()
