package com.skep.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 저장소 추상화. 지금은 로컬 디스크, 나중에 S3 구현체로 갈아끼움.
 *
 * key = 저장소 내부 식별자. ex: "2026/04/uuid.bin"
 *  - 호출자가 ownerType/ownerId 같은 도메인 정보를 알 필요 없게 단순 key 만 다룸
 *  - 실제 디스크/버킷 위치는 구현체가 결정
 */
public interface FileStorage {
    /** 파일을 저장하고 key 를 반환. */
    String store(MultipartFile file);

    /** byte 배열을 저장하고 key 를 반환 (서버 측 생성 콘텐츠용). */
    String storeBytes(byte[] bytes, String suggestedExtension);

    /** 저장된 파일 리소스를 반환 (다운로드용). */
    Resource load(String key);

    /** key 의 내용을 새 byte 로 덮어씀 (없으면 생성). OnlyOffice 콜백 등 in-place 갱신용. */
    void overwrite(String key, byte[] bytes);

    /** 저장된 파일 삭제. 없어도 예외 없음. */
    void delete(String key);
}
