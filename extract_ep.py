#!/usr/bin/env python3
import re

with open('/var/folders/5z/1cdjzwb9515f_ft40n2thyq40000gn/T/lavaplayer-yt-player-script11404048700466387682.js', 'r') as f:
    content = f.read()

# Find the EP function
match = re.search(r'EP=function\([^)]+\)\{', content)
if match:
    start = match.start()
    brace_count = 0
    i = match.end() - 1
    
    while i < len(content):
        if content[i] == '{':
            brace_count += 1
        elif content[i] == '}':
            brace_count -= 1
            if brace_count == 0:
                func = content[start:i+1]
                print("Found EP function:")
                print(func[:500])
                print("...")
                print(func[-500:])
                print(f"\nTotal length: {len(func)} characters")
                
                # Look for what EP calls
                if 'EP(' in func:
                    print("\nEP seems to call itself recursively")
                
                # Look for any helper calls
                helpers = re.findall(r'\b([A-Z][A-Z0-9]{1,2})\s*\(', func)
                if helpers:
                    print(f"\nHelper functions called: {set(helpers)}")
                    
                break
        i += 1

# Also find where EP is called with signature
sig_calls = re.findall(r'EP\((\d+),\s*decodeURIComponent\([^)]+\.s\)\)', content)
if sig_calls:
    print(f"\nEP is called with first parameter: {sig_calls[0]} for signature transformation")

# Find the Y array since EP uses Y[...]
y_match = re.search(r'var Y="([^"]+)"\.split\("([^"]+)"\)', content)
if y_match:
    y_content = y_match.group(1)
    delimiter = y_match.group(2)
    y_array = y_content.split(delimiter)
    print(f"\nY array has {len(y_array)} elements")
    print(f"Y[7] = '{y_array[7]}'")
    print(f"Y[21] = '{y_array[21]}'")
    print(f"Y[24] = '{y_array[24]}'")
    print(f"Y[34] = '{y_array[34]}'")