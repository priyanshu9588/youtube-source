#!/usr/bin/env python3
import re

# Read the new script file
with open('/Users/priyanshu/Development/youtube-source/common/src/main/java/dev/lavalink/youtube/cipher/new_script.js', 'r') as f:
    script = f.read()

print("Finding Signature Function in YouTube Player Script")
print("=" * 80)

# Look for the specific functions that have split and join
functions_to_check = ['dG', 'J8', 'eOm', 'j6', 't0', 'wMZ', 'jxZ', 'T']

for func_name in functions_to_check:
    # Find the function definition
    # Try different patterns
    patterns = [
        rf'{re.escape(func_name)}=function\(([^)]+)\)\{{([^}}]*(?:\{{[^}}]*\}}[^}}]*)*)\}}',
        rf'\b{re.escape(func_name)}=function\(([^)]+)\)\{{[^}}]{{0,2000}}\}}',
    ]
    
    for pattern in patterns:
        func_match = re.search(pattern, script)
        if func_match:
            params = func_match.group(1)
            body = func_match.group(2) if len(func_match.groups()) > 1 else func_match.group(0)
            
            # Check if this looks like a signature function
            has_split = 'split(""' in body or "split('')" in body
            has_join = 'join(""' in body or "join('')" in body
            
            if has_split and has_join:
                print(f"\n✓ Found potential signature function: {func_name}({params})")
                print(f"  Has split: {has_split}")
                print(f"  Has join: {has_join}")
                
                # Look for transformation object calls
                obj_calls = re.findall(r'([a-zA-Z_$][a-zA-Z_$0-9]{0,2})\.[a-zA-Z_$][a-zA-Z_$0-9]{0,2}\(', body[:500])
                if obj_calls:
                    unique_objs = list(set(obj_calls))
                    print(f"  Calls methods on objects: {unique_objs}")
                    
                    # Try to find the transformation object
                    for obj in unique_objs:
                        # Search for the object definition
                        obj_pattern = rf'(?:var )?{re.escape(obj)}=\{{([^}}]+)\}}'
                        obj_match = re.search(obj_pattern, script)
                        if obj_match:
                            obj_body = obj_match.group(1)
                            if 'splice' in obj_body or 'reverse' in obj_body:
                                print(f"  ✓ Found transformation object: {obj}")
                                # Count methods
                                methods = re.findall(r'([a-zA-Z_$][a-zA-Z_$0-9]{0,2}):function', obj_body)
                                print(f"    Methods: {methods[:5]}")
                                break
                
                # Show a sample of the function body
                print(f"\n  Function body preview:")
                print(f"  {body[:300]}...")
                break
            elif has_split or has_join:
                print(f"\nPartial match: {func_name}({params}) - split={has_split}, join={has_join}")

print("\n" + "=" * 80)

# Also search for the exact pattern that the current regex expects
print("\nChecking current regex pattern from SignatureCipherManager:")

# The pattern from SignatureCipherManager.java
current_pattern = r'function(?:\s+[a-zA-Z_$][a-zA-Z_0-9$]*)?\(([a-zA-Z_$][a-zA-Z_0-9$]*)\)\{' + \
                  r'[a-zA-Z_$][a-zA-Z_0-9$]*=[a-zA-Z_$][a-zA-Z_0-9$]*.*?\(\1,\d+\);return\s*\1.*};'

matches = re.findall(current_pattern, script)
if matches:
    print(f"Current pattern matches: {matches}")
else:
    print("Current pattern does NOT match anything!")
    
    # Try a modified version
    modified_pattern = r'([a-zA-Z_$][a-zA-Z_$0-9]*)=function\(([a-zA-Z])\)\{[^}]*\(\2,\d+\)[^}]*return[^}]*\2[^}]*\}'
    matches = re.findall(modified_pattern, script)
    if matches:
        print(f"\nModified pattern matches: {len(matches)} functions")
        for func_name, param in matches[:3]:
            print(f"  - {func_name}({param})")