#!/usr/bin/env python3
import re
import json

# Read the script file
with open('/Users/priyanshu/Development/youtube-source/common/src/main/java/dev/lavalink/youtube/cipher/script.js', 'r') as f:
    script = f.read()

# Search for signatureTimestamp
timestamp_match = re.search(r'signatureTimestamp:\s*(\d+)', script)
if timestamp_match:
    print(f"Found signatureTimestamp: {timestamp_match.group(1)}")

# Search for signature transformation function patterns
# Pattern 1: function(a){a=a.split("")...
sig_func_pattern1 = re.compile(r'([a-zA-Z_$][a-zA-Z_$0-9]*)\s*=\s*function\s*\(([a-zA-Z])\)\s*\{\s*\2=\2\.split\(""\)')
matches1 = sig_func_pattern1.findall(script)
if matches1:
    print(f"\nFound signature functions (pattern 1): {matches1[:5]}")

# Pattern 2: More modern minified pattern
sig_func_pattern2 = re.compile(r'([a-zA-Z_$][a-zA-Z_$0-9]*)\s*=\s*function\s*\(([a-zA-Z])\)\s*\{[^}]*split\([^)]*\)[^}]*\}')
matches2 = sig_func_pattern2.findall(script)
if matches2:
    print(f"\nFound potential signature functions (pattern 2): {matches2[:5]}")

# Search for transformation helper object pattern
transform_obj_pattern = re.compile(r'var\s+([a-zA-Z_$][a-zA-Z_$0-9]*)\s*=\s*\{[^}]*:\s*function\s*\([^)]*\)\s*\{[^}]*\}')
transform_matches = transform_obj_pattern.findall(script)
if transform_matches:
    print(f"\nFound transformation objects: {transform_matches[:5]}")

# Search for n-parameter transformation function
n_func_pattern = re.compile(r'function\s*\(([a-zA-Z])\)\s*\{[^}]*catch\s*\([^)]*\)[^}]*return[^}]*\+\s*\1[^}]*\}')
n_matches = n_func_pattern.findall(script)
if n_matches:
    print(f"\nFound n-parameter functions: {n_matches[:5]}")

# Search for global variables declarations
global_vars_pattern = re.compile(r'var\s+([a-zA-Z_$][a-zA-Z_$0-9]*)\s*=\s*"[^"]*"\.split\([^)]*\)')
global_matches = global_vars_pattern.findall(script)
if global_matches:
    print(f"\nFound global variables with split: {global_matches[:5]}")

# Search for any function that manipulates arrays/strings
array_func_pattern = re.compile(r'([a-zA-Z_$][a-zA-Z_$0-9]*)\s*:\s*function\s*\([^)]*\)\s*\{[^}]*(?:reverse|splice|slice|split)[^}]*\}')
array_matches = array_func_pattern.findall(script)
if array_matches:
    print(f"\nFound array manipulation functions: {array_matches[:10]}")

# Look for specific patterns in minified code
print("\n=== Analyzing minified patterns ===")

# Search for compact function definitions
compact_pattern = re.compile(r'([a-zA-Z_$][a-zA-Z_$0-9]*):function\([^)]*\)\{[^}]{10,100}\}')
compact_matches = compact_pattern.findall(script[:50000])  # Check first 50k chars
if compact_matches:
    print(f"Found {len(compact_matches)} compact function definitions")
    for match in compact_matches[:5]:
        print(f"  - {match}")

# Look for the signature cipher actions object
actions_pattern = re.compile(r'([a-zA-Z_$][a-zA-Z_$0-9]*)\s*=\s*\{[^}]*(?:reverse|splice|slice)[^}]*\}')
actions_matches = actions_pattern.findall(script)
if actions_matches:
    print(f"\nFound potential actions objects: {actions_matches[:5]}")

print("\n=== Script structure analysis ===")
print(f"Script length: {len(script)} characters")
print(f"Number of 'function' keywords: {script.count('function')}")
print(f"Number of 'split' calls: {script.count('split')}")
print(f"Number of 'reverse' calls: {script.count('reverse')}")
print(f"Number of 'splice' calls: {script.count('splice')}")