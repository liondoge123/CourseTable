"""Validate the actual v4->v5 SQL against Room schemas, without an Android device."""
import json
import re
import sqlite3
from pathlib import Path

root = Path(__file__).resolve().parent.parent
schemas = [json.loads((root / "app/schemas/com.coursetable.app.data.AppDatabase" / f"{version}.json").read_text(encoding="utf-8"))["database"] for version in (4, 5)]
connection = sqlite3.connect(":memory:")
connection.execute("PRAGMA foreign_keys=ON")
for entity in schemas[0]["entities"]:
    connection.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    for index in entity.get("indices", []):
        connection.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
connection.execute("INSERT INTO courses VALUES(7,1,'Math','Teacher','A301',1,1,2,1,18,0,123,'1,4,8')")
connection.execute("INSERT INTO courses VALUES(9,1,'Math','Teacher','B301',3,3,2,1,18,1,456,'')")
source = (root / "app/src/main/java/com/coursetable/app/data/Course.kt").read_text(encoding="utf-8")
block = source.split("val MIGRATION_4_5", 1)[1].split("internal val MIGRATION_3_4", 1)[0]
queries = re.findall(r'db.execSQL\("([^"\n]*)"\)', block)
assert queries, "migration SQL not found"
for query in queries:
    connection.execute(query)
for entity in schemas[1]["entities"]:
    actual = connection.execute("PRAGMA table_info(" + entity["tableName"] + ")").fetchall()
    assert [(r[1], r[2], bool(r[3])) for r in actual] == [(f["columnName"], f["affinity"], f.get("notNull", False)) for f in entity["fields"]], entity["tableName"]
    for index in entity.get("indices", []):
        actual_index = connection.execute("SELECT name FROM sqlite_master WHERE type='index' AND name=?", (index["name"],)).fetchone()
        assert actual_index, index["name"]
assert connection.execute("SELECT id,courseId,location,selectedWeeksCsv FROM course_sessions ORDER BY id").fetchall() == [(7, 7, "A301", "1,4,8"), (9, 9, "B301", "")]
assert connection.execute("SELECT color FROM courses ORDER BY id").fetchall() == [(123,), (456,)]
connection.execute("DELETE FROM courses WHERE id=7")
assert connection.execute("SELECT id FROM course_sessions").fetchall() == [(9,)]
assert connection.execute("PRAGMA foreign_key_check").fetchall() == []
print("SQLite migration, schema columns/indexes, stable IDs, weeks, colors and cascade validation passed")
