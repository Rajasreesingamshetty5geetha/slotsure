-- Runs once, on first start of an empty MySQL volume.

CREATE DATABASE IF NOT EXISTS slot_db    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS order_db   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS payment_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS dispatch_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'slotsure'@'%' IDENTIFIED BY 'slotsure';

GRANT ALL PRIVILEGES ON slot_db.*    TO 'slotsure'@'%';
GRANT ALL PRIVILEGES ON order_db.*   TO 'slotsure'@'%';
GRANT ALL PRIVILEGES ON payment_db.* TO 'slotsure'@'%';
GRANT ALL PRIVILEGES ON dispatch_db.* TO 'slotsure'@'%';

FLUSH PRIVILEGES;
