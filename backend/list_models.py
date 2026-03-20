"""List available Gemini models"""
import os
from dotenv import load_dotenv
import google.generativeai as genai

load_dotenv()
api_key = os.getenv("GEMINI_API_KEY")

if not api_key:
    print("❌ GEMINI_API_KEY not found in .env")
    exit(1)

genai.configure(api_key=api_key)

print("=" * 60)
print("📋 AVAILABLE GEMINI MODELS")
print("=" * 60)

try:
    models = genai.list_models()
    vision_models = [m for m in models if 'generateContent' in m.supported_generation_methods]
    
    print(f"\n✅ Found {len(vision_models)} models with generateContent support:\n")
    
    for m in vision_models:
        print(f"  • {m.name}")
        if hasattr(m, 'display_name'):
            print(f"    Display: {m.display_name}")
        if hasattr(m, 'description'):
            desc = m.description[:80] + "..." if len(m.description) > 80 else m.description
            print(f"    Info: {desc}")
        print()
        
except Exception as e:
    print(f"❌ Error listing models: {e}")
    
print("=" * 60)
print("💡 RECOMMENDED: Use 'gemini-1.5-flash' (fast, free tier)")
print("=" * 60)
