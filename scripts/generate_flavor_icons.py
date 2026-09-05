#!/usr/bin/env python3
"""
Generate crisp, transparent app icons for Sonora, Lyra, and Aria.
All icons have 100% transparent backgrounds so only the icon emblem itself is visible.
"""

import os
import math
from PIL import Image, ImageDraw, ImageFilter
import numpy as np

def create_sonora_icon(size=1024):
    """Warm Amber / Copper double beamed musical note."""
    scale = size / 512.0
    ss = 2
    cs = size * ss
    canvas = Image.new("RGBA", (cs, cs), (0, 0, 0, 0))
    
    mask = Image.new("L", (cs, cs), 0)
    mdraw = ImageDraw.Draw(mask)
    
    c1 = (175 * scale * ss, 360 * scale * ss)
    c2 = (325 * scale * ss, 305 * scale * ss)
    rx = 52 * scale * ss
    ry = 38 * scale * ss
    angle = -25
    
    def draw_ellipse(dr, center, rx, ry, ang):
        pts = []
        rad = math.radians(ang)
        cos_a, sin_a = math.cos(rad), math.sin(rad)
        for deg in range(0, 360, 2):
            t = math.radians(deg)
            x = rx * math.cos(t)
            y = ry * math.sin(t)
            xr = center[0] + x * cos_a - y * sin_a
            yr = center[1] + x * sin_a + y * cos_a
            pts.append((xr, yr))
        dr.polygon(pts, fill=255)
        
    draw_ellipse(mdraw, c1, rx, ry, angle)
    draw_ellipse(mdraw, c2, rx, ry, angle)
    
    stem_w = 26 * scale * ss
    x1_stem = c1[0] + rx * 0.65
    x2_stem = c2[0] + rx * 0.65
    top_y1 = 135 * scale * ss
    top_y2 = 100 * scale * ss
    
    mdraw.rounded_rectangle([x1_stem - stem_w/2, top_y1, x1_stem + stem_w/2, c1[1]], radius=stem_w/2, fill=255)
    mdraw.rounded_rectangle([x2_stem - stem_w/2, top_y2, x2_stem + stem_w/2, c2[1]], radius=stem_w/2, fill=255)
    
    beam_h = 38 * scale * ss
    beam_pts = [
        (x1_stem - stem_w/2, top_y1),
        (x2_stem + stem_w/2, top_y2),
        (x2_stem + stem_w/2, top_y2 + beam_h),
        (x1_stem - stem_w/2, top_y1 + beam_h)
    ]
    mdraw.polygon(beam_pts, fill=255)
    
    beam2_y = beam_h + 18 * scale * ss
    beam2_h = 30 * scale * ss
    beam2_pts = [
        (x1_stem - stem_w/2, top_y1 + beam2_y),
        (x2_stem + stem_w/2, top_y2 + beam2_y),
        (x2_stem + stem_w/2, top_y2 + beam2_y + beam2_h),
        (x1_stem - stem_w/2, top_y1 + beam2_y + beam2_h)
    ]
    mdraw.polygon(beam2_pts, fill=255)
    
    # Soft drop shadow
    shadow_mask = mask.filter(ImageFilter.GaussianBlur(14 * scale * ss))
    shadow_color = Image.new("RGBA", (cs, cs), (0, 0, 0, 110))
    canvas.paste(shadow_color, (0, int(8 * scale * ss)), shadow_mask)
    
    # Warm amber -> glowing copper vertical gradient
    garr = np.zeros((cs, cs, 4), dtype=np.uint8)
    for y in range(cs):
        t = y / float(cs)
        r = int(255 * (1.0 - t * 0.15))
        g = int(185 * (1.0 - t * 0.55))
        b = int(45 * (1.0 - t * 0.70))
        garr[y, :, 0] = r
        garr[y, :, 1] = g
        garr[y, :, 2] = b
        garr[y, :, 3] = 255
    grad = Image.fromarray(garr, "RGBA")
    
    canvas.paste(grad, (0, 0), mask)
    return canvas.resize((size, size), Image.Resampling.LANCZOS)


