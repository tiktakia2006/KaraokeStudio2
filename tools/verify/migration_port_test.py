import json, pathlib, re, sqlite3, sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCHEMA = ROOT / "app/schemas/com.example.karaokestudio.data.local.KaraokeDatabase/1.json"
MIGRATION = (ROOT / "app/src/main/java/com/example/karaokestudio/data/local/Migrations.kt").read_text()

passed, failed = 0, []


def check(name, cond, info=""):
    global passed
    if cond:
        passed += 1
    else:
        failed.append(f"{name} {info}")


def kotlin_statements(source):
    statements = []
    for m in re.finditer(r"db\.execSQL\(", source):
        depth, i = 0, m.end() - 1
        for j in range(i, len(source)):
            if source[j] == "(":
                depth += 1
            elif source[j] == ")":
                depth -= 1
                if depth == 0:
                    body = source[i + 1:j]
                    break
        parts = re.findall(r'"((?:\\.|[^"\\])*)"', body)
        statements.append("".join(parts))
    return statements


def build_v1():
    db = sqlite3.connect(":memory:")
    db.execute("PRAGMA foreign_keys = ON")
    schema = json.loads(SCHEMA.read_text())
    for entity in schema["database"]["entities"]:
        db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity["indices"]:
            db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    return db


def columns(db, table):
    return {r[1]: (r[2].upper(), bool(r[3])) for r in db.execute(f"PRAGMA table_info({table})")}


def indexes(db, table):
    result = {}
    for r in db.execute(f"PRAGMA index_list({table})"):
        cols = [c[2] for c in db.execute(f"PRAGMA index_info({r[1]})")]
        result[r[1]] = (bool(r[2]), cols)
    return result


statements = kotlin_statements(MIGRATION)
check("міграція містить інструкції", len(statements) >= 9, str(len(statements)))

db = build_v1()
db.executemany(
    "INSERT INTO songs (id, title, artist, durationMs, lyrics, audioSource, coverSource, genre, isDemo, searchKey) VALUES (?,?,?,?,?,?,?,?,?,?)",
    [
        (1, "Ранкова хвиля", "Hurt", 48000, "", "demo:morning-wave", "gradient:0", "Поп", 1, "ранкова хвиля hurt"),
        (2, "Нічне місто", "Hurt", 50000, "", "demo:night-city", "gradient:1", "Рок", 1, "нічне місто hurt"),
        (3, "Моя пісня", "Я", 60000, "[00:01.00]привіт", "file:///data/user/0/app/files/audio/a.m4a", "gradient:2", "Інше", 0, "моя пісня я"),
    ],
)
db.executemany(
    "INSERT INTO performances (id, songId, performedAt, durationMs, score, isDemo) VALUES (?,?,?,?,?,?)",
    [(1, 1, 1000, 48000, 90, 1), (2, 1, 2000, 48000, None, 0), (3, 3, 3000, 60000, 55, 0)],
)
db.execute("INSERT INTO recordings (id, performanceId, filePath, sizeBytes, durationMs, createdAt) VALUES (1, 3, '/x/a.m4a', 100, 60000, 3000)")
db.execute("INSERT INTO favorite_songs (songId, addedAt) VALUES (1, 5000)")
db.commit()

for statement in statements:
    db.execute(statement)
db.commit()

songs = {r[0]: r for r in db.execute("SELECT id, slug, remoteUpdatedAt FROM songs")}
check("slug демо-пісні 1", songs[1][1] == "morning-wave")
check("slug демо-пісні 2", songs[2][1] == "night-city")
check("slug власної пісні порожній", songs[3][1] is None)
check("remoteUpdatedAt за замовчуванням 0", all(s[2] == 0 for s in songs.values()))

client_ids = [r[0] for r in db.execute("SELECT clientId FROM performances ORDER BY id")]
check("clientId заповнено для всіх виконань", len(client_ids) == 3 and all(len(c) == 32 for c in client_ids), str(client_ids))
check("clientId унікальні", len(set(client_ids)) == 3)

check("дані пісень збережено", db.execute("SELECT COUNT(*) FROM songs").fetchone()[0] == 3)
check("дані виконань збережено", db.execute("SELECT score FROM performances WHERE id = 3").fetchone()[0] == 55)
check("запис збережено", db.execute("SELECT COUNT(*) FROM recordings").fetchone()[0] == 1)
check("обране збережено", db.execute("SELECT addedAt FROM favorite_songs WHERE songId = 1").fetchone()[0] == 5000)

