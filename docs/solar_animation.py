#!/usr/bin/env python3
"""Generate global solar elevation animation using solarpos."""

import subprocess
import tempfile
import warnings
from pathlib import Path

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import cartopy.crs as ccrs
import cartopy.feature as cfeature
from PIL import Image

warnings.filterwarnings('ignore', message='facecolor will have no effect')

LAT_RANGE = "-60.0:75.0:5.0"
LON_RANGE = "-180.0:180.0:5.0"
DATE = "2026-06-21"
OUTPUT_FILE = "solar_elevation_animation.webp"

def generate_solar_data():
    with tempfile.NamedTemporaryFile(mode='w', suffix='.csv') as f:
        cmd = [
            "solarpos", "--format=csv", "--headers", "--parallel",
            LAT_RANGE, LON_RANGE, DATE, "--timezone=UTC",
            "position", "--step=1800"
        ]
        subprocess.run(cmd, stdout=f, check=True, text=True)
        f.flush()
        df = pd.read_csv(f.name)
    
    df['elevation'] = 90 - df['zenith']
    df['hour'] = pd.to_datetime(df['dateTime']).dt.hour + pd.to_datetime(df['dateTime']).dt.minute / 60
    return df[df['hour'].between(0, 23.75)]

def create_frame(ax, df, hour, levels, vmin, vmax):
    ax.clear()
    
    for feature, kwargs in [
        (cfeature.COASTLINE, {'linewidth': 0.3, 'color': 'white', 'alpha': 0.8}),
        (cfeature.BORDERS, {'linewidth': 0.2, 'color': 'white', 'alpha': 0.5}),
        (cfeature.LAND, {'color': '#2d3436', 'alpha': 0.3}),
        (cfeature.OCEAN, {'color': '#74b9ff', 'alpha': 0.2})
    ]:
        ax.add_feature(feature, **kwargs)
    
    ax.set_extent([-180, 180, -60, 75], crs=ccrs.PlateCarree())
    ax.spines['geo'].set_visible(False)
    
    frame_data = df[df['hour'] == hour]
    if frame_data.empty:
        return
    
    lons = sorted(frame_data['longitude'].unique())
    lats = sorted(frame_data['latitude'].unique())
    lon_grid, lat_grid = np.meshgrid(lons, lats)
    
    elev_grid = frame_data.pivot(index='latitude', columns='longitude', values='elevation').values
    
    ax.contourf(lon_grid, lat_grid, elev_grid, levels=levels,
                cmap='viridis', vmin=vmin, vmax=vmax,
                alpha=0.9, transform=ccrs.PlateCarree())
    
    ax.gridlines(alpha=0.3, color='white')
    
    h, m = int(hour), int((hour % 1) * 60)
    ax.text(0.98, 0.02, f'{h:02d}:{m:02d} UTC', transform=ax.transAxes,
            fontsize=12, fontfamily='monospace', ha='right', va='bottom',
            bbox={'boxstyle': 'round,pad=0.3', 'facecolor': 'black', 'alpha': 0.7, 'edgecolor': 'white'},
            color='white')

def create_animation(df):
    fig = plt.figure(figsize=(10.24, 7.68))
    ax = plt.axes(projection=ccrs.PlateCarree())
    
    vmin, vmax = df['elevation'].min(), df['elevation'].max()
    levels = np.linspace(vmin, vmax, 30)
    hours = sorted(df['hour'].unique())
    
    with tempfile.TemporaryDirectory() as temp_dir:
        frames = []
        for i, hour in enumerate(hours):
            create_frame(ax, df, hour, levels, vmin, vmax)
            frame_path = Path(temp_dir) / f"frame_{i:03d}.png"
            fig.savefig(frame_path, dpi=100, bbox_inches='tight', pad_inches=0, facecolor='white')
            frames.append(Image.open(frame_path))
        
        frames[0].save(OUTPUT_FILE, save_all=True, append_images=frames[1:],
                      duration=200, loop=2, quality=75, method=6)
    
    plt.close(fig)
    file_size_mb = Path(OUTPUT_FILE).stat().st_size / 1024 / 1024
    print(f"Animation saved: {OUTPUT_FILE} ({file_size_mb:.1f} MB)")

def main():
    df = generate_solar_data()
    create_animation(df)

if __name__ == "__main__":
    main()