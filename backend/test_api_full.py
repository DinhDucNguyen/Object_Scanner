"""Test API endpoint trực tiếp"""
import requests

BASE_URL = "http://192.168.1.84:8000"

print("=" * 60)
print("TEST 1: Health check")
print("=" * 60)
try:
    response = requests.get(f"{BASE_URL}/")
    print(f"✅ Status: {response.status_code}")
    print(f"Response: {response.json()}")
except Exception as e:
    print(f"❌ Error: {e}")

print("\n" + "=" * 60)
print("TEST 2: Scan object đã có trong DB (cell_phone)")
print("=" * 60)
try:
    response = requests.post(
        f"{BASE_URL}/api/scan",
        json={"object_code": "cell_phone", "confidence": 0.9}
    )
    print(f"Status: {response.status_code}")
    if response.status_code == 200:
        data = response.json()
        print(f"✅ Object: {data['object_code']}")
        print(f"✅ Source: {data['source']}")
        print(f"✅ Translations: {len(data['translations'])}")
        if data['translations']:
            print(f"   Vietnamese: {data['translations'][0]['word_name']}")
    else:
        print(f"❌ Error: {response.text}")
except Exception as e:
    print(f"❌ Error: {e}")

print("\n" + "=" * 60)
print("TEST 3: Scan object CHƯA có trong DB (water_bottle)")
print("=" * 60)
try:
    response = requests.post(
        f"{BASE_URL}/api/scan",
        json={"object_code": "water_bottle", "confidence": 0.7}
    )
    print(f"Status: {response.status_code}")
    if response.status_code == 200:
        data = response.json()
        print(f"Object: {data['object_code']}")
        print(f"Source: {data['source']}")
        print(f"Translations: {len(data['translations'])}")
        if data['source'] == 'new_object':
            print(f"⚠️ Object created but NO translations")
            print(f"   → Android app phải gọi Gemini fallback")
    else:
        print(f"❌ Error: {response.text}")
except Exception as e:
    print(f"❌ Error: {e}")

print("\n" + "=" * 60)
print("💡 NẾU Android hiển thị 'Không nhận diện được vật thể':")
print("=" * 60)
print("Nguyên nhân có thể:")
print("1. ML Kit không detect được (confidence < 0.6)")
print("2. Object không có trong 13 ML Kit mappings")
print("3. DB trả empty translations + Gemini fallback fail")
print("4. Network issue (Android không kết nối được backend)")
print("\nKiểm tra:")
print("- Android app có log gì trong Logcat không?")
print("- Đang quét vật thể gì? (laptop/phone/cup/...?)")
print("- Backend server có nhận request không? (check terminal logs)")
