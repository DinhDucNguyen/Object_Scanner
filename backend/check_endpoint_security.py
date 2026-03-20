import requests
import json

# Check /api/scan endpoint details
response = requests.get('http://localhost:8000/openapi.json')
if response.status_code == 200:
    data = response.json()
    scan_endpoint = data['paths'].get('/api/scan', {})
    print("=== /api/scan endpoint ===")
    print(json.dumps(scan_endpoint, indent=2))
    
    # Check if it has security requirements
    if 'post' in scan_endpoint:
        post_def = scan_endpoint['post']
        if 'security' in post_def:
            print("\n⚠️ Security requirements found:")
            print(json.dumps(post_def['security'], indent=2))
        else:
            print("\n✅ No security requirements")
