package com.skep.field;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 앱 WebView 로 보여줄 카카오맵 HTML — lat/lng/radius 는 JS 가 URL 에서 직접 파싱. */
@RestController
@RequestMapping("/api/field-auth")
public class FieldMapController {

    private final String kakaoJsKey;

    public FieldMapController(@Value("${skep.kakao.js-key:}") String kakaoJsKey) {
        this.kakaoJsKey = kakaoJsKey;
    }

    @GetMapping(value = "/map", produces = MediaType.TEXT_HTML_VALUE)
    public String map() {
        return """
            <!DOCTYPE html>
            <html lang="ko">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no" />
              <title>현장 지도</title>
              <style>
                html, body, #map { margin: 0; padding: 0; width: 100%%; height: 100%%; }
                .radius-chip {
                  position: absolute; bottom: 10px; right: 10px; z-index: 5;
                  background: #FFFFFF; padding: 6px 10px; font: 12px sans-serif; color: #0F172A;
                  box-shadow: 0 1px 3px rgba(0,0,0,0.15);
                }
              </style>
            </head>
            <body>
              <div id="map"></div>
              <div class="radius-chip" id="chip">반경</div>
              <script src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=%s&libraries=services"></script>
              <script>
                const q = new URLSearchParams(location.search);
                const lat = parseFloat(q.get('lat'));
                const lng = parseFloat(q.get('lng'));
                const radius = parseInt(q.get('radius') || '100', 10);
                const userLat = parseFloat(q.get('user_lat'));
                const userLng = parseFloat(q.get('user_lng'));
                const chip = document.getElementById('chip');
                chip.textContent = '현장 반경 ' + radius + 'm';
                if (!isFinite(lat) || !isFinite(lng)) {
                  document.getElementById('map').innerHTML = '<div style=\\"padding:20px;color:#64748B\\">현장 좌표가 설정되지 않았습니다</div>';
                } else {
                  const center = new kakao.maps.LatLng(lat, lng);
                  // 반경에 맞는 적절한 초기 레벨: 100m=2, 500m=4, 1km=5, 5km=7, 50km=10
                  let initialLevel = 5;
                  if (radius <= 100) initialLevel = 2;
                  else if (radius <= 300) initialLevel = 3;
                  else if (radius <= 1000) initialLevel = 4;
                  else if (radius <= 3000) initialLevel = 6;
                  else if (radius <= 10000) initialLevel = 7;
                  else initialLevel = 9;
                  const map = new kakao.maps.Map(document.getElementById('map'), { center, level: initialLevel });
                  new kakao.maps.Circle({
                    map, center, radius,
                    strokeWeight: 2, strokeColor: '#2563EB', strokeOpacity: 0.8, strokeStyle: 'solid',
                    fillColor: '#3B82F6', fillOpacity: 0.18,
                  });
                  new kakao.maps.Marker({ map, position: center });
                  if (isFinite(userLat) && isFinite(userLng)) {
                    const userPos = new kakao.maps.LatLng(userLat, userLng);
                    const accuracy = parseFloat(q.get('accuracy') || '0');
                    if (accuracy > 0) {
                      new kakao.maps.Circle({
                        map, center: userPos, radius: accuracy,
                        strokeWeight: 1, strokeColor: '#E53935', strokeOpacity: 0.4, strokeStyle: 'dashed',
                        fillColor: '#E53935', fillOpacity: 0.08,
                      });
                    }
                    const dotSvg = encodeURIComponent(
                      '<svg xmlns="http://www.w3.org/2000/svg" width="44" height="44">' +
                      '<circle cx="22" cy="22" r="20" fill="rgba(229,57,53,0.25)"/>' +
                      '<circle cx="22" cy="22" r="10" fill="#E53935" stroke="white" stroke-width="3"/>' +
                      '</svg>'
                    );
                    new kakao.maps.Marker({
                      map, position: userPos,
                      image: new kakao.maps.MarkerImage(
                        'data:image/svg+xml;utf8,' + dotSvg,
                        new kakao.maps.Size(44, 44),
                        { offset: new kakao.maps.Point(22, 22) }
                      ),
                    });
                    const label = new kakao.maps.CustomOverlay({
                      map, position: userPos, yAnchor: 2.2,
                      content: '<div style=\\"background:#E53935;color:white;padding:2px 8px;font:bold 11px sans-serif;border-radius:10px;white-space:nowrap;\\">내 위치</div>',
                    });
                    // setBounds 안 함 — 사용자가 자유롭게 줌. 본인 위치가 범위 밖이어도 마커는 표시됨.
                  }
                }
              </script>
            </body>
            </html>
            """.formatted(kakaoJsKey);
    }
}
