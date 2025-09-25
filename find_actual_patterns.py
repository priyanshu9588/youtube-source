#!/usr/bin/env python3
import re
import json

# Read the new script file
with open('/Users/priyanshu/Development/youtube-source/common/src/main/java/dev/lavalink/youtube/cipher/new_script.js', 'r') as f:
    script = f.read()

print("Finding Actual Patterns in YouTube Player Script")
print("=" * 80)

# Based on yt-dlp's approach, the signature function:
# 1. Takes a single parameter
# 2. Calls some transformation function with (param, number)
# 3. Returns the parameter

# Search for functions that match: func(a){...something(a,number)...return a}
pattern = r'([a-zA-Z_$][a-zA-Z_$0-9]{0,2})=function\(([a-z])\)\{[^}]*\(\2,\d+\)[^}]*return[^}]*\2[^}]*\}'
matches = re.findall(pattern, script)

if matches:
    print(f"\nFound {len(matches)} functions with pattern func(a){{...f(a,num)...return a}}:")
    for func_name, param in matches[:10]:
        print(f"  - {func_name}({param})")
        
    # For the first match, extract the full function
    if matches:
        func_name, param = matches[0]
        func_pattern = rf'{re.escape(func_name)}=function\([^)]+\)\{{([^}}]+(?:\{{[^}}]*\}}[^}}]*)*)\}}'
        func_match = re.search(func_pattern, script)
        if func_match:
            body = func_match.group(1)
            print(f"\nFirst function body ({func_name}):")
            print(body[:500])
            
            # Find what function is called with (param, number)
            call_pattern = rf'([a-zA-Z_$][a-zA-Z_$0-9]{{0,2}})\({param},\d+\)'
            calls = re.findall(call_pattern, body)
            if calls:
                print(f"\nCalls function: {calls[0]}")
                
                # Now find that function
                helper_func = calls[0]
                helper_pattern = rf'{re.escape(helper_func)}=function\([^)]+\)\{{([^}}]+(?:\{{[^}}]*\}}[^}}]*)*)\}}'
                helper_match = re.search(helper_pattern, script)
                if helper_match:
                    helper_body = helper_match.group(1)
                    print(f"\nHelper function {helper_func} body:")
                    print(helper_body[:500])
                    
                    # Check what this function calls
                    obj_calls = re.findall(r'([a-zA-Z_$][a-zA-Z_$0-9]{0,2})\.[a-zA-Z_$][a-zA-Z_$0-9]{0,2}\(', helper_body)
                    if obj_calls:
                        unique = list(set(obj_calls))
                        print(f"\nHelper function calls methods on: {unique}")
                        
                        # Find that object
                        for obj in unique:
                            obj_pattern = rf'(?:var )?{re.escape(obj)}=\{{([^}}]+(?:\{{[^}}]*\}}[^}}]*)*)\}}'
                            obj_match = re.search(obj_pattern, script)
                            if obj_match:
                                obj_body = obj_match.group(1)
                                if 'splice' in obj_body or 'reverse' in obj_body:
                                    print(f"\n✓ Found transformation object: {obj}")
                                    # Extract methods
                                    methods = re.findall(r'([a-zA-Z_$][a-zA-Z_$0-9]{0,2}):function', obj_body)
                                    print(f"  Methods: {methods[:10]}")
                                    
                                    # Check the actual transformations
                                    has_reverse = 'reverse' in obj_body
                                    has_splice = 'splice' in obj_body  
                                    has_swap = 'var' in obj_body and '=' in obj_body
                                    print(f"  Operations: reverse={has_reverse}, splice={has_splice}, swap-like={has_swap}")
                                    break

print("\n" + "=" * 80)
print("Looking for n-function patterns...")

# n-function typically has try-catch and returns something with the parameter
patterns = [
    # Standard try-catch pattern
    r'([a-zA-Z_$][a-zA-Z_$0-9]{0,3})=function\(([a-z])\)\{[^}]*try\{[^}]*\}[^}]*catch[^}]*\{[^}]*\2[^}]*\}[^}]*\}',
    # With "enhanced_except" pattern
    r'function\(([a-z])\)\{[^}]*catch[^}]*"enhanced_except[^}]*\+[^}]*\1[^}]*\}',
]

for i, pattern in enumerate(patterns):
    matches = re.findall(pattern, script)
    if matches:
        print(f"\nPattern {i+1} found {len(matches)} n-functions")
        for match in matches[:3]:
            if isinstance(match, tuple):
                func_name = match[0] if len(match) > 1 else 'anonymous'
                param = match[-1]
            else:
                func_name = 'anonymous'
                param = match
            print(f"  - {func_name}({param})")

print("\n" + "=" * 80)
print("Summary of findings to update SignatureCipherManager.java:")
print("1. Signature function pattern: func(a){...helper(a,number)...return a}")
print("2. The helper function then calls methods on a transformation object")
print("3. The transformation object has methods like reverse, splice, etc.")
print("4. Need to extract all three components: sig function, helper, and transform object")