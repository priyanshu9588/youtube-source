#!/usr/bin/env python3
import re

# Read the script file
with open('/Users/priyanshu/Development/youtube-source/common/src/main/java/dev/lavalink/youtube/cipher/script.js', 'r') as f:
    script = f.read()

# Find the signatureTimestamp
timestamp_match = re.search(r'signatureTimestamp:\s*(\d+)', script)
if timestamp_match:
    print(f"SignatureTimestamp: {timestamp_match.group(1)}")
    print("=" * 80)

# Find zAZ function which seems to be a signature function
print("\nSearching for zAZ function (potential signature function):")
zaz_pattern = re.compile(r'(zAZ\s*=\s*function[^}]+\})', re.DOTALL)
zaz_match = zaz_pattern.search(script)
if zaz_match:
    func_text = zaz_match.group(1)[:500]
    print(func_text)
    print("...")

# Look for the helper object that contains splice, reverse operations
print("\n" + "=" * 80)
print("Searching for transformation helper objects:")

# Search for objects with methods like pA, qN, etc that perform array operations
helper_pattern = re.compile(r'var\s+([a-zA-Z_$][a-zA-Z_$0-9]*)\s*=\s*\{([^}]+(?:splice|reverse|slice)[^}]+)\}', re.DOTALL)
helper_matches = helper_pattern.findall(script)

for i, (var_name, content) in enumerate(helper_matches[:3]):
    print(f"\n{i+1}. Variable: {var_name}")
    # Clean up and show first 300 chars
    cleaned = content.replace('\n', ' ').strip()[:300]
    print(f"   Content: {cleaned}...")

# Search for n-parameter transformation function
print("\n" + "=" * 80)
print("Searching for n-parameter transformation functions:")

# Look for functions that contain specific patterns for n-param transformation
n_patterns = [
    r'function\s*\(([a-zA-Z])\)\s*\{[^}]*var\s+([a-zA-Z_$][a-zA-Z_$0-9]*)\s*=\s*\1\[[^]]+\]\([^)]+\)[^}]*catch[^}]*return[^}]+\+\s*\1[^}]*\}',
    r'([a-zA-Z_$][a-zA-Z_$0-9]*)\s*=\s*function\s*\(([a-zA-Z])\)\s*\{[^}]*catch[^}]*return[^}]+\2[^}]*\}'
]

for pattern in n_patterns:
    n_pattern = re.compile(pattern, re.DOTALL)
    n_matches = n_pattern.findall(script)
    if n_matches:
        print(f"\nFound n-function matches: {n_matches[:2]}")

# Look for global variables that are arrays
print("\n" + "=" * 80)
print("Global variables (arrays):")

var_Y_match = re.search(r'var\s+Y\s*=\s*([^;]+);', script)
if var_Y_match:
    var_content = var_Y_match.group(1)[:200]
    print(f"var Y = {var_content}...")

# Look for the actual signature and n-parameter calls in the code
print("\n" + "=" * 80)
print("Looking for signature cipher usage patterns:")

# Find where zAZ is called
zaz_usage = re.findall(r'([a-zA-Z_$][a-zA-Z_$0-9]*)\s*\([^,]+,\s*zAZ\)', script)
if zaz_usage:
    print(f"zAZ is used by: {zaz_usage[:5]}")

# Search for the actual transformation actions object
print("\n" + "=" * 80)
print("Searching for transformation actions object:")

# Pattern for object with methods that take two parameters (array and index)
actions_pattern = re.compile(r'var\s+([a-zA-Z_$][a-zA-Z_$0-9]*)\s*=\s*\{([^}]*:\s*function\s*\([a-zA-Z],\s*[a-zA-Z]\)[^}]*)\}', re.DOTALL)
actions_matches = actions_pattern.findall(script)

for var_name, content in actions_matches[:3]:
    # Check if this looks like the actions object (should have splice, reverse operations)
    if 'splice' in content or 'reverse' in content:
        print(f"\nPotential actions object: {var_name}")
        # Extract method names
        methods = re.findall(r'([a-zA-Z_$][a-zA-Z_$0-9]*)\s*:\s*function', content)
        print(f"Methods: {methods[:10]}")