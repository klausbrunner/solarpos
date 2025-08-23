#!/usr/bin/env python3
"""
Solar Animation Generator for solarpos

Creates an animated visualization showing solar elevation across a geographic region
throughout a full day, demonstrating solarpos's geographic sweep and time series capabilities.
"""

import subprocess
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from matplotlib.animation import FuncAnimation
import cartopy.crs as ccrs
import cartopy.feature as cfeature
from pathlib import Path
import tempfile
from PIL import Image

# Configuration
LAT_RANGE = "-60.0:75.0:5.0"  # Worldwide coverage (excluding extreme polar regions)
LON_RANGE = "-180.0:175.0:5.0"  # Full longitude range
DATE = "2026-06-21"  # Summer solstice
TIMEZONE = "UTC"
OUTPUT_FILE = "solar_elevation_animation.webp"
FRAMES_PER_HOUR = 4  # 15-minute intervals for smooth animation

def generate_solar_data():
    """Generate solar elevation data for the full day using solarpos."""
    print("Generating solar data...")
    
    with tempfile.NamedTemporaryFile(mode='w', suffix='.csv', delete=False) as f:
        temp_file = f.name
    
    # Generate data for full day with fine time resolution
    cmd = [
        "solarpos",
        "--format=csv", "--headers",
        LAT_RANGE, LON_RANGE, DATE,
        f"--timezone={TIMEZONE}",
        "position",
        "--step=1800"  # 30-minute intervals for faster processing
    ]
    
    print(f"Running: {' '.join(cmd)}")
    with open(temp_file, 'w') as f:
        result = subprocess.run(cmd, stdout=f, stderr=subprocess.PIPE, text=True)
    
    if result.returncode != 0:
        raise RuntimeError(f"solarpos failed: {result.stderr}")
    
    # Load and process data
    df = pd.read_csv(temp_file)
    df['elevation'] = 90 - df['zenith']
    df['datetime'] = pd.to_datetime(df['dateTime'])
    df['hour'] = df['datetime'].dt.hour + df['datetime'].dt.minute / 60.0
    
    # Keep full 24-hour range
    df = df[(df['hour'] >= 0) & (df['hour'] <= 23.75)]
    
    Path(temp_file).unlink()  # Clean up
    
    print(f"Generated {len(df)} data points for {len(df['hour'].unique())} time steps")
    return df

