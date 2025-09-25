#!/usr/bin/env python3
import re

# Read the script file
with open('/Users/priyanshu/Development/youtube-source/common/src/main/java/dev/lavalink/youtube/cipher/script.js', 'r') as f:
    script = f.read()

print("Analyzing YouTube player script for signature cipher...")
print("=" * 80)

# Find signatureTimestamp
timestamp_match = re.search(r'signatureTimestamp[:\s]*(\d+)', script)
if timestamp_match:
    print(f"Found signatureTimestamp: {timestamp_match.group(1)}\n")

# Method 1: Look for functions that call split("") on their parameter
print("Searching for signature transformation function (split pattern):")
split_func_pattern = re.compile(r'([a-zA-Z_$][a-zA-Z_$0-9]*)\s*=\s*function\s*\(([a-zA-Z])\)\s*\{[^}]*\2\.split\(["\']"*["\']?\)', re.DOTALL)
split_matches = split_func_pattern.findall(script)

if split_matches:
    for func_name, param in split_matches[:10]:
        # Get more context for each function
        func_pattern = re.compile(rf'{re.escape(func_name)}\s*=\s*function\s*\([^)]*\)\s*\{{([^}}]*\}}[^}}]*)*\}}', re.DOTALL)
        func_match = func_pattern.search(script)
        if func_match:
            func_body = func_match.group(0)
            # Check if this looks like a signature function (should manipulate arrays)
            if 'split' in func_body and ('reverse' in func_body or 'splice' in func_body or 'slice' in func_body):
                print(f"\nPotential signature function: {func_name}")
                print(f"First 300 chars: {func_body[:300]}...")

                # Try to find what helper object it uses
                helper_pattern = re.compile(r'([a-zA-Z_$][a-zA-Z_$0-9]*)\.[a-zA-Z_$][a-zA-Z_$0-9]*\([^)]*\)')
                helper_matches = helper_pattern.findall(func_body)
                if helper_matches:
                    helper_obj = helper_matches[0].split('.')[0]
                    print(f"Uses helper object: {helper_obj}")

# Method 2: Search for the helper/actions object
print("\n" + "=" * 80)
print("Searching for transformation helper/actions object:")

# Look for objects with reverse, splice, slice methods
obj_pattern = re.compile(r'var\s+([a-zA-Z_$][a-zA-Z_$0-9]*)\s*=\s*\{([^}]*(?:reverse|splice|slice)[^}]*)\}', re.DOTALL)
obj_matches = obj_pattern.findall(script)

for var_name, content in obj_matches:
    # Count the number of methods
    method_count = content.count('function')
    if method_count >= 2:  # Should have at least 2-3 transformation methods
        print(f"\nFound actions object: {var_name}")
        print(f"Number of methods: {method_count}")

        # Extract method signatures
        method_pattern = re.compile(r'([a-zA-Z_$][a-zA-Z_$0-9]*)\s*:\s*function\s*\(([^)]*)\)')
        methods = method_pattern.findall(content)

        for method_name, params in methods[:5]:
            print(f"  - {method_name}({params})")

        # Show a sample of the content
        print(f"Sample content (first 400 chars):")
        print(content[:400])
        break

# Method 3: Look for n-parameter transformation function
print("\n" + "=" * 80)
print("Searching for n-parameter transformation function:")

# Enhanced pattern for n-function
n_patterns = [
    # Pattern 1: Classic n-function with try-catch
    r'([a-zA-Z_$][a-zA-Z_$0-9]*)\s*=\s*function\s*\(([a-zA-Z])\)\s*\{[^}]*try\s*\{[^}]*\}[^}]*catch[^}]*return[^}]*\2[^}]*\}',
    # Pattern 2: Function with specific n-transform structure
    r'([a-zA-Z_$][a-zA-Z_$0-9]*)\s*=\s*function\s*\(([a-zA-Z])\)\s*\{[^}]*var\s+[a-zA-Z_$][a-zA-Z_$0-9]*\s*=[^}]*\2[^}]*return[^}]*\}',
    # Pattern 3: Minified version
    r'([a-zA-Z_$][a-zA-Z_$0-9]*):function\s*\(([a-zA-Z])\)\s*\{[^}]*catch[^}]*\2[^}]*\}'
]

for i, pattern in enumerate(n_patterns):
    n_pattern = re.compile(pattern, re.DOTALL)
    n_matches = n_pattern.findall(script)
    if n_matches:
        print(f"\nPattern {i+1} matches:")
        for func_name, param in n_matches[:3]:
            print(f"  - Function: {func_name}, Parameter: {param}")
            # Try to get the function body
            if ':function' in pattern:
                func_pattern = re.compile(rf'{re.escape(func_name)}:function\s*\([^)]*\)\s*\{{[^}}]*\}}', re.DOTALL)
            else:
                func_pattern = re.compile(rf'{re.escape(func_name)}\s*=\s*function\s*\([^)]*\)\s*\{{[^}}]*\}}', re.DOTALL)

            func_match = func_pattern.search(script)
            if func_match:
                print(f"    Body preview: {func_match.group(0)[:200]}...")

# Method 4: Look for global variable declarations
print("\n" + "=" * 80)
print("Global variable declarations:")

# var Y seems to be important
var_patterns = ['Y', 'h', 'c']
for var_name in var_patterns:
    var_pattern = re.compile(rf'var\s+{var_name}\s*=\s*([^;]+);')
    var_match = var_pattern.search(script)
    if var_match:
        content = var_match.group(1)
        if 'split' in content:
            print(f"\nvar {var_name} = {content[:150]}...")

print("\n" + "=" * 80)
print("Analysis complete!")