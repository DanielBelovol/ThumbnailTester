-- Создание таблицы пользователей (UserData)
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    google_id VARCHAR(255) NOT NULL UNIQUE,
    refresh_token VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Создание таблицы миниатюр (ThumbnailData)
CREATE TABLE thumbnails (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,  -- Внешний ключ на таблицу пользователей
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Создание таблицы статистики миниатюр (ThumbnailStats)
CREATE TABLE thumbnail_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    thumbnail_id BIGINT NOT NULL,  -- Внешний ключ на таблицу миниатюр
    ctr DOUBLE,
    impressions INT,
    views INT,
    FOREIGN KEY (thumbnail_id) REFERENCES thumbnails(id)
);
