"""Kiểm tra Gemini API quota và model info"""
import google.generativeai as genai
import os
from dotenv import load_dotenv

load_dotenv()
genai.configure(api_key=os.getenv("GEMINI_API_KEY"))

print("=" * 60)
print("GEMINI API STATUS CHECK")
print("=" * 60)

# Test simple request
try:
    model = genai.GenerativeModel("gemini-2.5-flash")
    print(f"✅ Model: gemini-2.5-flash")
    print(f"🔑 API Key: {os.getenv('GEMINI_API_KEY')[:20]}...")
    
    # Test generate với text đơn giản
    response = model.generate_content("Hello, what's 2+2?")
    print(f"✅ API Working: {response.text[:50]}")
    print(f"📊 Quota: Still available (request successful)")
    
except Exception as e:
    print(f"❌ Error: {str(e)}")
    if "429" in str(e) or "quota" in str(e).lower():
        print(f"⚠️ QUOTA EXHAUSTED - Free tier limits:")
        print(f"   - 15 requests per minute (RPM)")
        print(f"   - 1,500 requests per day (RPD)")
        print(f"   - Resets every day at midnight UTC")
    elif "API_KEY_INVALID" in str(e):
        print(f"⚠️ API Key invalid or expired")

print("=" * 60)
print("\n💡 To use Gemini Pro (paid):")
print("1. Create Google Cloud project")
print("2. Enable Generative AI API")  
print("3. Enable billing")
print("4. Get API key from Cloud Console (not AI Studio)")
print("5. Update .env with new key")
