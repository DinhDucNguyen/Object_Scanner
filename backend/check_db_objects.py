"""Kiểm tra objects trong DB và so sánh với ML Kit mappings"""
from sqlalchemy import create_engine, text
from dotenv import load_dotenv
import os

load_dotenv()
engine = create_engine(os.getenv("DATABASE_URL"))

print("=" * 70)
print("DANH SÁCH OBJECTS TRONG DATABASE")
print("=" * 70)

with engine.connect() as conn:
    result = conn.execute(text("""
        SELECT o.object_code, COUNT(t.id) as translation_count
        FROM objects o
        LEFT JOIN translations t ON o.id = t.object_id
        GROUP BY o.object_code
        ORDER BY o.object_code
    """))
    
    objects_in_db = []
    for row in result:
        objects_in_db.append(row[0])
        status = "✅" if row[1] > 0 else "⚠️"
        print(f"{status} {row[0]:<20} ({row[1]} translations)")

print(f"\nTổng: {len(objects_in_db)} objects trong DB")

print("\n" + "=" * 70)
print("ML KIT OBJECT MAPPINGS (13 objects)")
print("=" * 70)
print("Đây là những vật thể ML Kit có thể nhận diện trên điện thoại:")
print()

ml_kit_mappings = {
    "Personal computer": "laptop",
    "Laptop": "laptop", 
    "Mobile phone": "cell_phone",
    "Telephone": "cell_phone",
    "Computer keyboard": "keyboard",
    "Computer mouse": "mouse",
    "Television": "television",
    "Book": "book",
    "Pen": "pen",
    "Coffee cup": "cup",
    "Drink": "cup",
    "Bottle": "bottle",
    "Chair": "chair",
}

print("ML Kit Label → object_code:")
for ml_label, code in ml_kit_mappings.items():
    in_db = "✅ CÓ trong DB" if code in objects_in_db else "❌ CHƯA có trong DB"
    print(f"  {ml_label:<25} → {code:<15} {in_db}")

print("\n" + "=" * 70)
print("PHÂN TÍCH NGUYÊN NHÂN 'Không nhận diện được vật thể'")
print("=" * 70)
print()
print("1. OBJECT KHÔNG TRONG ML KIT MAPPINGS:")
print("   → ML Kit detect nhưng không map được sang object_code")
print("   → Android hiển thị 'Không nhận diện được vật thể'")
print()
print("2. CONFIDENCE QUÁ THẤP (<0.6):")
print("   → ML Kit detect với confidence < 0.6")
print("   → Android skip và hiển thị lỗi")
print()
print("3. DB TRẢ EMPTY + GEMINI FAIL:")
print("   → Object không có translations")
print("   → Android gọi Gemini fallback nhưng fail (quota/network)")
print("   → Hiển thị lỗi")
print()
print("4. NETWORK ISSUE:")
print("   → Android không kết nối được backend 192.168.1.84:8000")
print("   → Timeout hoặc connection refused")
print()
print("=" * 70)
print("ĐỂ KIỂM TRA:")
print("=" * 70)
print("1. Bạn đang quét vật thể GÌ? (laptop, phone, cup, book...?)")
print("2. Check Android Studio Logcat có log gì không?")
print("3. Backend terminal có log '🔍 [SCAN] Received request' không?")
print("4. Thử quét các vật thể CÓ trong 13 mappings:")
print("   - Laptop/Computer")
print("   - Phone")
print("   - Keyboard")
print("   - Mouse")
print("   - Cup/Coffee")
print("   - Bottle")
print("   - Book")
