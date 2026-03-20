"""
Test Gemini API trực tiếp để debug "unknown" issue
"""
import sys
import os
sys.path.insert(0, os.path.dirname(__file__))

from app.services.gemini_service import GeminiService
from PIL import Image
import io

# Tạo ảnh test đơn giản với text "LAPTOP"
print("=" * 60)
print("🧪 TEST GEMINI API DIRECTLY")
print("=" * 60)

img = Image.new('RGB', (800, 600), (100, 150, 200))
from PIL import ImageDraw
draw = ImageDraw.Draw(img)
draw.rectangle([200, 200, 600, 400], fill=(255, 255, 255))
draw.text((300, 280), "LAPTOP", fill=(0, 0, 0))

# Convert to JPEG bytes
buffer = io.BytesIO()
img.save(buffer, format='JPEG', quality=90)
image_bytes = buffer.getvalue()

print(f"📸 Test image: {len(image_bytes)} bytes")
print()

# Test Gemini service
gemini = GeminiService()

if not gemini.model:
    print("❌ Gemini model not initialized!")
    print("   Check GEMINI_API_KEY in .env file")
    exit(1)

print(f"✅ Gemini model initialized: {gemini.model._model_name}")
print()

print("🔍 Calling Gemini API...")
result = gemini.identify_object(image_bytes)

print()
print("=" * 60)
print("📊 RESULT:")
print("=" * 60)

if result:
    print("✅ SUCCESS!")
    print(f"   object_code: {result.get('object_code', 'N/A')}")
    print(f"   category: {result.get('category', 'N/A')}")
    translations = result.get('translations', [])
    print(f"   translations: {len(translations)} languages")
    if translations:
        for t in translations[:2]:  # Show first 2
            print(f"      - {t.get('lang_code')}: {t.get('word_name')}")
else:
    print("❌ FAILED - returned None")
    print("   Gemini không nhận diện được hoặc API error")
    print("   Check logs above for error details")

print()
print("=" * 60)
