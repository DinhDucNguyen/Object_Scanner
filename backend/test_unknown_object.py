import requests

# Test với một object không có trong DB
print("Testing scan with unknown object 'coffee_cup'...")
response = requests.post('http://192.168.1.84:8000/api/scan', 
    json={
        'object_code': 'coffee_cup',  # Object không có trong DB
        'confidence': 0.95
    },
    headers={'Content-Type': 'application/json'}
)

print(f"Status: {response.status_code}")
if response.status_code == 200:
    data = response.json()
    print(f"✅ Response:")
    print(f"   Object: {data['object_code']}")
    print(f"   Source: {data['source']}")
    print(f"   Translations: {len(data['translations'])}")
    if len(data['translations']) == 0:
        print("   ⚠️ No translations - should fallback to Gemini in Android app")
else:
    print(f"❌ Error: {response.text}")
