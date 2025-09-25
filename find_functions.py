#!/usr/bin/env python3
import re
import urllib.request

# Download the script
url = "https://www.youtube.com/s/player/377ca75b/player_ias.vflset/en_GB/base.js"
with urllib.request.urlopen(url) as response:
    js = response.read().decode('utf-8')

print(f"Downloaded script, size: {len(js)}")

# Find signature function (based on pytubefix patterns)
patterns = [
    r'\b[cs]\s*&&\s*[adf]\.set\([^,]+\s*,\s*encodeURIComponent\s*\(\s*([a-zA-Z0-9$]+)\(',
    r'\b[a-zA-Z0-9]+\s*&&\s*[a-zA-Z0-9]+\.set\([^,]+\s*,\s*encodeURIComponent\s*\(\s*([a-zA-Z0-9$]+)\(',
    r'\bm=([a-zA-Z0-9$]{2,})\(decodeURIComponent\(h\.s\)\)',
    r'\bc&&\(c=([a-zA-Z0-9$]{2,})\(decodeURIComponent\(c\)\)',
    r'(?:\b|[^a-zA-Z0-9$])([a-zA-Z0-9$]{2,})\s*=\s*function\(\s*a\s*\)\s*\{\s*a\s*=\s*a\.split\(\s*""\s*\)',
    r'([a-zA-Z0-9$]+)\s*=\s*function\(\s*a\s*\)\s*\{\s*a\s*=\s*a\.split\(\s*""\s*\)',
]

sig_func = None
for pattern in patterns:
    match = re.search(pattern, js)
    if match:
        sig_func = match.group(1)
        print(f"Found signature function: {sig_func} with pattern: {pattern[:50]}...")
        break

if not sig_func:
    # Try simpler pattern
    match = re.search(r'encodeURIComponent\(([a-zA-Z0-9$_]{2,})\(', js)
    if match:
        sig_func = match.group(1)
        # Verify it's actually a function
        if f"{sig_func}=function" in js or f"function {sig_func}" in js:
            print(f"Found signature function (simple): {sig_func}")

# Find n-transform function
# Look for functions with specific characteristics
n_func = None

# Try to find based on error messages or specific patterns
patterns = [
    r'([a-zA-Z0-9$]+)\s*=\s*function\(a\)\{[^}]*"enhanced_except_[^}]*\}',
    r'([a-zA-Z0-9$]+)\s*=\s*function\(a\)\{[^}]*b\[0\]="enhanced_except[^}]*\}',
]

for pattern in patterns:
    match = re.search(pattern, js, re.DOTALL)
    if match:
        n_func = match.group(1)
        print(f"Found n-transform function: {n_func}")
        break

if not n_func:
    # Try to find by looking for array split pattern and specific structure
    # This is a heuristic based on common patterns
    pattern = r'\b([a-zA-Z0-9$]+)\s*=\s*function\(a\)\{[^}]*var\s+b=a\.split\([^)]*\)[^}]*return[^}]+\}'
    matches = re.findall(pattern, js[:500000])  # Look in first 500k chars
    if matches:
        # Pick the first one that looks right
        for m in matches:
            if f"{m}=function" in js:
                n_func = m
                print(f"Found potential n-transform function: {n_func}")
                break

if not n_func:
    # Last resort - look for any function that has characteristic patterns
    print("Searching for n-transform with broader patterns...")
    # Look for functions that manipulate arrays and have try-catch
    pattern = r'([a-zA-Z0-9$]+)\s*=\s*function\([^)]+\)\{[^}]*try\{[^}]*\}catch[^}]*\}'
    matches = re.findall(pattern, js[:500000], re.DOTALL)
    print(f"Found {len(matches)} functions with try-catch")
    if matches:
        print(f"First few: {matches[:5]}")

print(f"\nFinal results:")
print(f"  Signature function: {sig_func}")
print(f"  N-transform function: {n_func}")