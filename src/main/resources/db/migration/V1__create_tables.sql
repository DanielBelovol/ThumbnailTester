-- Table users
CREATE TABLE users
(
    id            BIGSERIAL PRIMARY KEY,
    google_id     VARCHAR(255) NOT NULL UNIQUE,
    refresh_token VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table thumbnails
CREATE TABLE thumbnails
(
    id        BIGSERIAL PRIMARY KEY,
    video_url VARCHAR(255) NOT NULL,
    user_id   BIGINT       NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Table image_options
CREATE TABLE image_options
(
    id           BIGSERIAL PRIMARY KEY,
    file_url  TEXT   NOT NULL,
    text         VARCHAR(255),
    is_winner    BOOLEAN DEFAULT FALSE,
    thumbnail_id BIGINT NOT NULL,
    FOREIGN KEY (thumbnail_id) REFERENCES thumbnails (id) ON DELETE CASCADE
);

-- Table thumbnail_stats
CREATE TABLE thumbnail_stats
(
    id                      BIGSERIAL PRIMARY KEY,
    thumbnail_id            BIGINT NOT NULL,
    views                   INT,
    ctr                     DOUBLE PRECISION,
    impressions             INT,
    average_view_duration   DOUBLE PRECISION,
    adv_ctr                 DOUBLE PRECISION,
    comments                INT,
    likes                   INT,
    subscribers_gained      INT,
    average_view_percentage DOUBLE PRECISION,
    total_watch_time        INT,
    FOREIGN KEY (thumbnail_id) REFERENCES image_options (id) ON DELETE CASCADE
);

-- Table thumbnail-test-config
CREATE TABLE thumbnail_test_config
(
    id                      BIGSERIAL PRIMARY KEY,
    test_type               VARCHAR(50) NOT NULL,
    testing_type            VARCHAR(50) NOT NULL,
    testing_by_time_minutes BIGINT,
    testing_by_metrics      BIGINT,
    criterion_of_winner     VARCHAR(50) NOT NULL,
    thumbnail_id            BIGINT      NOT NULL UNIQUE,
    FOREIGN KEY (thumbnail_id) REFERENCES thumbnails (id) ON DELETE CASCADE
);
