"""이미지 빌드 시 1회 실행 — PP-OCRv5 모델을 이미지 안에 캐시.

빌드 단계에서 엔진을 생성하고 더미 이미지로 predict 를 돌려 검출/인식 모델을
내려받아 캐시하므로, 컨테이너는 네트워크 없이(오프라인) 기동할 수 있다.
"""

import cv2
import numpy as np

from app import ocr_engine


def main():
    img = np.full((60, 400, 3), 255, np.uint8)
    cv2.putText(img, "test 123", (10, 44), cv2.FONT_HERSHEY_SIMPLEX, 1.2, (0, 0, 0), 2)
    ocr_engine.run(img)
    print("[preload] PaddleOCR models cached")


if __name__ == "__main__":
    main()
