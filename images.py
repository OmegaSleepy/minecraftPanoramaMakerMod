#!/usr/bin/env python3
import os, sys, math, numpy as np, cv2
from PIL import Image

OUT_W = 4096
OUT_H = 2048
RESULTS_DIR = "results"
os.makedirs(RESULTS_DIR, exist_ok=True)

def sph_to_cart(lon, lat):
    x = np.cos(lat) * np.sin(lon)
    y = np.sin(lat)
    z = np.cos(lat) * np.cos(lon)
    return x, y, z

def cart_to_cube_uv(x, y, z):
    ax = np.abs(x); ay = np.abs(y); az = np.abs(z)
    face = np.zeros_like(x, dtype=np.int32)
    u = np.zeros_like(x, dtype=np.float32)
    v = np.zeros_like(x, dtype=np.float32)

    # X major
    mask = (ax >= ay) & (ax >= az)
    mask_pos = mask & (x > 0)
    u[mask_pos] = -z[mask_pos] / ax[mask_pos]
    v[mask_pos] = -y[mask_pos] / ax[mask_pos]
    face[mask_pos] = 1  # right

    mask_neg = mask & (x <= 0)
    u[mask_neg] = z[mask_neg] / ax[mask_neg]
    v[mask_neg] = -y[mask_neg] / ax[mask_neg]
    face[mask_neg] = 3  # left

    # Y major
    mask = (ay > ax) & (ay >= az)
    mask_pos = mask & (y > 0)
    u[mask_pos] = x[mask_pos] / ay[mask_pos]
    v[mask_pos] = z[mask_pos] / ay[mask_pos]
    face[mask_pos] = 4  # top

    mask_neg = mask & (y <= 0)
    u[mask_neg] = x[mask_neg] / ay[mask_neg]
    v[mask_neg] = -z[mask_neg] / ay[mask_neg]
    face[mask_neg] = 5  # bottom

    # Z major
    mask = (az > ax) & (az > ay)
    mask_pos = mask & (z > 0)
    u[mask_pos] = x[mask_pos] / az[mask_pos]
    v[mask_pos] = -y[mask_pos] / az[mask_pos]
    face[mask_pos] = 0  # front

    mask_neg = mask & (z <= 0)
    u[mask_neg] = -x[mask_neg] / az[mask_neg]
    v[mask_neg] = -y[mask_neg] / az[mask_neg]
    face[mask_neg] = 2  # back

    # Convert u,v from [-1,1] to [0,1]
    u = (u * 0.5 + 0.5).astype(np.float32)
    v = (v * 0.5 + 0.5).astype(np.float32)
    return face, u, v

def build_maps(out_w, out_h):
    i = np.arange(out_w, dtype=np.float32)
    j = np.arange(out_h, dtype=np.float32)
    lon = (i + 0.5) / out_w * (2 * math.pi) - math.pi
    lat = (0.5 - (j + 0.5) / out_h) * math.pi
    lon_grid, lat_grid = np.meshgrid(lon, lat)
    x, y, z = sph_to_cart(lon_grid, lat_grid)
    face, u, v = cart_to_cube_uv(x, y, z)
    return face, u, v

def load_faces(folder):
    imgs = {}
    for i in range(6):
        p = os.path.join(folder, f"side_{i}.png")
        if not os.path.isfile(p):
            raise FileNotFoundError(p)
        img = cv2.imread(p, cv2.IMREAD_COLOR)
        if img is None:
            raise IOError(f"Failed to load {p}")
        img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        h, w = img.shape[:2]
        if h != w:
            raise ValueError(f"{p} not square: {w}x{h}")
        imgs[i] = img
    sizes = {imgs[i].shape[0] for i in imgs}
    if len(sizes) != 1:
        raise ValueError("face sizes differ")
    return imgs

def stitch_folder(folder, out_w=OUT_W, out_h=OUT_H):
    base = os.path.basename(folder)
    outname = base[len("panorama_"):] if base.startswith("panorama_") else base
    faces_img = load_faces(folder)
    face_map, u_map, v_map = build_maps(out_w, out_h)
    H = out_h; W = out_w
    out = np.zeros((H, W, 3), dtype=np.uint8)
    face_size = next(iter(faces_img.values())).shape[0]

    for face_idx in range(6):
        mask = (face_map == face_idx)
        if not np.any(mask):
            continue
        map_x = np.full((H, W), -1.0, dtype=np.float32)
        map_y = np.full((H, W), -1.0, dtype=np.float32)
        px = u_map * (face_size - 1)
        py = v_map * (face_size - 1)
        map_x[mask] = px[mask]
        map_y[mask] = py[mask]
        sampled = cv2.remap(faces_img[face_idx], map_x, map_y, interpolation=cv2.INTER_LINEAR, borderMode=cv2.BORDER_WRAP)
        out[mask] = sampled[mask]
    return out, outname

SEARCH_DIR = "/home/martin/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/Create_Endgame/minecraft/panoramas"  # <- your target folder

def process_all():
    folders = sorted([
        d for d in os.listdir(SEARCH_DIR)
        if os.path.isdir(os.path.join(SEARCH_DIR, d)) and d.startswith("panorama_")
    ])

    if not folders:
        print("No panorama_* folders found", file=sys.stderr)
        return

    for f in folders:
        full_path = os.path.join(SEARCH_DIR, f)
        try:
            out_img, name = stitch_folder(full_path)
        except Exception as e:
            print(f"Skipping {f}: {e}", file=sys.stderr)
            continue

        Image.fromarray(out_img).save(
            os.path.join(RESULTS_DIR, f"{name}.webp"),
            "WEBP",
            quality=90,
            subsampling=0
        )
        print(f"Saved {RESULTS_DIR}/{name}.webp")

if __name__ == "__main__":
    process_all()
