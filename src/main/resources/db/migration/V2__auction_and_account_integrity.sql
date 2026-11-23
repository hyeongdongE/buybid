ALTER TABLE users ADD CONSTRAINT uk_users_email UNIQUE (email);
ALTER TABLE bid_proxy ADD CONSTRAINT uk_bid_proxy_product_user UNIQUE (product_id, user_id);
ALTER TABLE chatroom ADD CONSTRAINT uk_chatroom_product UNIQUE (product_id);
ALTER TABLE product_image ADD CONSTRAINT uk_product_image_sort UNIQUE (product_id, sort);
ALTER TABLE chat_message MODIFY COLUMN content VARCHAR(1000);

CREATE INDEX ix_products_start ON products (status, start_at, product_id);
CREATE INDEX ix_products_end ON products (status, end_at, product_id);
ALTER TABLE bid_proxy ADD COLUMN rank_order BIGINT;
UPDATE bid_proxy SET rank_order = proxy_id;
ALTER TABLE bid_proxy MODIFY COLUMN rank_order BIGINT NOT NULL;
CREATE INDEX ix_bid_proxy_ranking ON bid_proxy (product_id, max_price DESC, rank_order);
CREATE INDEX ix_chat_message_history ON chat_message (room_id, chat_id);

INSERT INTO categories (name)
SELECT seed.name
FROM (
    SELECT '전자제품' AS name UNION ALL SELECT '패션/잡화' UNION ALL SELECT '가구/생활'
    UNION ALL SELECT '취미/게임/도서' UNION ALL SELECT '기타'
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM categories existing WHERE existing.name = seed.name);
