"""Builds the Anki packages that AnkiPackageReaderTest reads, into ../assets/anki.

Run it again after changing what the packages hold:

    pip install zstandard
    python make_anki_packages.py

The packages follow what Anki itself writes (checked against decks exported from Anki 2.1 and 23.10):

* legacy.apkg      one collection.anki2, as Anki 2.0 and tools like genanki write it.
* legacy21.apkg    collection.anki21 with the notes, next to a collection.anki2 that only asks to update
                   Anki: what Anki exports with "Support older Anki versions" ticked.
* modern.apkg      collection.anki21b, the same database at schema 18, compressed with zstd, again next
                   to the stub: Anki's default export since 2.1.50.
* broken.apkg      a modern package whose compressed collection is cut short.

The schema 18 database has a deck table whose names are sorted with Anki's own collation, "unicase",
and is left in write-ahead-log mode: the two things a reader without Anki's code trips over.
"""
import json
import os
import sqlite3
import tempfile
import zipfile

import zstandard

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "anki")
SEPARATOR = "\x1f"

LEGACY_TABLES = """
CREATE TABLE col (id integer PRIMARY KEY, crt integer NOT NULL, mod integer NOT NULL, scm integer NOT NULL,
  ver integer NOT NULL, dty integer NOT NULL, usn integer NOT NULL, ls integer NOT NULL, conf text NOT NULL,
  models text NOT NULL, decks text NOT NULL, dconf text NOT NULL, tags text NOT NULL);
CREATE TABLE notes (id integer PRIMARY KEY, guid text NOT NULL, mid integer NOT NULL, mod integer NOT NULL,
  usn integer NOT NULL, tags text NOT NULL, flds text NOT NULL, sfld integer NOT NULL, csum integer NOT NULL,
  flags integer NOT NULL, data text NOT NULL);
CREATE TABLE cards (id integer PRIMARY KEY, nid integer NOT NULL, did integer NOT NULL, ord integer NOT NULL,
  mod integer NOT NULL, usn integer NOT NULL, type integer NOT NULL, queue integer NOT NULL, due integer NOT NULL,
  ivl integer NOT NULL, factor integer NOT NULL, reps integer NOT NULL, lapses integer NOT NULL, left integer NOT NULL,
  odue integer NOT NULL, odid integer NOT NULL, flags integer NOT NULL, data text NOT NULL);
CREATE INDEX ix_cards_nid ON cards (nid);
"""

DECK_TABLE = """
CREATE TABLE decks (id integer PRIMARY KEY NOT NULL, name text NOT NULL COLLATE unicase, mtime_secs integer NOT NULL,
  usn integer NOT NULL, common blob NOT NULL, kind blob NOT NULL);
CREATE UNIQUE INDEX idx_decks_name ON decks (name);
"""

GREETINGS, FOOD, DEFAULT = 1700000000001, 1700000000002, 1

# (fields, deck of the first card, deck of a second card or None)
NOTES = [
    (["Good morning", "Guten Morgen"], GREETINGS, None),
    (["<b>Thank you</b> very&nbsp;much", "<div>Vielen Dank</div><div><img src=\"danke.jpg\"></div>[sound:danke.mp3]"], GREETINGS, None),
    # As this app's own Anki export writes a card with alternatives, after a trip through Anki.
    (["Do you want to come?", "Möchtest du kommen?<br><br>Also: Willst du kommen? / Möchten Sie kommen?<br>Note: Sie is formal."], GREETINGS, None),
    # A note type with a sound-only field between the two sides, and one card in each of two decks.
    (["bread", "[sound:brot.mp3]", "das Brot", "Ich esse Brot."], FOOD, GREETINGS),
    # A cloze note: the text with its gap filled, and the extra field as the other side.
    (["The {{c1::cheese::food}} is good.", "Der Käse ist gut."], FOOD, None),
    (["only one side", ""], FOOD, None),
    (["water", "das Wasser"], DEFAULT, None),
]

