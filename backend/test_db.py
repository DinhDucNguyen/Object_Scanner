from app.db.session import SessionLocal
from app.models.translation import Translation
from app.models.object import Object

db = SessionLocal()

# Check translations
trans_count = db.query(Translation).count()
print(f"Total translations: {trans_count}")

if trans_count > 0:
    sample = db.query(Translation).first()
    print(f"\n✅ Sample translation:")
    print(f"   word_name: {sample.word_name}")
    print(f"   phonetic: {sample.phonetic}")

# Check objects
obj_count = db.query(Object).count()
print(f"\nTotal objects: {obj_count}")

# Test specific object
laptop = db.query(Object).filter_by(object_code="laptop").first()
if laptop:
    print(f"\n✅ Found laptop object")
    laptop_trans = db.query(Translation).filter_by(object_id=laptop.id).all()
    print(f"   Laptop has {len(laptop_trans)} translations")
    for t in laptop_trans:
        print(f"   - Language ID {t.language_id}: {t.word_name}")

db.close()
