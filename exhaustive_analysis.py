#!/usr/bin/env python3
import re

# Read the new script file
with open('/Users/priyanshu/Development/youtube-source/common/src/main/java/dev/lavalink/youtube/cipher/new_script.js', 'r') as f:
    script = f.read()

print("Exhaustive Pattern Analysis for YouTube Player Script")
print("=" * 80)

# Find the timestamp first
ts_patterns = [
    r'signatureTimestamp[":]*\s*([0-9]+)',
    r'sts[":]*\s*([0-9]+)',
    r'"sts"\s*:\s*([0-9]+)',
]

timestamp = None
for pattern in ts_patterns:
    matches = re.findall(pattern, script)
    if matches:
        timestamp = matches[0]
        print(f"Found timestamp: {timestamp}")
        break

print("\n" + "=" * 80)
print("SIGNATURE FUNCTION SEARCH:")
print("=" * 80)

# Try multiple patterns for signature function
sig_patterns = [
    # Classic pattern: func(a){a=a.split("");...;return a.join("")}
    (r'([a-zA-Z_$][a-zA-Z_$0-9]*)=function\(([a-zA-Z])\)\{\2=\2\.split\(""\);[^}]+;return \2\.join\(""\)\}', "Classic"),
    # Without space after function
    (r'([a-zA-Z_$][a-zA-Z_$0-9]*)=function\(([a-zA-Z])\)\{\2=\2\.split\(""\)[^}]+return \2\.join\(""\)\}', "No semicolon"),
    # With var declaration
    (r'var ([a-zA-Z_$][a-zA-Z_$0-9]*)=function\(([a-zA-Z])\)\{\2=\2\.split\(""\)[^}]+\}', "With var"),
    # More flexible
    (r'([a-zA-Z_$][a-zA-Z_$0-9]{1,3})=function\(([a-zA-Z])\)\{[^}]*\2\.split\(""\)[^}]*\2\.join\(""\)[^}]*\}', "Flexible"),
    # Very short function names
    (r'([a-zA-Z][a-zA-Z0-9]?)=function\(([a-zA-Z])\)\{\2=\2\.split\(""\)[^}]+\}', "Short names"),
]

found_sig_func = None
for pattern, desc in sig_patterns:
    matches = re.findall(pattern, script)
    if matches:
        print(f"\n{desc} pattern found {len(matches)} matches:")
        for func_name, param in matches[:3]:
            print(f"  Function: {func_name}({param})")
            # Get function body
            func_pattern = rf'{re.escape(func_name)}=function\([^)]*\)\{{([^}}]*(?:\{{[^}}]*\}}[^}}]*)*)\}}'
            func_match = re.search(func_pattern, script)
            if func_match:
                body = func_match.group(1)[:150]
                print(f"    Body: {body}...")
                # Find object references
                obj_refs = re.findall(r'([a-zA-Z_$][a-zA-Z_$0-9]{0,2})\.[a-zA-Z]', body)
                if obj_refs:
                    unique = list(set(obj_refs))
                    print(f"    Uses objects: {unique[:3]}")
                    if not found_sig_func:
                        found_sig_func = (func_name, param, unique[0] if unique else None)

if not found_sig_func:
    # Try to find any function that splits and joins
    print("\nSearching for ANY function with split/join pattern...")
    # Very loose pattern
    pattern = r'([a-zA-Z_$][a-zA-Z_$0-9]{0,3})=function\([^)]*\)\{[^}]*split[^}]*join[^}]*\}'
    matches = re.findall(pattern, script)
    if matches:
        print(f"Found {len(matches)} functions with split/join:")
        for func in matches[:10]:
            print(f"  - {func}")

print("\n" + "=" * 80)
print("TRANSFORMATION OBJECT SEARCH:")
print("=" * 80)