def create_lyra_icon(size=1024):
    """Electric Cyan / Sapphire harp / lyre silhouette with strings."""
    scale = size / 512.0
    ss = 2
    cs = size * ss
    canvas = Image.new("RGBA", (cs, cs), (0, 0, 0, 0))
    
    mask = Image.new("L", (cs, cs), 0)
    mdraw = ImageDraw.Draw(mask)
    
    # Draw lyre frame: curved arms on left and right, joined at base soundbox
    cx = cs / 2.0
    base_y = 380 * scale * ss
    top_y = 120 * scale * ss
    arm_thick = 30 * scale * ss
    
    # Base soundbox (rounded bowl)
    bowl_bbox = [cx - 110 * scale * ss, base_y - 70 * scale * ss, cx + 110 * scale * ss, base_y + 40 * scale * ss]
    mdraw.ellipse(bowl_bbox, fill=255)
    
    # Left and right curved arms
    def draw_curve(pts, width):
        for i in range(len(pts) - 1):
            p1 = pts[i]
            p2 = pts[i+1]
            mdraw.line([p1, p2], fill=255, width=int(width))
            mdraw.ellipse([p1[0]-width/2, p1[1]-width/2, p1[0]+width/2, p1[1]+width/2], fill=255)
            mdraw.ellipse([p2[0]-width/2, p2[1]-width/2, p2[0]+width/2, p2[1]+width/2], fill=255)
            
    left_arm = []
    right_arm = []
    steps = 30
    for i in range(steps + 1):
        t = i / float(steps)
        y = (base_y - 20 * scale * ss) * (1 - t) + top_y * t
        # Curve outward then gently inward
        flare = math.sin(t * math.pi) * 65 * scale * ss
        base_x = 90 * scale * ss + t * 40 * scale * ss
        lx = cx - base_x - flare
        rx = cx + base_x + flare
        left_arm.append((lx, y))
        right_arm.append((rx, y))
        
    draw_curve(left_arm, arm_thick)
    draw_curve(right_arm, arm_thick)
    
    # Top finials (gentle curl outward at top)
    mdraw.ellipse([left_arm[-1][0]-arm_thick*0.7, left_arm[-1][1]-arm_thick*0.7, left_arm[-1][0]+arm_thick*0.7, left_arm[-1][1]+arm_thick*0.7], fill=255)
    mdraw.ellipse([right_arm[-1][0]-arm_thick*0.7, right_arm[-1][1]-arm_thick*0.7, right_arm[-1][0]+arm_thick*0.7, right_arm[-1][1]+arm_thick*0.7], fill=255)
    
    # Top crossbar
    cross_y = top_y + 25 * scale * ss
    cross_left = cx - 120 * scale * ss
    cross_right = cx + 120 * scale * ss
    mdraw.rounded_rectangle([cross_left, cross_y - 14 * scale * ss, cross_right, cross_y + 14 * scale * ss], radius=14*scale*ss, fill=255)
    
    # Strings (4 vertical strings)
    string_w = 10 * scale * ss
    for sx_rel in [-50, -17, 17, 50]:
        sx = cx + sx_rel * scale * ss
        mdraw.line([(sx, cross_y), (sx, base_y - 20 * scale * ss)], fill=255, width=int(string_w))
    
    # Soft drop shadow
    shadow_mask = mask.filter(ImageFilter.GaussianBlur(14 * scale * ss))
    shadow_color = Image.new("RGBA", (cs, cs), (0, 0, 0, 110))
    canvas.paste(shadow_color, (0, int(8 * scale * ss)), shadow_mask)
    
    # Electric cyan -> deep sapphire vertical gradient
    garr = np.zeros((cs, cs, 4), dtype=np.uint8)
    for y in range(cs):
        t = y / float(cs)
        r = int(0 + t * 0)
        g = int(215 * (1.0 - t * 0.50))
        b = int(255 * (1.0 - t * 0.15))
        garr[y, :, 0] = r
        garr[y, :, 1] = g
        garr[y, :, 2] = b
        garr[y, :, 3] = 255
    grad = Image.fromarray(garr, "RGBA")
    
    canvas.paste(grad, (0, 0), mask)
    return canvas.resize((size, size), Image.Resampling.LANCZOS)


def create_aria_icon(size=1024):
    """Radiant Magenta / Violet infinity acoustic wave loop."""
    scale = size / 512.0
    ss = 2
    cs = size * ss
    canvas = Image.new("RGBA", (cs, cs), (0, 0, 0, 0))
    
    mask = Image.new("L", (cs, cs), 0)
    mdraw = ImageDraw.Draw(mask)
    
    cx = cs / 2.0
    cy = cs / 2.0
    
    # Lemniscate of Bernoulli (Infinity curve)
    # x(t) = a * cos(t) / (1 + sin(t)^2)
    # y(t) = a * sin(t)*cos(t) / (1 + sin(t)^2)
    a = 185 * scale * ss
    ribbon_w = 46 * scale * ss
    
    pts = []
    for deg in range(0, 360, 1):
        t = math.radians(deg)
        denom = 1 + math.sin(t)**2
        x = cx + (a * math.cos(t)) / denom
        y = cy + (a * math.sin(t) * math.cos(t) * 1.35) / denom
        pts.append((x, y))
        
    for i in range(len(pts) - 1):
        p1 = pts[i]
        p2 = pts[i+1]
        mdraw.line([p1, p2], fill=255, width=int(ribbon_w))
        mdraw.ellipse([p1[0]-ribbon_w/2, p1[1]-ribbon_w/2, p1[0]+ribbon_w/2, p1[1]+ribbon_w/2], fill=255)
        
    # Soft drop shadow
    shadow_mask = mask.filter(ImageFilter.GaussianBlur(14 * scale * ss))
    shadow_color = Image.new("RGBA", (cs, cs), (0, 0, 0, 110))
    canvas.paste(shadow_color, (0, int(8 * scale * ss)), shadow_mask)
    
    # Radiant magenta -> deep purple-violet horizontal/vertical gradient
    garr = np.zeros((cs, cs, 4), dtype=np.uint8)
    for y in range(cs):
        for x in range(cs):
            t = (y * 0.4 + x * 0.6) / float(cs)
            r = int(245 * (1.0 - t * 0.35))
            g = int(50 * (1.0 - t * 0.50))
            b = int(230 + t * 25)
            garr[y, x, 0] = r
            garr[y, x, 1] = g
            garr[y, x, 2] = min(255, b)
            garr[y, x, 3] = 255
    grad = Image.fromarray(garr, "RGBA")
    
    canvas.paste(grad, (0, 0), mask)
    return canvas.resize((size, size), Image.Resampling.LANCZOS)


