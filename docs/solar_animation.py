#!/usr/bin/env python3
"""Generate global solar elevation animation using solarpos."""

import subprocess
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import cartopy.crs as ccrs
import cartopy.feature as cfeature
from pathlib import Path
import tempfile
import shutil
from PIL import Image

LAT_RANGE = "-60.0:75.0:5.0"
LON_RANGE = "-180.0:175.0:5.0"
DATE = "2026-06-21"
TIMEZONE = "UTC"
OUTPUT_FILE = "solar_elevation_animation.webp"

def generate_solar_data():
    print("Generating solar data...")
    
    with tempfile.NamedTemporaryFile(mode='w', suffix='.csv', delete=False) as f:
        temp_file = f.name
    
    cmd = ["solarpos", "--format=csv", "--headers", LAT_RANGE, LON_RANGE, DATE,
           f"--timezone={TIMEZONE}", "position", "--step=1800"]
    
    print(f"Running: {' '.join(cmd)}")
    with open(temp_file, 'w') as f:
        result = subprocess.run(cmd, stdout=f, stderr=subprocess.PIPE, text=True)
    
    if result.returncode != 0:
        raise RuntimeError(f"solarpos failed: {result.stderr}")
    
    df = pd.read_csv(temp_file)
    df['elevation'] = 90 - df['zenith']
    df['hour'] = pd.to_datetime(df['dateTime']).dt.hour + pd.to_datetime(df['dateTime']).dt.minute / 60.0
    df = df[(df['hour'] >= 0) & (df['hour'] <= 23.75)]
    
    Path(temp_file).unlink()
    print(f"Generated {len(df)} data points for {len(df['hour'].unique())} time steps")
    return df

def create_animation(df):
    print("Creating animation...")
    
    fig = plt.figure(figsize=(10.24, 7.68))
    ax = plt.axes(projection=ccrs.PlateCarree())
    
    ax.add_feature(cfeature.COASTLINE, linewidth=0.5, color='white', alpha=0.8)
    ax.add_feature(cfeature.BORDERS, linewidth=0.3, color='white', alpha=0.6)
    ax.add_feature(cfeature.LAND, color='#2d3436', alpha=0.3)
    ax.add_feature(cfeature.OCEAN, color='#74b9ff', alpha=0.2)
    ax.set_extent([-180, 175, -60, 75], crs=ccrs.PlateCarree())
    
    time_steps = sorted(df['hour'].unique())
    global_vmin, global_vmax = df['elevation'].min(), df['elevation'].max()
    global_levels = np.linspace(global_vmin, global_vmax, 30)
    print(f"Global elevation range: {global_vmin:.1f} to {global_vmax:.1f}")
    
    def animate(frame_idx):
        ax.clear()
        ax.add_feature(cfeature.COASTLINE, linewidth=0.3, color='white', alpha=0.8)
        ax.add_feature(cfeature.BORDERS, linewidth=0.2, color='white', alpha=0.5)
        ax.add_feature(cfeature.LAND, color='#2d3436', alpha=0.3)
        ax.add_feature(cfeature.OCEAN, color='#74b9ff', alpha=0.2)
        ax.set_extent([-180, 175, -60, 75], crs=ccrs.PlateCarree())
        
        current_hour = time_steps[frame_idx]
        frame_data = df[df['hour'] == current_hour].copy()
        if len(frame_data) == 0:
            return []
        
        lons = sorted(frame_data['longitude'].unique())
        lats = sorted(frame_data['latitude'].unique())
        lon_grid, lat_grid = np.meshgrid(lons, lats)
        elev_grid = np.full_like(lon_grid, np.nan)
        
        for _, row in frame_data.iterrows():
            lat_idx = lats.index(row['latitude'])
            lon_idx = lons.index(row['longitude'])
            elev_grid[lat_idx, lon_idx] = row['elevation']
        
        cs = None
        if not np.isnan(elev_grid).all():
            cs = ax.contourf(lon_grid, lat_grid, elev_grid, levels=global_levels, 
                           cmap='viridis', vmin=global_vmin, vmax=global_vmax,
                           alpha=0.90, transform=ccrs.PlateCarree())
        
        ax.gridlines(alpha=0.3, color='white')
        
        hour_int = int(current_hour)
        minute_int = int((current_hour - hour_int) * 60)
        time_str = f"{hour_int:02d}:{minute_int:02d}"
        
        ax.text(0.98, 0.02, f'{time_str} UTC', transform=ax.transAxes, fontsize=12, 
               fontfamily='monospace', ha='right', va='bottom',
               bbox=dict(boxstyle='round,pad=0.3', facecolor='black', alpha=0.7, edgecolor='white'),
               color='white')
        
        return [cs] if cs else []
    
    print("Generating individual frames...")
    frames = []
    temp_dir = tempfile.mkdtemp()
    
    for i, frame_idx in enumerate(range(len(time_steps))):
        animate(frame_idx)
        frame_path = Path(temp_dir) / f"frame_{i:03d}.png"
        fig.savefig(frame_path, dpi=100, bbox_inches='tight', pad_inches=0, facecolor='white')
        frames.append(Image.open(frame_path))
        if (i + 1) % 10 == 0:
            print(f"  Generated {i + 1}/{len(time_steps)} frames")
    
    print(f"Saving animation to {OUTPUT_FILE}...")
    frames[0].save(OUTPUT_FILE, save_all=True, append_images=frames[1:], duration=200, loop=3,
                   lossless=False, quality=75, method=6, minimize_size=True, allow_mixed=True)
    
    shutil.rmtree(temp_dir)
    plt.close(fig)
    print(f"Animation saved as {OUTPUT_FILE}")

def main():
    df = generate_solar_data()
    create_animation(df)

    print(f"  Output file: {OUTPUT_FILE}")
    print(f"  Size: {Path(OUTPUT_FILE).stat().st_size / 1024 / 1024:.1f} MB")

if __name__ == "__main__":
    main()