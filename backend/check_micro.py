import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
from app.db.session import SessionLocal
from sqlalchemy import text

db = SessionLocal()
oid = 194

tables = [
    ("BanDich", "doi_tuong_id"),
    ("AnhDoiTuong", "doi_tuong_id"),
    ("TienTrinhHocTap", "doi_tuong_id"),
    ("LichSuQuet", "doi_tuong_id"),
    ("AIPrediction", "doi_tuong_id"),
]

for table, col in tables:
    try:
        count = db.execute(text(f"SELECT COUNT(*) FROM {table} WHERE {col}=:id"), {"id": oid}).scalar()
        print(f"{table}: {count} rows")
    except Exception as e:
        print(f"{table}: loi - {e}")

db.close()