expected_songs = {
    "id": ("INTEGER", True), "title": ("TEXT", True), "artist": ("TEXT", True), "durationMs": ("INTEGER", True),
    "lyrics": ("TEXT", True), "audioSource": ("TEXT", True), "coverSource": ("TEXT", False), "genre": ("TEXT", True),
    "isDemo": ("INTEGER", True), "searchKey": ("TEXT", True), "slug": ("TEXT", False), "remoteUpdatedAt": ("INTEGER", True),
}
expected_perf = {
    "id": ("INTEGER", True), "songId": ("INTEGER", True), "performedAt": ("INTEGER", True), "durationMs": ("INTEGER", True),
    "score": ("INTEGER", False), "isDemo": ("INTEGER", True), "clientId": ("TEXT", True),
}
expected_outbox = {
    "id": ("INTEGER", True), "type": ("TEXT", True), "entityKey": ("TEXT", True), "op": ("TEXT", True),
    "changedAt": ("INTEGER", True), "attempts": ("INTEGER", True),
}
check("структура songs", columns(db, "songs") == expected_songs, str(columns(db, "songs")))
check("структура performances", columns(db, "performances") == expected_perf, str(columns(db, "performances")))
check("структура sync_outbox", columns(db, "sync_outbox") == expected_outbox, str(columns(db, "sync_outbox")))

song_idx = indexes(db, "songs")
check("унікальний індекс slug", song_idx.get("index_songs_slug") == (True, ["slug"]), str(song_idx))
perf_idx = indexes(db, "performances")
check("унікальний індекс clientId", perf_idx.get("index_performances_clientId") == (True, ["clientId"]), str(perf_idx))
check("старі індекси виконань збережено", "index_performances_songId" in perf_idx and "index_performances_performedAt" in perf_idx)
outbox_idx = indexes(db, "sync_outbox")
check("унікальний індекс черги", outbox_idx.get("index_sync_outbox_type_entityKey") == (True, ["type", "entityKey"]), str(outbox_idx))

db.execute("INSERT INTO songs (title, artist, durationMs, lyrics, audioSource, genre, isDemo, searchKey) VALUES ('a','b',1,'','file:x','Інше',0,'a b')")
db.execute("INSERT INTO songs (title, artist, durationMs, lyrics, audioSource, genre, isDemo, searchKey) VALUES ('c','d',1,'','file:y','Інше',0,'c d')")
check("кілька пісень без slug дозволені", db.execute("SELECT COUNT(*) FROM songs WHERE slug IS NULL").fetchone()[0] == 3)
try:
    db.execute("INSERT INTO songs (title, artist, durationMs, lyrics, audioSource, genre, isDemo, searchKey, slug) VALUES ('x','y',1,'','demo:z','Інше',1,'x y','morning-wave')")
    check("дубль slug заборонено", False)
except sqlite3.IntegrityError:
    check("дубль slug заборонено", True)

db.execute("INSERT INTO sync_outbox (type, entityKey, op, changedAt, attempts) VALUES ('favorite','morning-wave','upsert',1,0)")
db.execute("INSERT OR REPLACE INTO sync_outbox (type, entityKey, op, changedAt, attempts) VALUES ('favorite','morning-wave','delete',2,0)")
rows = db.execute("SELECT op, changedAt FROM sync_outbox WHERE type='favorite' AND entityKey='morning-wave'").fetchall()
check("черга зводить зміни однієї сутності до останньої", rows == [("delete", 2)], str(rows))

db.execute("PRAGMA foreign_keys = ON")
db.execute("DELETE FROM songs WHERE slug = 'morning-wave'")
check("видалення пісні каскадно прибирає виконання", db.execute("SELECT COUNT(*) FROM performances WHERE songId = 1").fetchone()[0] == 0)

fk_problems = db.execute("PRAGMA foreign_key_check").fetchall()
check("цілісність зовнішніх ключів", not fk_problems, str(fk_problems))
check("PRAGMA integrity_check", db.execute("PRAGMA integrity_check").fetchone()[0] == "ok")

print(f"Пройдено: {passed}, провалено: {len(failed)}")
for item in failed:
    print("  ПРОВАЛ:", item)
sys.exit(1 if failed else 0)