# Find objects with transformation methods
obj_patterns = [
    # Standard object pattern
    (r'var ([a-zA-Z_$][a-zA-Z_$0-9]{0,2})=\{[^}]*(?:splice|reverse)[^}]*\}', "Standard"),
    # Without var
    (r'([a-zA-Z_$][a-zA-Z_$0-9]{0,2})=\{[^}]*(?:splice|reverse)[^}]*\}', "No var"),
    # With multiple methods
    (r'([a-zA-Z_$][a-zA-Z_$0-9]{0,2})=\{(?:[a-zA-Z_$][a-zA-Z_$0-9]*:function[^}]+,?\s*){2,}\}', "Multiple methods"),
]

found_obj = None
for pattern, desc in obj_patterns:
    matches = re.findall(pattern, script)
    if matches:
        print(f"\n{desc} pattern found {len(matches)} matches:")
        for obj_name in matches[:5]:
            if isinstance(obj_name, tuple):
                obj_name = obj_name[0]
            # Get the full object
            obj_pattern = rf'(?:var )?{re.escape(obj_name)}=\{{([^}}]+(?:\{{[^}}]*\}}[^}}]*)*)\}}'
            obj_match = re.search(obj_pattern, script)
            if obj_match:
                content = obj_match.group(1)
                # Check for transformation operations
                has_splice = 'splice' in content
                has_reverse = 'reverse' in content
                method_count = content.count('function')
                if has_splice or has_reverse:
                    print(f"  Object: {obj_name}")
                    print(f"    Methods: {method_count}, splice={has_splice}, reverse={has_reverse}")
                    # Extract method names
                    methods = re.findall(r'([a-zA-Z_$][a-zA-Z_$0-9]{0,2}):function', content[:500])
                    if methods:
                        print(f"    Method names: {methods[:5]}")
                    if not found_obj and method_count >= 2:
                        found_obj = obj_name

print("\n" + "=" * 80)
print("N-FUNCTION SEARCH:")
print("=" * 80)

# Search for n-function patterns
n_patterns = [
    # With try-catch
    (r'([a-zA-Z_$][a-zA-Z_$0-9]{0,3})=function\(([a-zA-Z])\)\{[^}]*try[^}]*catch[^}]*\2[^}]*\}', "Try-catch"),
    # With specific error handling
    (r'function\(([a-zA-Z])\)\{[^}]*catch\([^)]*\)\{[^}]*"enhanced_except[^}]*\+[^}]*\1[^}]*\}[^}]*\}', "Enhanced except"),
    # General pattern
    (r'([a-zA-Z_$][a-zA-Z_$0-9]{0,3})=function\(([a-zA-Z])\)\{var [^}]+catch[^}]+return[^}]+\}', "General"),
]

found_n_func = None
for pattern, desc in n_patterns:
    matches = re.findall(pattern, script)
    if matches:
        print(f"\n{desc} pattern found {len(matches)} matches:")
        for match in matches[:3]:
            if isinstance(match, tuple):
                func_name = match[0] if len(match) > 1 else "anonymous"
                param = match[-1]
            else:
                func_name = "anonymous"
                param = match
            print(f"  Function: {func_name}({param})")
            if not found_n_func:
                found_n_func = (func_name, param)

print("\n" + "=" * 80)
print("SUMMARY:")
print("=" * 80)
print(f"Timestamp: {timestamp or 'NOT FOUND'}")
if found_sig_func:
    print(f"Signature function: {found_sig_func[0]}({found_sig_func[1]}) - uses object: {found_sig_func[2]}")
else:
    print("Signature function: NOT FOUND")
if found_obj:
    print(f"Transformation object: {found_obj}")
else:
    print("Transformation object: NOT FOUND")
if found_n_func:
    print(f"N-function: {found_n_func[0]}({found_n_func[1]})")
else:
    print("N-function: NOT FOUND")

print("\n" + "=" * 80)
print("RECOMMENDATIONS:")
print("=" * 80)
if not found_sig_func:
    print("⚠️ Signature function pattern has changed significantly!")
    print("  - Check if function structure changed (e.g., no explicit split/join)")
    print("  - Look for indirect function calls or new obfuscation")
if not found_obj:
    print("⚠️ Transformation object pattern has changed!")
    print("  - Object might be defined differently or methods are obfuscated")
if not found_n_func:
    print("⚠️ N-function pattern has changed!")
    print("  - Try-catch structure might be different")