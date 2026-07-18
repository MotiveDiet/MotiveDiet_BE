-- 살찌는 음식 화이트리스트 고정 시드 (ROADMAP-BE Phase 1 / PRD 6.1).
-- 매 부팅 실행되므로(sql.init.mode=always) ON DUPLICATE KEY UPDATE 로 멱등하게 만든다.
-- 이모지 저장에는 food_category 가 utf8mb4 여야 한다 (schema-changes.sql 참고).
INSERT INTO food_category (id, name, emoji, weekly_threshold) VALUES
    (1, '치킨',  '🍗', 2),
    (2, '햄버거', '🍔', 3),
    (3, '라면',  '🍜', 2),
    (4, '빵',   '🥐', 3),
    (5, '술',   '🍺', 2),
    (6, '야식',  '🌙', 2)
ON DUPLICATE KEY UPDATE name = VALUES(name), emoji = VALUES(emoji), weekly_threshold = VALUES(weekly_threshold);
