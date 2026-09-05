#!/usr/bin/env python3
"""
Crop the user's original flavor icons (Sonora, Lyra, Aria) so that the outer
canvas background is completely removed/invisible, keeping the original artwork intact.
Exports to assets/flavor_icons/ and app/src/{sonora,lyra,aria}/res/mipmap-*/
"""

import os
from PIL import Image, ImageDraw
import numpy as np

def crop_badge(im, box=(37, 37, 273, 273), radius=53):
    """Crops the badge using a 4x supersampled rounded squircle mask."""
    w, h = im.size
    ss = 4
    mask = Image.new("L", (w * ss, h * ss), 0)
    draw = ImageDraw.Draw(mask)
    
    x1, y1, x2, y2 = [coord * ss for coord in box]
    r = radius * ss
    draw.rounded_rectangle([x1, y1, x2, y2], radius=r, fill=255)
    
    mask = mask.resize((w, h), Image.Resampling.LANCZOS)
    
    res = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    res.paste(im, (0, 0), mask)
    
    # Crop to the squircle bounding box
    cropped = res.crop((box[0], box[1], box[2], box[3]))
    return cropped


def export_cropped_flavor_icons(project_root):
    densities = {
        "mdpi": (48, 108),
        "hdpi": (72, 162),
        "xhdpi": (96, 216),
        "xxhdpi": (144, 324),
        "xxxhdpi": (192, 432),
    }
    
    for flv in ["sonora", "lyra", "aria"]:
        src_path = os.path.join(project_root, f"assets/flavor_icons/original/{flv}_original_fg.png")
        if not os.path.exists(src_path):
            print(f"Error: {src_path} not found")
            continue
            
        raw_im = Image.open(src_path).convert("RGBA")
        # In original_fg.png, the sub-image was at (60..371, 60..371)
        sub_im = raw_im.crop((60, 60, 371, 371))
        
        # Crop the squircle badge
        cropped_badge = crop_badge(sub_im)
        
        # Save master cropped icon to assets/flavor_icons/
        assets_dir = os.path.join(project_root, "assets/flavor_icons")
        os.makedirs(assets_dir, exist_ok=True)
        # Upscale with high quality to 512x512 for assets
        master_icon = cropped_badge.resize((512, 512), Image.Resampling.LANCZOS)
        master_icon.save(os.path.join(assets_dir, f"{flv}_icon.png"))
        
        flavor_res = os.path.join(project_root, f"app/src/{flv}/res")
        
        for density, (icon_size, fg_size) in densities.items():
            den_dir = os.path.join(flavor_res, f"mipmap-{density}")
            os.makedirs(den_dir, exist_ok=True)
            
            # 1. Legacy ic_launcher.png (centered with transparent background)
            target_icon_size = int(icon_size * 0.90)
            scaled_icon = cropped_badge.resize((target_icon_size, target_icon_size), Image.Resampling.LANCZOS)
            legacy_canvas = Image.new("RGBA", (icon_size, icon_size), (0, 0, 0, 0))
            offset = (icon_size - target_icon_size) // 2
            legacy_canvas.paste(scaled_icon, (offset, offset), scaled_icon)
            legacy_canvas.save(os.path.join(den_dir, "ic_launcher.png"))
            
            # 2. Foreground for adaptive icon (centered in 72dp safe zone on 108dp canvas)
            target_fg_size = int(fg_size * 0.65)
            scaled_fg = cropped_badge.resize((target_fg_size, target_fg_size), Image.Resampling.LANCZOS)
            fg_canvas = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 0))
            fg_offset = (fg_size - target_fg_size) // 2
            fg_canvas.paste(scaled_fg, (fg_offset, fg_offset), scaled_fg)
            fg_canvas.save(os.path.join(den_dir, "ic_launcher_foreground.png"))
            
            # 3. Background: 100% transparent PNG
            bg_canvas = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 0))
            bg_canvas.save(os.path.join(den_dir, "ic_launcher_background.png"))
            
            # 4. Monochrome for Android 13+ themed icons
            arr = np.array(scaled_fg)
            mono_arr = np.zeros_like(arr)
            mono_arr[:, :, 0] = 255
            mono_arr[:, :, 1] = 255
            mono_arr[:, :, 2] = 255
            mono_arr[:, :, 3] = arr[:, :, 3]
            mono_im = Image.fromarray(mono_arr, "RGBA")
            mono_canvas = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 0))
            mono_canvas.paste(mono_im, (fg_offset, fg_offset), mono_im)
            mono_canvas.save(os.path.join(den_dir, "ic_launcher_monochrome.png"))
            
        print(f"Successfully processed original cropped icon for {flv}!")

if __name__ == "__main__":
    export_cropped_flavor_icons("/home/hunter99507/Documents/Scripts/Scripts/Chora")