DECKS = {DEFAULT: ["Default"], GREETINGS: ["German", "Greetings"], FOOD: ["German", "Food & Drink"]}

STUB_NOTE = "Please update to the latest Anki version, then import the .colpkg/.apkg file again." + SEPARATOR


def database(build):
    """The bytes of a database that build(connection) fills."""
    handle, path = tempfile.mkstemp(suffix=".anki2")
    os.close(handle)
    connection = sqlite3.connect(path)
    connection.create_collation("unicase", lambda a, b: (a.lower() > b.lower()) - (a.lower() < b.lower()))
    build(connection)
    connection.commit()
    connection.close()
    with open(path, "rb") as file:
        data = file.read()
    os.remove(path)
    return data


def add_notes(connection, notes):
    card_id = 1
    for note_id, (fields, deck, second_deck) in enumerate(notes, start=1):
        connection.execute("INSERT INTO notes VALUES (?, ?, 1, 0, -1, '', ?, ?, 0, 0, '')", (note_id, "guid%d" % note_id, SEPARATOR.join(fields), fields[0]))
        # The second card comes first in the table, so a reader has to go by the card's position, not its row.
        for ord_, did in sorted(enumerate([deck] + ([second_deck] if second_deck else [])), reverse=True):
            connection.execute("INSERT INTO cards VALUES (?, ?, ?, ?, 0, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, '')", (card_id, note_id, did, ord_))
            card_id += 1


def legacy(notes):
    def build(connection):
        connection.executescript(LEGACY_TABLES)
        decks = {str(i): {"id": i, "name": "::".join(levels), "usn": 0} for i, levels in DECKS.items()}
        connection.execute("INSERT INTO col VALUES (1, 0, 0, 0, 11, 0, 0, 0, '{}', '{}', ?, '{}', '{}')", (json.dumps(decks),))
        add_notes(connection, notes)
    return database(build)


def modern(notes):
    def build(connection):
        connection.execute("PRAGMA journal_mode = WAL")
        connection.executescript(LEGACY_TABLES + DECK_TABLE)
        connection.execute("INSERT INTO col VALUES (1, 0, 0, 0, 18, 0, 0, 0, '', '', '', '', '')")
        for i, levels in DECKS.items():
            connection.execute("INSERT INTO decks VALUES (?, ?, 0, 0, x'', x'')", (i, SEPARATOR.join(levels)))
        add_notes(connection, notes)
    return database(build)


def package(name, entries, compression):
    with zipfile.ZipFile(os.path.join(OUT, name), "w", compression) as archive:
        for entry, data in entries:
            archive.writestr(entry, data)
    print(name, os.path.getsize(os.path.join(OUT, name)), "bytes")


def main():
    os.makedirs(OUT, exist_ok=True)
    stub = legacy([([STUB_NOTE.rstrip(SEPARATOR), ""], DEFAULT, None)])
    compressed = zstandard.ZstdCompressor().compress(modern(NOTES))
    package("legacy.apkg", [("collection.anki2", legacy(NOTES)), ("media", "{}")], zipfile.ZIP_DEFLATED)
    # The stub comes first in the zip here, so a reader can't simply take the first collection it meets.
    package("legacy21.apkg", [("meta", b"\x08\x02"), ("collection.anki2", stub), ("collection.anki21", legacy(NOTES)), ("media", "{}")], zipfile.ZIP_DEFLATED)
    package("modern.apkg", [("meta", b"\x08\x03"), ("collection.anki21b", compressed), ("collection.anki2", stub), ("media", b""), ("0", b"not a picture")], zipfile.ZIP_STORED)
    package("broken.apkg", [("meta", b"\x08\x03"), ("collection.anki21b", compressed[: len(compressed) // 2]), ("collection.anki2", stub), ("media", b"")], zipfile.ZIP_STORED)


if __name__ == "__main__":
    main()
