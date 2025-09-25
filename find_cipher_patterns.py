#!/usr/bin/env python3
import re
import sys

# Read the new script file
with open('/Users/priyanshu/Development/youtube-source/common/src/main/java/dev/lavalink/youtube/cipher/new_script.js', 'r') as f:
    script = f.read()

print("YouTube Player Script Cipher Pattern Finder")
print("=" * 80)

# First, let's find functions that manipulate arrays
print("\n1. Finding functions that call .split('') and .join('')...")

# Look for functions that split and join strings
pattern = r'([a-zA-Z_$][a-zA-Z_$0-9]{0,2})=function\(([a-zA-Z_$][a-zA-Z_$0-9]*)\)\{[^}]*\.split\(["\']["\']\)[^}]*\.join\(["\']["\']\)[^}]*\}'
matches = re.findall(pattern, script)

if matches:
    print(f"Found {len(matches)} potential signature functions:")
    for func_name, param in matches[:5]:
        print(f"  - {func_name}({param})")
        # Get the full function body
        func_pattern = rf'{re.escape(func_name)}=function\([^)]*\)\{{([^}}]*(?:\{{[^}}]*\}}[^}}]*)*)\}}'
        func_match = re.search(func_pattern, script)
        if func_match:
            body = func_match.group(1)
            # Look for object method calls
            obj_calls = re.findall(r'([a-zA-Z_$][a-zA-Z_$0-9]{0,2})\.[a-zA-Z_$][a-zA-Z_$0-9]{0,2}\(', body)
            if obj_calls:
                unique_objs = list(set(obj_calls))
                print(f"    Calls methods on: {unique_objs[0]}")

print("\n2. Finding transformation helper objects...")

# Look for objects with splice, reverse, or swap operations
pattern = r'var\s+([a-zA-Z_$][a-zA-Z_$0-9]{0,2})=\{[^}]*(splice|reverse)[^}]*\}'
matches = re.findall(pattern, script)

if matches:
    print(f"Found {len(matches)} objects with array operations:")
    for obj_name, op in matches[:5]:
        print(f"  - {obj_name} (contains {op})")
        # Get the full object
        obj_pattern = rf'var\s+{re.escape(obj_name)}=\{{([^}}]+)\}}'
        obj_match = re.search(obj_pattern, script)
        if obj_match:
            content = obj_match.group(1)
            methods = re.findall(r'([a-zA-Z_$][a-zA-Z_$0-9]{0,2}):function', content)
            print(f"    Methods: {methods[:5]}")

print("\n3. Looking for n-transform patterns...")

# Find functions with try-catch that return something with the parameter
pattern = r'([a-zA-Z_$][a-zA-Z_$0-9]{0,2})=function\(([a-zA-Z_$][a-zA-Z_$0-9]*)\)\{[^}]*try\{[^}]*\}catch[^}]*\{[^}]*\2[^}]*\}[^}]*\}'
matches = re.findall(pattern, script)

if matches:
    print(f"Found {len(matches)} functions with try-catch:")
    for func_name, param in matches[:5]:
        print(f"  - {func_name}({param})")

print("\n4. Searching for specific cipher patterns...")

# Look for the signature cipher pattern more broadly
# It should have a.split(""), some operations, and a.join("")
pattern = r'([a-zA-Z_$][a-zA-Z_$0-9]{0,2})=function\(([a-zA-Z])\)\{\2=\2\.split\(""\);([^}]+);return \2\.join\(""\)\}'
matches = re.findall(pattern, script)

if matches:
    print(f"Found signature cipher functions:")
    for func_name, param, operations in matches:
        print(f"  - {func_name}({param})")
        print(f"    Operations: {operations[:100]}...")
        # Find what object is referenced
        obj_refs = re.findall(r'([a-zA-Z_$][a-zA-Z_$0-9]{0,2})\.[a-zA-Z_$]', operations)
        if obj_refs:
            print(f"    Uses object: {obj_refs[0]}")

print("\n5. Looking for timestamp patterns...")

# Search for signatureTimestamp or sts with various formats
patterns = [
    r'signatureTimestamp[":]*\s*([0-9]+)',
    r'sts[":]*\s*([0-9]+)',
    r'"sts"\s*:\s*([0-9]+)',
    r'sts\s*=\s*([0-9]+)',
    r'STS[":]*\s*([0-9]+)'
]

for pattern in patterns:
    matches = re.findall(pattern, script)
    if matches:
        print(f"Found timestamp: {matches[0]}")
        break
else:
    # Try to find any 5-digit number that could be a timestamp
    print("No standard timestamp pattern found, searching for 5-digit numbers...")
    five_digit = re.findall(r'\b([12][0-9]{4})\b', script)
    if five_digit:
        # Get unique values
        unique = list(set(five_digit))
        print(f"Potential timestamps: {unique[:5]}")

print("\n6. Analyzing script structure...")

# Count occurrences of key operations
reverse_count = script.count('.reverse()')
splice_count = script.count('.splice(')
split_count = script.count('.split("")')
join_count = script.count('.join("")')

print(f"  - .reverse() calls: {reverse_count}")
print(f"  - .splice() calls: {splice_count}")
print(f"  - .split('') calls: {split_count}")
print(f"  - .join('') calls: {join_count}")

print("\n" + "=" * 80)
print("Analysis complete!")