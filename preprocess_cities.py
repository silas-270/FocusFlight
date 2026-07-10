import json
import math
import os

def preprocess():
    input_file = "cities15000.txt"
    output_dir = "app/src/main/assets/js/environment"
    output_file = os.path.join(output_dir, "cities_grid.json")

    # Ensure output directory exists
    os.makedirs(output_dir, exist_ok=True)

    grid = {}
    city_count = 0

    if not os.path.exists(input_file):
        print(f"Error: {input_file} not found!")
        return

    with open(input_file, "r", encoding="utf-8") as f:
        for line_num, line in enumerate(f, 1):
            parts = line.strip("\n").split("\t")
            if len(parts) < 19:
                continue

            name = parts[2] # asciiname (ASCII version to prevent display/font issues)
            lat = float(parts[4])
            lon = float(parts[5])
            feat_code = parts[7]
            country = parts[8]
            
            try:
                pop = int(parts[14]) if parts[14] else 0
            except ValueError:
                pop = 0

            # Exclude suburbs/districts
            if feat_code == "PPLX":
                continue

            # ── TIER CLASSIFICATION ─────────────────────────────────────────
            # Tier 0: Country capitals (PPLC) or mega-cities with pop >= 3 million
            if feat_code == "PPLC" or pop >= 3000000:
                tier = 0
            # Tier 1: Regional capitals (PPLA) or very large cities pop >= 500k
            elif feat_code == "PPLA" or pop >= 500000:
                tier = 1
            # Tier 2: County capitals (PPLA2) or large cities pop >= 100k
            elif feat_code == "PPLA2" or pop >= 100000:
                tier = 2
            # Tier 3: Small cities (PPL) >= 15k
            else:
                tier = 3

            # Round coordinates to 2 decimal places to save file size (~1.1 km precision)
            lat = round(lat, 2)
            lon = round(lon, 2)

            # ── SPATIAL GRIDDING (10x10 degree bins) ────────────────────────
            lat_bin = int(math.floor(lat / 10.0) * 10)
            lon_bin = int(math.floor(lon / 10.0) * 10)
            grid_key = f"{lat_bin}_{lon_bin}"

            if grid_key not in grid:
                grid[grid_key] = []

            # Structure: [name, lat, lon, country_code, tier]
            grid[grid_key].append([name, lat, lon, country, tier])
            city_count += 1

    # Write to optimized JSON
    with open(output_file, "w", encoding="utf-8") as out:
        json.dump(grid, out, separators=(',', ':')) # No whitespaces for minimum file size

    print(f"Pre-processed {city_count} cities into {len(grid)} spatial grid cells.")
    print(f"Saved optimized JSON to: {output_file}")
    file_size_kb = os.path.getsize(output_file) / 1024.0
    print(f"Output File Size: {file_size_kb:.2f} KB")

if __name__ == "__main__":
    preprocess()
