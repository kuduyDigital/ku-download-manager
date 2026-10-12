"""Builds KuDownloader's icons from branding/kudownloader-logo.png.

    python branding/make-icons.py                       # needs Pillow
    pnpm --prefix app tauri icon ../branding/kudownloader-logo.png

The second command makes the desktop app icons (.ico, .icns and the PNG
sizes in app/src-tauri/icons); delete the android/ and ios/ folders it adds.

The tray icon is the drawing without its blue tile: branding/kudownloader-tray.png
when that file exists, otherwise cut out of the logo here.
"""

from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "branding" / "kudownloader-logo.png"
TRAY_SRC = ROOT / "branding" / "kudownloader-tray.png"
BLUE = (1, 110, 250)  # the logo's background blue
RES = ROOT / "android/app/src/main/res"
# The blue area inside the logo's black frame (left, top, right, bottom insets
# of the visible logo; the frame is drawn thicker at the top and left).
INSET = (80, 73, 40, 40)
INNER_RADIUS = 0.24  # of the inner width


def visible(im: Image.Image) -> Image.Image:
    return im.crop(im.getbbox())


def square(im: Image.Image) -> Image.Image:
    """Centred on a transparent square canvas."""
    side = max(im.size)
    out = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    out.paste(im, ((side - im.width) // 2, (side - im.height) // 2))
    return out


def inner(logo: Image.Image) -> Image.Image:
    """The blue tile and drawing, without the black frame (corners rounded off)."""
    l, t, r, b = INSET
    im = logo.crop((l, t, logo.width - r, logo.height - b))
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, im.width - 1, im.height - 1), radius=int(im.width * INNER_RADIUS), fill=255)
    im.putalpha(ImageChops.multiply(im.getchannel("A"), mask))
    return im


def cutout(logo: Image.Image) -> Image.Image:
    """The drawing alone: the blue made transparent (soft edge, blue tint removed)."""
    im = inner(logo)
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            d = ((r - BLUE[0]) ** 2 + (g - BLUE[1]) ** 2 + (b - BLUE[2]) ** 2) ** 0.5
            k = min(1.0, max(0.0, (d - 40) / 110))  # 0 = background blue, 1 = drawing
            if k < 1:
                # Take the blue out of edge pixels so no blue fringe is left.
                r = int(min(255, max(0, (r - BLUE[0] * (1 - k)) / max(k, 0.05))))
                g = int(min(255, max(0, (g - BLUE[1] * (1 - k)) / max(k, 0.05))))
                b = int(min(255, max(0, (b - BLUE[2] * (1 - k)) / max(k, 0.05))))
            px[x, y] = (r, g, b, int(a * k))
    return visible(im)


def save(im: Image.Image, path: Path, size: int | None = None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    (im.resize((size, size), Image.LANCZOS) if size else im).save(path, optimize=True)
    print(path.relative_to(ROOT), f"{size or im.width}px")


def android(logo: Image.Image) -> None:
    tile = square(inner(logo))
    # Adaptive icon (API 26+): blue background layer + the tile as foreground
    # (108 dp = 432 px). Smaller than the visible 72 dp: the background is the
    # same blue, and the drawing stays inside the 66 dp safe zone, so no
    # launcher mask (circle, squircle, teardrop) cuts it.
    fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    art = tile.resize((248, 248), Image.LANCZOS)
    fg.alpha_composite(art, ((432 - 248) // 2, (432 - 248) // 2))
    save(fg, RES / "drawable-nodpi/ic_launcher_art.png")
    # Android 7–7.1: the logo itself, and a round variant.
    save(square(logo), RES / "mipmap-xxxhdpi/ic_launcher.png", 192)
    disc = Image.new("RGBA", (192, 192), BLUE + (255,))
    small = tile.resize((150, 150), Image.LANCZOS)
    disc.alpha_composite(small, (21, 21))
    mask = Image.new("L", (192, 192), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, 191, 191), fill=255)
    disc.putalpha(mask)
    save(disc, RES / "mipmap-xxxhdpi/ic_launcher_round.png")
    # In the app (About screen).
    save(square(logo), RES / "drawable-nodpi/logo.png", 256)


def main() -> None:
    logo = visible(Image.open(SRC).convert("RGBA"))
    android(logo)
    # Desktop: title bar and About (UI); tray (the drawing without its tile).
    save(square(logo), ROOT / "app/src/assets/logo.png", 256)
    tray = visible(Image.open(TRAY_SRC).convert("RGBA")) if TRAY_SRC.exists() else cutout(logo)
    tray = square(tray)
    save(tray.filter(ImageFilter.UnsharpMask(1, 60, 2)), ROOT / "app/src-tauri/icons/tray.png", 64)
    # Browser extension.
    for s in (16, 32, 48, 128):
        save(square(logo), ROOT / f"extension/icons/{s}x{s}.png", s)
    # Website.
    save(square(logo), ROOT / "docs/site/logo.png", 256)
    save(square(logo), ROOT / "docs/site/favicon-32.png", 32)
    save(square(logo), ROOT / "docs/site/apple-touch-icon.png", 180)


if __name__ == "__main__":
    main()