def create_animation(df):
    """Create animated visualization of solar elevation."""
    print("Creating animation...")
    
    # Set up the plot with cartopy for geographic projection - smaller size for faster processing
    fig = plt.figure(figsize=(10.24, 7.68))  # 1024x768 at 100 DPI
    ax = plt.axes(projection=ccrs.PlateCarree())
    
    # Add geographic features
    ax.add_feature(cfeature.COASTLINE, linewidth=0.5, color='white', alpha=0.8)
    ax.add_feature(cfeature.BORDERS, linewidth=0.3, color='white', alpha=0.6)
    ax.add_feature(cfeature.LAND, color='#2d3436', alpha=0.3)
    ax.add_feature(cfeature.OCEAN, color='#74b9ff', alpha=0.2)
    
    # Set extent to match data exactly (no frame around data)
    ax.set_extent([-180, 175, -60, 75], crs=ccrs.PlateCarree())
    
    # Get unique time steps
    time_steps = sorted(df['hour'].unique())
    
    # Calculate global elevation range for consistent color scale
    global_vmin = df['elevation'].min()
    global_vmax = df['elevation'].max()
    print(f"Global elevation range: {global_vmin:.1f} to {global_vmax:.1f}")
    
    # Create consistent levels across all frames
    global_levels = np.linspace(global_vmin, global_vmax, 30)
    
    def animate(frame_idx):
        ax.clear()
        
        # Re-add geographic features
        ax.add_feature(cfeature.COASTLINE, linewidth=0.3, color='white', alpha=0.8)
        ax.add_feature(cfeature.BORDERS, linewidth=0.2, color='white', alpha=0.5)
        ax.add_feature(cfeature.LAND, color='#2d3436', alpha=0.3)
        ax.add_feature(cfeature.OCEAN, color='#74b9ff', alpha=0.2)
        ax.set_extent([-180, 175, -60, 75], crs=ccrs.PlateCarree())
        
        current_hour = time_steps[frame_idx]
        frame_data = df[df['hour'] == current_hour].copy()
        
        if len(frame_data) == 0:
            return []
        
        # Create contour plot
        lons = sorted(frame_data['longitude'].unique())
        lats = sorted(frame_data['latitude'].unique())
        
        # Create grid
        lon_grid, lat_grid = np.meshgrid(lons, lats)
        elev_grid = np.full_like(lon_grid, np.nan)
        
        for _, row in frame_data.iterrows():
            lat_idx = lats.index(row['latitude'])
            lon_idx = lons.index(row['longitude'])
            elev_grid[lat_idx, lon_idx] = row['elevation']  # Don't clamp to 0, allow negative values
        
        # Create filled contours with consistent color scale
        valid_data = elev_grid[~np.isnan(elev_grid)]
        if len(valid_data) > 0:
            # Use viridis for better night contrast - darker blues/purples for negative values
            cs = ax.contourf(lon_grid, lat_grid, elev_grid, 
                            levels=global_levels, 
                            cmap='viridis',
                            vmin=global_vmin, 
                            vmax=global_vmax,
                            alpha=0.90,
                            extend='neither',
                            transform=ccrs.PlateCarree())
        else:
            cs = None
        
        # Add gridlines
        gl = ax.gridlines(draw_labels=True, alpha=0.3, color='white')
        gl.top_labels = False
        gl.right_labels = False
        
        # Format time for display
        hour_int = int(current_hour)
        minute_int = int((current_hour - hour_int) * 60)
        time_str = f"{hour_int:02d}:{minute_int:02d}"
        
        # Title - reduced text
        ax.set_title(f'Global Solar Elevation - {time_str} UTC', 
                    fontsize=16, fontweight='bold', pad=15)
        
        # Add timestamp in corner with UTC designator - use monospace font to prevent wiggle
        ax.text(0.98, 0.02, f'{time_str} UTC', 
               transform=ax.transAxes, 
               fontsize=12, 
               fontfamily='monospace',  # Fixed-width font to prevent horizontal movement
               horizontalalignment='right',
               verticalalignment='bottom',
               bbox=dict(boxstyle='round,pad=0.3', facecolor='black', alpha=0.7, edgecolor='white'),
               color='white')
        
        return [cs] if cs is not None else []
    
    # Save individual frames first
    print("Generating individual frames...")
    frames = []
    temp_dir = tempfile.mkdtemp()
    
    for i, frame_idx in enumerate(range(len(time_steps))):
        # Create frame
        animate(frame_idx)
        
        # Save frame to temporary file
        frame_path = Path(temp_dir) / f"frame_{i:03d}.png"
        fig.savefig(frame_path, dpi=100, bbox_inches='tight', 
                   facecolor='black', edgecolor='none')
        
        # Load and append to frames list
        frames.append(Image.open(frame_path))
        
        if (i + 1) % 10 == 0:
            print(f"  Generated {i + 1}/{len(time_steps)} frames")
    
    # Save as WebP animation with maximal compression
    print(f"Saving animation to {OUTPUT_FILE}...")
    frames[0].save(
        OUTPUT_FILE,
        save_all=True,
        append_images=frames[1:],
        duration=200,  # 200ms per frame = 5 fps
        loop=3,  # Only loop 3 times
        lossless=False,
        quality=75,  # Better quality to reduce compression artifacts
        method=6,    # Maximum compression effort
        minimize_size=True,  # Additional size optimization
        allow_mixed=True     # Allow mixed compression modes
    )
    
    # Clean up temporary files
    import shutil
    shutil.rmtree(temp_dir)
    
    plt.close(fig)
    print(f"Animation saved as {OUTPUT_FILE}")

def main():
    """Main function."""
    print("=== Solar Animation Generator ===")
    print("Showcasing solarpos geographic sweep and time series features")
    print()
    
    try:
        # Generate data
        df = generate_solar_data()
        
        # Create animation
        create_animation(df)
        
        print()
        print("✓ Animation created successfully!")
        print(f"  Output file: {OUTPUT_FILE}")
        print(f"  Size: {Path(OUTPUT_FILE).stat().st_size / 1024 / 1024:.1f} MB")
        
    except Exception as e:
        print(f"❌ Error: {e}")
        raise

if __name__ == "__main__":
    main()