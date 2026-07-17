/** 이미지 파일을 시계방향 90° 회전한 새 File 로 반환 (canvas). 세로로 누운 폰 사진을 똑바로 세워
 *  4모서리 정렬·저장할 때 사용. 장비 등록증·인원 서류 업로드에서 공통으로 쓴다. */
export async function rotateImage90(file: File): Promise<File> {
  const bmp = await createImageBitmap(file);
  const canvas = document.createElement('canvas');
  canvas.width = bmp.height; // 90° 회전이라 가로·세로 교체
  canvas.height = bmp.width;
  const ctx = canvas.getContext('2d');
  if (!ctx) { bmp.close(); return file; }
  ctx.translate(canvas.width / 2, canvas.height / 2);
  ctx.rotate(Math.PI / 2);
  ctx.drawImage(bmp, -bmp.width / 2, -bmp.height / 2);
  bmp.close();
  const type = file.type && file.type.startsWith('image/') ? file.type : 'image/png';
  return new Promise((resolve) =>
    canvas.toBlob((b) => resolve(b ? new File([b], file.name, { type }) : file), type, 0.95),
  );
}
