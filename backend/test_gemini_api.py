import requests
import base64

# Read a sample image
with open('test_image.jpg', 'rb') as f:
    image_bytes = f.read()

print(f"Testing Gemini API with image size: {len(image_bytes)} bytes")

# Test scan/image endpoint
response = requests.post('http://192.168.1.84:8000/api/scan/image',
    files={'file': ('test.jpg', image_bytes, 'image/jpeg')}
)

print(f"Status: {response.status_code}")
if response.status_code == 200:
    data = response.json()
    print(f"✅ Gemini scan successful!")
    print(f"   Object: {data['object_code']}")
    print(f"   Source: {data['source']}")
    print(f"   Translations: {len(data['translations'])}")
else:
    print(f"❌ Error: {response.text}")
