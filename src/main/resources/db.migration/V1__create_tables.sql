-- Таблица пользователей
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    google_id VARCHAR(255) NOT NULL UNIQUE,
    refresh_token VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Таблица миниатюр
CREATE TABLE thumbnails (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    video_url VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT FALSE,
    user_id BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Таблица статистики миниатюр
CREATE TABLE thumbnail_stats (
    id BIGSERIAL PRIMARY KEY,
    thumbnail_id BIGINT NOT NULL,
    views INT,
    ctr DOUBLE PRECISION,
    impressions INT,
    average_view_duration DOUBLE PRECISION,
    adv_ctr DOUBLE PRECISION,
    comments INT,
    likes INT,
    subscribers_gained INT,
    average_view_percentage DOUBLE PRECISION,
    total_watch_time INT,
    FOREIGN KEY (thumbnail_id) REFERENCES thumbnails(id) ON DELETE CASCADE
);

-- Таблица конфигураций тестов миниатюр
CREATE TABLE thumbnail_test_config (
    id BIGSERIAL PRIMARY KEY,
    test_type VARCHAR(50) NOT NULL,
    testing_type VARCHAR(50) NOT NULL,
    testing_by_time_minutes BIGINT,
    testing_by_metrics BIGINT,
    criterion_of_winner VARCHAR(50) NOT NULL,
    thumbnail_id BIGINT NOT NULL UNIQUE,
    FOREIGN KEY (thumbnail_id) REFERENCES thumbnails(id) ON DELETE CASCADE
);
