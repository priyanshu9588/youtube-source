#!/usr/bin/env python3
import re

with open('/var/folders/5z/1cdjzwb9515f_ft40n2thyq40000gn/T/lavaplayer-yt-player-script11404048700466387682.js', 'r') as f:
    content = f.read()

# Find the Co object
match = re.search(r'Co=\{', content)
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
                co_obj = content[start:i+1]
                print("Found Co object:")
                print(co_obj)
                
                # Extract the methods
                methods = re.findall(r'(\w+):function\([^)]*\)\{([^}]*)\}', co_obj)
                print(f"\nCo has {len(methods)} methods:")
                for name, body in methods:
                    print(f"  Co.{name}: {body}")
                    
                break
        i += 1

# Find what Y indices are used
y_match = re.search(r'var Y="([^"]+)"\.split\("([^"]+)"\)', content)
if y_match:
    y_content = y_match.group(1)
    delimiter = y_match.group(2)
    y_array = y_content.split(delimiter)
    
    # Map the Y indices used in Co
    print("\nY array mappings used in Co:")
    print(f"Y[11] = '{y_array[11]}'")
    print(f"Y[30] = '{y_array[30]}'")
    print(f"Y[37] = '{y_array[37]}'")