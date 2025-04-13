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
    total_watch_time INT,  -- в секундах, например
    FOREIGN KEY (thumbnail_id) REFERENCES thumbnails(id) ON DELETE CASCADE
);
