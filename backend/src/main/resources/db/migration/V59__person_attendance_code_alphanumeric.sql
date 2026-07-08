-- 출퇴근 코드 형식 변경: 6자리 숫자 → 영숫자 6자리 (헷갈림 방지 위해 0OIL1B 제외).
-- 안전 문자 집합: ACDEFGHJKMNPQRSTUVWXYZ23456789 (28자).
-- 모든 기존 person 의 attendance_code 를 영숫자 6자리로 backfill (UNIQUE 충돌 시 random retry).
DO $$
DECLARE
    p_id BIGINT;
    new_code TEXT;
    chars TEXT := 'ACDEFGHJKMNPQRSTUVWXYZ23456789';
    n_chars INT := length(chars);
    i INT;
    retries INT;
BEGIN
    FOR p_id IN SELECT id FROM persons ORDER BY id LOOP
        retries := 0;
        LOOP
            new_code := '';
            FOR i IN 1..6 LOOP
                new_code := new_code || substr(chars, floor(random() * n_chars + 1)::int, 1);
            END LOOP;
            BEGIN
                UPDATE persons SET attendance_code = new_code WHERE id = p_id;
                EXIT;
            EXCEPTION WHEN unique_violation THEN
                retries := retries + 1;
                IF retries > 10 THEN
                    RAISE EXCEPTION '6자리 코드 충돌 (person %)', p_id;
                END IF;
            END;
        END LOOP;
    END LOOP;
END$$;
