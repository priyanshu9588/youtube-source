#!/usr/bin/env python3
import re
import json

# Read the script file
with open('/Users/priyanshu/Development/youtube-source/common/src/main/java/dev/lavalink/youtube/cipher/script.js', 'r') as f:
    script = f.read()

print("Advanced YouTube Player Script Analysis")
print("=" * 80)

# Find signatureTimestamp
timestamp_match = re.search(r'(signatureTimestamp|sts):(\d+)', script)
if timestamp_match:
    print(f"SignatureTimestamp: {timestamp_match.group(2)}")
    print()

# Look for the signature function more carefully
# The signature function should:
# 1. Take a parameter
# 2. Split it into array (usually with .split(""))
# 3. Perform transformations (reverse, splice, swap)
# 4. Join back (usually with .join(""))
# 5. Return the result

print("Searching for signature transformation function:")

# Pattern 1: Look for functions that split, transform, and join
sig_patterns = [
    # Pattern for: function(a){a=a.split("");...;return a.join("")}
    r'([a-zA-Z_$][a-zA-Z_$0-9]*)\s*=\s*function\s*\(([a-zA-Z_$][a-zA-Z_$0-9]*)\)\s*\{[^}]*\2=\2\.split\(""\)[^}]*return\s+[^}]*\.join\(""\)[^}]*\}',
    # Pattern for minified version
    r'([a-zA-Z_$][a-zA-Z_$0-9]*)\s*=\s*function\s*\(([a-zA-Z_$][a-zA-Z_$0-9]*)\)\s*\{[^}]*\.split\(""\)[^}]*\.join\(""\)[^}]*\}',
    # Alternative pattern
    r'function\s+([a-zA-Z_$][a-zA-Z_$0-9]*)\s*\(([a-zA-Z_$][a-zA-Z_$0-9]*)\)\s*\{[^}]*\2\.split\(""\)[^}]*return[^}]*\}'
]

for i, pattern in enumerate(sig_patterns, 1):
    sig_pattern = re.compile(pattern, re.DOTALL)
    matches = sig_pattern.findall(script)
    if matches:
        print(f"\nPattern {i} matches:")
        for func_name, param in matches[:3]:
            print(f"  Function: {func_name}, Parameter: {param}")
            # Try to get the function body
            func_pattern = re.compile(rf'{re.escape(func_name)}\s*=\s*function\s*\([^)]*\)\s*\{{([^}}]*(?:\{{[^}}]*\}}[^}}]*)*)\}}', re.DOTALL)
            func_match = func_pattern.search(script)
            if func_match:
                body = func_match.group(1)[:200]
                print(f"    Body preview: {body}...")
                # Check if it references an actions object
                obj_refs = re.findall(r'([a-zA-Z_$][a-zA-Z_$0-9]*)\.[a-zA-Z_$][a-zA-Z_$0-9]*\(', body)
                if obj_refs:
                    unique_refs = list(set(obj_refs))
                    print(f"    References objects: {unique_refs[:3]}")

# Look for the actions object that contains the transformation methods
print("\n" + "=" * 80)
print("Searching for transformation actions object:")

# Look for objects with multiple function methods
actions_patterns = [
    # var obj = {method1:function(a,b){...}, method2:function(a,b){...}, ...}
    r'var\s+([a-zA-Z_$][a-zA-Z_$0-9]*)\s*=\s*\{([^}]*function\s*\([^)]*\)[^}]*\}[^}]*)\}',
    # Minified version
    r'([a-zA-Z_$][a-zA-Z_$0-9]*)\s*=\s*\{((?:[a-zA-Z_$][a-zA-Z_$0-9]*:function\([^)]*\)\{[^}]*\},?\s*)+)\}'
]

found_actions = False
for pattern in actions_patterns:
    if found_actions:
        break
    actions_pattern = re.compile(pattern, re.DOTALL)
    matches = actions_pattern.findall(script)
    for var_name, content in matches:
        # Check if this looks like a transformation object
        if 'splice' in content or 'reverse' in content:
            print(f"\nFound potential actions object: {var_name}")
            # Count methods
            method_count = content.count('function')
            print(f"  Number of methods: {method_count}")
            # Extract method names
            methods = re.findall(r'([a-zA-Z_$][a-zA-Z_$0-9]*)\s*:\s*function', content)
            if methods:
                print(f"  Methods: {methods[:5]}")
            # Check for array operations
            has_reverse = 'reverse' in content
            has_splice = 'splice' in content
            has_slice = 'slice' in content
            print(f"  Operations: reverse={has_reverse}, splice={has_splice}, slice={has_slice}")
            if method_count >= 2 and (has_reverse or has_splice):
                print(f"  >>> This looks like the actions object! <<<")
                found_actions = True
                break

# Look for n-parameter transformation function
print("\n" + "=" * 80)
print("Searching for n-parameter transformation function:")

# The n-function typically:
# 1. Takes a parameter
# 2. Has a try-catch block
# 3. Returns something with the parameter appended in the catch block
n_patterns = [
    # Standard pattern with try-catch
    r'function\s*\(([a-zA-Z_$][a-zA-Z_$0-9]*)\)\s*\{[^}]*try\s*\{[^}]*\}[^}]*catch[^}]*\{[^}]*return[^}]*\+\s*\1[^}]*\}[^}]*\}',
    # Pattern with variable assignment
    r'([a-zA-Z_$][a-zA-Z_$0-9]*)\s*=\s*function\s*\(([a-zA-Z_$][a-zA-Z_$0-9]*)\)\s*\{[^}]*try[^}]*catch[^}]*return[^}]*\2[^}]*\}',
    # More flexible pattern
    r'function\s*\(([a-zA-Z_$][a-zA-Z_$0-9]*)\)\s*\{[^}]*catch\s*\([^)]*\)[^}]*\{[^}]*\1[^}]*\}[^}]*\}'
]

for i, pattern in enumerate(n_patterns, 1):
    n_pattern = re.compile(pattern, re.DOTALL)
    matches = n_pattern.findall(script)
    if matches:
        print(f"\nN-function pattern {i} matches: {len(matches)} functions found")
        # Show first match details
        if isinstance(matches[0], tuple):
            func_name = matches[0][0] if len(matches[0]) > 1 else 'anonymous'
            param = matches[0][-1]
        else:
            func_name = 'anonymous'
            param = matches[0]
        print(f"  First match - Parameter: {param}")

# Look for global variables
print("\n" + "=" * 80)
print("Global variable analysis:")

# Look for array/string declarations that might be used
global_var_pattern = r'var\s+([a-zA-Z_$][a-zA-Z_$0-9]*)\s*=\s*(["\'][^"\']*.split\([^)]*\)|\[[^\]]+\])'
var_matches = re.findall(global_var_pattern, script)

for var_name, var_content in var_matches[:5]:
    if 'split' in var_content:
        print(f"  var {var_name} = [string].split(...)")
    else:
        print(f"  var {var_name} = [array]")

print("\n" + "=" * 80)
print("Summary:")
print(f"  Script length: {len(script)} characters")
print(f"  Timestamp: {timestamp_match.group(2) if timestamp_match else 'Not found'}")
print(f"  Potential signature functions found: {sum(len(re.findall(p, script)) for p in sig_patterns)}")
print(f"  Potential n-functions found: {sum(len(re.findall(p, script, re.DOTALL)) for p in n_patterns)}")