CREATE TABLE users (
    user_idx BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255),
    password VARCHAR(255),
    name VARCHAR(255),
    created_at DATETIME(6),
    role VARCHAR(255)
);

CREATE TABLE categories (
    cate_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255)
);

CREATE TABLE products (
    product_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255),
    content VARCHAR(255),
    start_at DATETIME(6),
    end_at DATETIME(6),
    start_price INT,
    min_increment INT,
    buy_now INT,
    current_price INT,
    status ENUM('CANCELED', 'ENDED', 'RUNNING', 'SCHEDULED'),
    seller_idx BIGINT,
    winner_idx BIGINT,
    cate_id INT,
    leader_idx BIGINT,
    FOREIGN KEY (seller_idx) REFERENCES users (user_idx),
    FOREIGN KEY (winner_idx) REFERENCES users (user_idx),
    FOREIGN KEY (cate_id) REFERENCES categories (cate_id),
    FOREIGN KEY (leader_idx) REFERENCES users (user_idx)
);

CREATE TABLE product_image (
    image_idx BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    url VARCHAR(255),
    sort INT NOT NULL,
    product_id BIGINT,
    FOREIGN KEY (product_id) REFERENCES products (product_id)
);

CREATE TABLE bid_proxy (
    proxy_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    max_price INT,
    product_id BIGINT,
    user_id BIGINT,
    FOREIGN KEY (product_id) REFERENCES products (product_id),
    FOREIGN KEY (user_id) REFERENCES users (user_idx)
);

CREATE TABLE bid_details (
    bid_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    amount INT,
    created_at DATETIME(6),
    product_id BIGINT,
    winner_id BIGINT,
    FOREIGN KEY (product_id) REFERENCES products (product_id),
    FOREIGN KEY (winner_id) REFERENCES users (user_idx)
);

CREATE TABLE chatroom (
    chat_room_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    buyer_id BIGINT,
    sellar_id BIGINT,
    product_id BIGINT,
    FOREIGN KEY (buyer_id) REFERENCES users (user_idx),
    FOREIGN KEY (sellar_id) REFERENCES users (user_idx),
    FOREIGN KEY (product_id) REFERENCES products (product_id)
);

CREATE TABLE chat_message (
    chat_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    content VARCHAR(255),
    created_at DATETIME(6),
    sender_id BIGINT,
    room_id BIGINT,
    FOREIGN KEY (sender_id) REFERENCES users (user_idx),
    FOREIGN KEY (room_id) REFERENCES chatroom (chat_room_id)
);

CREATE TABLE refresh_tokens (
    email VARCHAR(255) NOT NULL PRIMARY KEY,
    token VARCHAR(255)
);
