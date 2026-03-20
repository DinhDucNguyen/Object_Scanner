import requests

# Check all routes
response = requests.get('http://localhost:8000/openapi.json')
if response.status_code == 200:
    data = response.json()
    print("Available routes:")
    for path, methods in data['paths'].items():
        for method in methods.keys():
            print(f"  {method.upper()} {path}")
