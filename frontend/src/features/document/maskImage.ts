type Box = [number, number, number, number]; // x, y, w, h (원본 이미지 px)

/**
 * 원본 이미지 위에 검정 박스(원본 px [x,y,w,h])를 실제 픽셀로 칠해(burn) 새 JPEG File 을 만든다.
 * 서류 수집(무로그인)에서 자동(주민번호) 마스킹 위에 사용자가 가릴 곳을 덮는 용도 — 자동+수동 픽셀이 중첩된다.
 * 박스가 없거나 캔버스가 없으면 원본 File 을 그대로 돌려준다(fail-open). warpImage.ts 관례 동일.
 */
export async function maskImageByBoxes(file: File, boxes: Box[]): Promise<File> {
  if (boxes.length === 0) return file;
  const bmp = await createImageBitmap(file);
  const c = document.createElement('canvas');
  c.width = bmp.width;
  c.height = bmp.height;
  const ctx = c.getContext('2d');
  if (!ctx) { bmp.close(); return file; }
  ctx.drawImage(bmp, 0, 0);
  bmp.close();
  ctx.fillStyle = '#000';
  for (const [x, y, w, h] of boxes) ctx.fillRect(x, y, w, h);
  const name = file.name.replace(/(\.[a-z0-9]+)?$/i, '') + '-masked.jpg';
  return new Promise((resolve) =>
    c.toBlob((blob) => resolve(blob ? new File([blob], name, { type: 'image/jpeg' }) : file), 'image/jpeg', 0.92),
  );
}
