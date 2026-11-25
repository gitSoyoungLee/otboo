"""
부하 테스트용 '핸드폰 사진 수준' 테스트 이미지 생성기.

- 해상도: 4000x3000 (1200만 화소, 일반적인 폰 카메라 기본 해상도)
- 용량 목표: 3~5MB JPEG
- 실제 사진처럼 압축이 덜 되도록 부드러운 그라디언트 + 도형 + 노이즈를 섞는다.
  (단색 이미지는 수십 KB로 압축돼 버려서 "폰 사진" 조건을 재현하지 못한다)

사용법: python -I loadtest/gen_images.py <출력폴더>
"""
import sys
import os
import numpy as np
from PIL import Image, ImageDraw

W, H = 4000, 3000


def make(seed: int, noise_sigma: float) -> Image.Image:
    rng = np.random.default_rng(seed)
    y, x = np.mgrid[0:H, 0:W].astype(np.float32)
    base = np.zeros((H, W, 3), dtype=np.float32)
    for c in range(3):
        fx, fy = rng.uniform(0.0005, 0.003, size=2)
        px, py = rng.uniform(0, 6.28, size=2)
        base[..., c] = 128 + 60 * np.sin(x * fx + px) * np.cos(y * fy + py)
    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    for _ in range(300):
        x0, y0 = int(rng.integers(0, W)), int(rng.integers(0, H))
        r = int(rng.integers(20, 400))
        color = tuple(int(v) for v in rng.integers(0, 256, size=3))
        if rng.random() < 0.5:
            d.ellipse([x0, y0, x0 + r, y0 + r], fill=color)
        else:
            d.rectangle([x0, y0, x0 + r, y0 + int(r * 0.6)], fill=color)
    arr = np.asarray(img).astype(np.float32)
    arr += rng.normal(0, noise_sigma, size=arr.shape).astype(np.float32)
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for i, sigma in enumerate([7, 8, 9]):
        path = os.path.join(out, f"photo_{i + 1}.jpg")
        make(seed=100 + i, noise_sigma=sigma).save(path, "JPEG", quality=90)
        with Image.open(path) as im:
            print(f"{path}\t{im.size[0]}x{im.size[1]}\t{os.path.getsize(path)} bytes")


if __name__ == "__main__":
    main()
