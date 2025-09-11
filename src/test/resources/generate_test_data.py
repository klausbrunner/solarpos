#!/usr/bin/env python3
"""Generate large test datasets for streaming validation."""

import sys
import random
from datetime import datetime
from collections.abc import Iterator

# Pre-compute constants for faster random generation
MIN_TIMESTAMP: int = 1577836800  # 2020-01-01T00:00:00
MAX_TIMESTAMP: int = 1924991999  # 2030-12-31T23:59:59

def generate_data(format_type: str) -> Iterator[str]:
    match format_type:
        case "paired":
            while True:
                lat, lng = random.uniform(-90, 90), random.uniform(-180, 180)
                dt = datetime.fromtimestamp(random.randint(MIN_TIMESTAMP, MAX_TIMESTAMP))
                yield f"{lat:.6f} {lng:.6f} {dt:%Y-%m-%dT%H:%M:%S}"
        case "coords":
            while True:
                lat, lng = random.uniform(-90, 90), random.uniform(-180, 180)
                yield f"{lat:.6f} {lng:.6f}"
        case "times":
            while True:
                dt = datetime.fromtimestamp(random.randint(MIN_TIMESTAMP, MAX_TIMESTAMP))
                yield f"{dt:%Y-%m-%dT%H:%M:%S}"
        case _:
            raise ValueError(f"Unknown format: {format_type}")

def main():
    match sys.argv[1:]:
        case []:
            print("Usage: python3 generate_test_data.py [lines|infinite] [format]", file=sys.stderr)
            print("Formats: paired, coords, times", file=sys.stderr)
            sys.exit(1)
        case [lines_arg]:
            format_type = "paired"
        case [lines_arg, format_type]:
            pass
        case _:
            print("Too many arguments", file=sys.stderr)
            sys.exit(1)

    data_gen = generate_data(format_type)
    
    match lines_arg:
        case "infinite":
            print(f"# Generating infinite {format_type} data stream...", file=sys.stderr)
            try:
                for count, line in enumerate(data_gen, 1):
                    print(line)
                    if count % 10000 == 0:
                        print(f"# Generated {count} lines...", file=sys.stderr)
            except (KeyboardInterrupt, BrokenPipeError):
                print(f"# Stream stopped after {count} lines", file=sys.stderr)
        case _:
            try:
                num_lines = int(lines_arg)
            except ValueError:
                print(f"Invalid number: {lines_arg}", file=sys.stderr)
                sys.exit(1)
            
            print(f"# Generated {num_lines} lines of {format_type} data", file=sys.stderr)
            for line, _ in zip(data_gen, range(num_lines)):
                print(line)

if __name__ == "__main__":
    main()