def export_flavor_assets(name, icon_img, project_root):
    """
    Exports all standard Android mipmap densities with transparent background:
    - ic_launcher.png (legacy icon)
    - ic_launcher_foreground.png (scaled within 72dp safe zone on 108dp canvas)
    - ic_launcher_background.png (100% transparent PNG)
    - ic_launcher_monochrome.png (monochrome silhouette with alpha)
    """
    densities = {
        "mdpi": (48, 108),
        "hdpi": (72, 162),
        "xhdpi": (96, 216),
        "xxhdpi": (144, 324),
        "xxxhdpi": (192, 432),
    }
    
    flavor_res = os.path.join(project_root, f"app/src/{name}/res")
    os.makedirs(flavor_res, exist_ok=True)
    
    # Save master high-res icon to assets
    assets_dir = os.path.join(project_root, "assets/flavor_icons")
    os.makedirs(assets_dir, exist_ok=True)
    icon_img.save(os.path.join(assets_dir, f"{name}_icon.png"))
    
    for density, (icon_size, fg_size) in densities.items():
        den_dir = os.path.join(flavor_res, f"mipmap-{density}")
        os.makedirs(den_dir, exist_ok=True)
        
        # 1. Legacy icon (centered in icon_size, transparent bg)
        # Scale icon to occupy ~85% of icon_size
        target_icon_size = int(icon_size * 0.88)
        scaled_icon = icon_img.resize((target_icon_size, target_icon_size), Image.Resampling.LANCZOS)
        legacy_canvas = Image.new("RGBA", (icon_size, icon_size), (0, 0, 0, 0))
        offset = (icon_size - target_icon_size) // 2
        legacy_canvas.paste(scaled_icon, (offset, offset), scaled_icon)
        legacy_canvas.save(os.path.join(den_dir, "ic_launcher.png"))
        
        # 2. Foreground for adaptive icon
        # In Android adaptive icon, canvas is 108dp. Safe zone is 72dp circle in center.
        # So symbol should be ~ 65dp to 70dp in center.
        # Ratio of safe zone: 72 / 108 = 0.6667. We scale to ~0.58 of fg_size.
        target_fg_symbol_size = int(fg_size * 0.58)
        scaled_fg_symbol = icon_img.resize((target_fg_symbol_size, target_fg_symbol_size), Image.Resampling.LANCZOS)
        fg_canvas = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 0))
        fg_offset = (fg_size - target_fg_symbol_size) // 2
        fg_canvas.paste(scaled_fg_symbol, (fg_offset, fg_offset), scaled_fg_symbol)
        fg_canvas.save(os.path.join(den_dir, "ic_launcher_foreground.png"))
        
        # 3. Background: 100% transparent PNG
        bg_canvas = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 0))
        bg_canvas.save(os.path.join(den_dir, "ic_launcher_background.png"))
        
        # 4. Monochrome for themed icons (Android 13+)
        # Convert symbol alpha to pure white
        arr = np.array(scaled_fg_symbol)
        mono_arr = np.zeros_like(arr)
        mono_arr[:, :, 0] = 255
        mono_arr[:, :, 1] = 255
        mono_arr[:, :, 2] = 255
        mono_arr[:, :, 3] = arr[:, :, 3]
        mono_symbol = Image.fromarray(mono_arr, "RGBA")
        mono_canvas = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 0))
        mono_canvas.paste(mono_symbol, (fg_offset, fg_offset), mono_symbol)
        mono_canvas.save(os.path.join(den_dir, "ic_launcher_monochrome.png"))
        
    print(f"Successfully generated all transparent icons for {name}!")


def main():
    project_root = "/home/hunter99507/Documents/Scripts/Scripts/Chora"
    
    print("Generating Sonora icon...")
    sonora = create_sonora_icon(1024)
    export_flavor_assets("sonora", sonora, project_root)
    
    print("Generating Lyra icon...")
    lyra = create_lyra_icon(1024)
    export_flavor_assets("lyra", lyra, project_root)
    
    print("Generating Aria icon...")
    aria = create_aria_icon(1024)
    export_flavor_assets("aria", aria, project_root)
    
    print("Done generating all flavor icon sets!")


if __name__ == "__main__":
    main()
