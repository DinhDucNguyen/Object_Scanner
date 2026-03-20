"""Test Gemini endpoint với ảnh thực tế"""
import requests
from PIL import Image
import io

BASE_URL = "http://192.168.1.84:8000"

print("=" * 70)
print("TEST GEMINI API ENDPOINT: /api/scan/image")
print("=" * 70)

# Tạo ảnh test đơn giản (hoặc dùng ảnh thực nếu có)
print("\n1. Tạo ảnh test (hoặc chụp ảnh thực tế bằng Android)")
print("   Endpoint này nhận multipart/form-data với field 'file'")
print()

# Giả lập flow từ Android
print("2. Android gửi request:")
print("   POST /api/scan/image")
print("   Content-Type: multipart/form-data")
print("   Body: file=<image_bytes>")
print()

print("3. Backend flow:")
print("   ├─ Nhận ảnh")
print("   ├─ Gọi Gemini Vision API")
print("   ├─ Gemini nhận diện vật thể + dịch tiếng Việt")
print("   ├─ Lưu vào DB nếu chưa có")
print("   └─ Trả ScanResponse với translations")
print()

print("=" * 70)
print("💡 ĐỂ TEST GEMINI:")
print("=" * 70)
print("1. Từ Android app:")
print("   - Khi ML Kit fail → gọi repository.scanByImage(imageBytes)")
print("   - Repository gọi API: POST /api/scan/image với file")
print()
print("2. Test thủ công bằng curl:")
print("   curl -X POST http://192.168.1.84:8000/api/scan/image \\")
print("        -F 'file=@test_image.jpg'")
print()
print("3. Gemini sẽ nhận diện MỌI vật thể (không giới hạn 13 objects)")
print("   - Car, motorcycle, guitar, piano, banana, apple...")
print("   - Và dịch sang tiếng Việt")
print()

print("=" * 70)
print("KIỂM TRA ENDPOINT HIỆN TẠI:")
print("=" * 70)

try:
    # Check endpoint có tồn tại không
    response = requests.get(f"{BASE_URL}/docs")
    if response.status_code == 200:
        print("✅ Backend running")
        print("✅ OpenAPI docs: http://192.168.1.84:8000/docs")
        print("✅ Endpoint: POST /api/scan/image")
        print()
        print("📱 Android cần gọi:")
        print("   val result = apiService.scanByImage(imageFile)")
    else:
        print("❌ Backend không truy cập được")
except Exception as e:
    print(f"❌ Error: {e}")

print()
print("=" * 70)
print("QUAN TRỌNG:")
print("=" * 70)
print("Gemini API hiện có quota:")
print("  - Free tier: 15 RPM (requests/phút), 1,500 RPD (requests/ngày)")
print("  - Vừa test thấy còn quota")
print()
print("Nếu quét nhiều → nên cache kết quả trong DB để lần sau không phải gọi lại")
