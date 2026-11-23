-- Run on a copy of the existing MySQL data before adopting Flyway V1.
-- Results are counts and contain no email addresses or tokens.
SELECT 'duplicate normalized email' AS check_name, COUNT(*) AS groups_found
FROM (SELECT LOWER(TRIM(email)) FROM users WHERE email IS NOT NULL
      GROUP BY LOWER(TRIM(email)) HAVING COUNT(*) > 1) AS findings
UNION ALL
SELECT 'duplicate proxy per product and user', COUNT(*)
FROM (SELECT product_id, user_id FROM bid_proxy WHERE product_id IS NOT NULL AND user_id IS NOT NULL
      GROUP BY product_id, user_id HAVING COUNT(*) > 1) AS findings
UNION ALL
SELECT 'duplicate chatroom per product', COUNT(*)
FROM (SELECT product_id FROM chatroom WHERE product_id IS NOT NULL
      GROUP BY product_id HAVING COUNT(*) > 1) AS findings
UNION ALL
SELECT 'duplicate image position', COUNT(*)
FROM (SELECT product_id, sort FROM product_image WHERE product_id IS NOT NULL
      GROUP BY product_id, sort HAVING COUNT(*) > 1) AS findings
UNION ALL
SELECT 'duplicate category name', COUNT(*)
FROM (SELECT name FROM categories WHERE name IS NOT NULL
      GROUP BY name HAVING COUNT(*) > 1) AS findings
UNION ALL
SELECT 'invalid auction dates or amounts', COUNT(*) FROM products
WHERE start_at IS NULL OR end_at IS NULL OR end_at <= start_at OR start_price IS NULL OR start_price < 0
   OR min_increment IS NULL OR min_increment <= 0 OR status IS NULL OR seller_idx IS NULL
UNION ALL
SELECT 'current price exceeds recorded winning proxy limit', COUNT(*)
FROM products p JOIN bid_proxy b ON b.product_id = p.product_id AND b.user_id = p.leader_idx
WHERE p.status = 'RUNNING' AND p.current_price > b.max_price;
