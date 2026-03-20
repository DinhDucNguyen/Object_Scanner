import requests
import json

# Test scan endpoint
print("Testing /api/scan endpoint...")
response = requests.post('http://192.168.1.84:8000/api/scan', 
    json={
        'object_code': 'cell_phone',
        'confidence': 0.95
    },
    headers={'Content-Type': 'application/json'}
)

print(f"Status: {response.status_code}")
if response.status_code == 200:
    data = response.json()
    print(f"✅ Success!")
    print(f"   Object: {data['object_code']}")
    print(f"   Source: {data['source']}")
    print(f"   Translations: {len(data['translations'])}")
    if data['translations']:
        t = data['translations'][0]
        print(f"   First translation: {t['word_name']} ({t['language_code']})")
else:
    print(f"❌ Error: {response.text}")
