#!/usr/bin/env python3
"""
Generate large test datasets for streaming validation.
Usage: python3 generate_test_data.py [lines] [format] > output.txt
"""

import sys
import random
from datetime import datetime, timedelta

def random_coordinate():
    """Generate random valid coordinates"""
    lat = random.uniform(-90, 90)
    lng = random.uniform(-180, 180)
    return lat, lng

def random_datetime():
    """Generate random datetime between 2020-2030"""
    start = datetime(2020, 1, 1)
    end = datetime(2030, 12, 31)
    delta = end - start
    random_days = random.randint(0, delta.days)
    random_seconds = random.randint(0, 86400)
    dt = start + timedelta(days=random_days, seconds=random_seconds)
    return dt.strftime("%Y-%m-%dT%H:%M:%S")

def generate_paired_data(num_lines):
    """Generate paired coordinate-time data"""
    for _ in range(num_lines):
        lat, lng = random_coordinate()
        dt = random_datetime()
        print(f"{lat:.6f} {lng:.6f} {dt}")

def generate_coordinates(num_lines):
    """Generate coordinate-only data"""
    for _ in range(num_lines):
        lat, lng = random_coordinate()
        print(f"{lat:.6f} {lng:.6f}")

def generate_times(num_lines):
    """Generate time-only data"""
    for _ in range(num_lines):
        dt = random_datetime()
        print(dt)

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 generate_test_data.py [lines|infinite] [format]")
        print("Formats: paired, coords, times")
        print("Example: python3 generate_test_data.py 1000000 paired > big_data.txt")
        print("Example: python3 generate_test_data.py infinite paired | head -10")
        sys.exit(1)
    
    lines_arg = sys.argv[1]
    format_type = sys.argv[2] if len(sys.argv) > 2 else "paired"
    
    if lines_arg == "infinite":
        print(f"# Generating infinite {format_type} data stream...", file=sys.stderr)
        try:
            count = 0
            while True:
                if format_type == "paired":
                    lat, lng = random_coordinate()
                    dt = random_datetime()
                    print(f"{lat:.6f} {lng:.6f} {dt}")
                elif format_type == "coords":
                    lat, lng = random_coordinate()
                    print(f"{lat:.6f} {lng:.6f}")
                elif format_type == "times":
                    dt = random_datetime()
                    print(dt)
                else:
                    print(f"Unknown format: {format_type}", file=sys.stderr)
                    sys.exit(1)
                
                count += 1
                if count % 10000 == 0:
                    print(f"# Generated {count} lines...", file=sys.stderr)
                    
        except (KeyboardInterrupt, BrokenPipeError):
            print(f"# Stream stopped after {count} lines", file=sys.stderr)
            sys.exit(0)
    else:
        num_lines = int(lines_arg)
        print(f"# Generated {num_lines} lines of {format_type} data", file=sys.stderr)
        
        if format_type == "paired":
            generate_paired_data(num_lines)
        elif format_type == "coords":
            generate_coordinates(num_lines)
        elif format_type == "times":
            generate_times(num_lines)
        else:
            print(f"Unknown format: {format_type}", file=sys.stderr)
            sys.exit(1)

if __name__ == "__main__":
    main()