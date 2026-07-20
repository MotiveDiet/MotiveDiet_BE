ALTER TABLE `user`
    ADD COLUMN `USER_GOOGLE_ID` VARCHAR(255) NULL;

CREATE UNIQUE INDEX `UK_USER_GOOGLE_ID`
    ON `user` (`USER_GOOGLE_ID`);

ALTER TABLE `user`
    ADD COLUMN `USER_GOAL_WEIGHT` DOUBLE NULL,
    ADD COLUMN `USER_GOAL_DATE` DATE NULL;

-- ── Phase 1 (feat/#3): 온보딩 파싱 + 로깅 MVP ──
-- 머지 전에 반드시 이 블록을 prod DB 에 먼저 적용할 것. 이모지 저장을 위해 새 테이블은 utf8mb4.

CREATE TABLE `motive_signal` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT NOT NULL,
    `motive_type` VARCHAR(255) NOT NULL,
    `target`      VARCHAR(255) NULL,
    `event_date`  DATE NULL,
    `paraphrase`  VARCHAR(255) NULL,
    PRIMARY KEY (`id`)
) DEFAULT CHARSET = utf8mb4;

CREATE TABLE `food_category` (
    `id`               BIGINT NOT NULL,
    `name`             VARCHAR(255) NOT NULL,
    `emoji`            VARCHAR(255) NOT NULL,
    `weekly_threshold` INT NOT NULL,
    PRIMARY KEY (`id`)
) DEFAULT CHARSET = utf8mb4;

CREATE TABLE `favorite_food` (
    `id`               BIGINT NOT NULL AUTO_INCREMENT,
    `user_id`          BIGINT NOT NULL,
    `food_category_id` BIGINT NOT NULL,
    `slot_order`       INT NOT NULL,
    PRIMARY KEY (`id`),
    -- 동시 슬롯 추가가 같은 slot_order 를 쓰는 것을 DB 레벨에서 막는다 (경쟁 상태 → 제약 위반)
    UNIQUE KEY `UK_FAVORITE_FOOD_SLOT` (`user_id`, `slot_order`)
) DEFAULT CHARSET = utf8mb4;

CREATE TABLE `food_log` (
    `id`               BIGINT NOT NULL AUTO_INCREMENT,
    `user_id`          BIGINT NOT NULL,
    `food_category_id` BIGINT NOT NULL,
    `logged_at`        DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`)
) DEFAULT CHARSET = utf8mb4;

-- ── Phase 2 (feat/#4): 팩폭 코칭 엔진 — 코칭 설정 + opt-in 동의 ──
-- 머지 전에 반드시 이 블록을 prod DB 에 먼저 적용할 것 (ddl-auto: validate).
-- 설정 3개는 NOT NULL DEFAULT 로 추가한다: 기존 행은 DEFAULT 로 채워지고, 배포 중인 구버전 앱이
-- 이 컬럼 없이 INSERT 해도 DEFAULT 가 먹으므로 안전하다. consented_at 은 "미동의=null" 이라 nullable.
ALTER TABLE `user`
    ADD COLUMN `USER_INTENSITY_LEVEL`         VARCHAR(255) NOT NULL DEFAULT 'MILD',
    ADD COLUMN `USER_FREQUENCY_LAYER_ENABLED` TINYINT(1)   NOT NULL DEFAULT 1,
    ADD COLUMN `USER_MOTIVE_COMBO_ENABLED`    TINYINT(1)   NOT NULL DEFAULT 1,
    ADD COLUMN `USER_CONSENTED_AT`            DATETIME(6)  NULL;
