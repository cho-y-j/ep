"""박스+텍스트 리스트를 구글 비전과 같은 읽기순서의 fullText 로 재구성.

verify-api Java 파서(OcrExtractController)는 fullText(단일 문자열)에 정규식을 건다.
구글 비전은 textAnnotations[0].description 을 위→아래·왼→오 순서로 준다.
PaddleOCR 은 박스별 텍스트를 주므로 같은 순서로 재조립해야 파서가 무변경으로 동작한다.

알고리즘:
  1) 박스 세로중심(cy) 오름차순 정렬.
  2) y-밴드 클러스터: 직전 밴드 평균 cy 와의 차이가 (두 박스 높이 중 큰 값의 0.5배)
     이내면 같은 줄로 묶는다(세로로 50% 이상 겹치는 박스 = 같은 줄).
  3) 밴드 내부는 x(왼쪽 좌표) 오름차순 정렬.
  4) 밴드 안은 공백으로, 밴드 사이는 '\n' 으로 join → fullText.
"""


def _geom(box):
    xs = [p[0] for p in box]
    ys = [p[1] for p in box]
    return min(xs), min(ys), max(xs), max(ys)


def reconstruct(detections):
    """detections: [{text, score, box:[[x,y]x4]}].

    return: (full_text, ordered_lines)
      full_text: 읽기순서로 재조립된 단일 문자열 (파서 입력)
      ordered_lines: 같은 읽기순서의 [{text, score, box}] (박스 단위, adapter/마스킹용)
    """
    items = []
    for d in detections:
        x0, y0, x1, y1 = _geom(d["box"])
        items.append({
            "text": d["text"], "score": d["score"], "box": d["box"],
            "x": x0, "cy": (y0 + y1) / 2.0, "h": max(1.0, float(y1 - y0)),
        })
    items.sort(key=lambda it: it["cy"])

    bands = []
    for it in items:
        if bands:
            last = bands[-1]
            ref_cy = last["cy_sum"] / last["n"]
            tol = 0.5 * max(it["h"], last["h_max"])
            if abs(it["cy"] - ref_cy) <= tol:
                last["items"].append(it)
                last["cy_sum"] += it["cy"]
                last["n"] += 1
                last["h_max"] = max(last["h_max"], it["h"])
                continue
        bands.append({"items": [it], "cy_sum": it["cy"], "n": 1, "h_max": it["h"]})

    line_texts = []
    ordered = []
    for band in bands:
        band["items"].sort(key=lambda it: it["x"])
        line_texts.append(" ".join(it["text"] for it in band["items"]))
        for it in band["items"]:
            ordered.append({"text": it["text"], "score": it["score"], "box": it["box"]})

    return "\n".join(line_texts), ordered
