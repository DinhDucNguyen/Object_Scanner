"""
Test script để verify scan API với ảnh thật
"""
import httpx
from PIL import Image
import io

# Tạo ảnh test (màu xanh lá với chữ "LAPTOP")
img = Image.new('RGB', (800, 600), (50, 200, 50))
from PIL import ImageDraw, ImageFont
draw = ImageDraw.Draw(img)
try:
    font = ImageFont.truetype("arial.ttf", 60)
except:
    font = ImageFont.load_default()
draw.text((250, 250), "LAPTOP", fill=(255, 255, 255), font=font)

# Convert sang bytes
buffer = io.BytesIO()
img.save(buffer, format='JPEG', quality=90)
image_bytes = buffer.getvalue()

print(f"📸 Test image: {len(image_bytes)} bytes")

# Login để lấy token
with httpx.Client(timeout=60.0) as client:  # Tăng timeout lên 60s cho Gemini
    login_response = client.post(
        "http://192.168.1.84:8000/api/auth/login",
        json={"username": "duc", "password": "123456"}
    )

    if login_response.status_code != 200:
        print(f"❌ Login failed: {login_response.status_code}")
        print(login_response.text)
        exit(1)

    token = login_response.json()["access_token"]
    print(f"✅ Login OK, token: {token[:20]}...")

    # Gọi scan API
    files = {'file': ('test.jpg', image_bytes, 'image/jpeg')}
    headers = {'Authorization': f'Bearer {token}'}

    print("\n🔍 Calling /api/scan/image...")
    scan_response = client.post(
        "http://192.168.1.84:8000/api/scan/image",
        files=files,
        headers=headers
    )

    print(f"\n📊 Response status: {scan_response.status_code}")
    print(f"📊 Response body:")
    print(scan_response.text[:500])

    if scan_response.status_code == 200:
        data = scan_response.json()
        print(f"\n✅ Success!")
        print(f"   - object_code: {data.get('object_code')}")
        print(f"   - source: {data.get('source')}")
        print(f"   - translations: {len(data.get('translations', []))}")
    else:
        print(f"\n❌ Failed: {scan_response.status_code}")
