import requests

# Test scan by code
response = requests.post('http://localhost:8000/api/scan', json={
    'object_code': 'laptop',
    'confidence': 0.95
})

print(f"Status: {response.status_code}")
if response.status_code == 200:
    data = response.json()
    print(f"\n✅ Scan successful!")
    print(f"   Object code: {data['object_code']}")
    print(f"   Translations: {len(data['translations'])}")
    if data['translations']:
        t = data['translations'][0]
        print(f"   First translation:")
        print(f"      - word_name: {t['word_name']}")
        print(f"      - phonetic: {t['phonetic']}")
        print(f"      - language_code: {t['language_code']}")
else:
    print(f"Error: {response.text}")
