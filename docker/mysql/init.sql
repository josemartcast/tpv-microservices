CREATE DATABASE IF NOT EXISTS tpv_auth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS tpv_pos CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS tpv_billing CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'tpv_user'@'%' IDENTIFIED BY 'tpv_pass';
CREATE USER IF NOT EXISTS 'tpv_user'@'localhost' IDENTIFIED BY 'tpv_pass';
CREATE USER IF NOT EXISTS 'tpv_user'@'127.0.0.1' IDENTIFIED BY 'tpv_pass';

GRANT ALL PRIVILEGES ON tpv_auth.* TO 'tpv_user'@'%';
GRANT ALL PRIVILEGES ON tpv_pos.* TO 'tpv_user'@'%';
GRANT ALL PRIVILEGES ON tpv_billing.* TO 'tpv_user'@'%';
GRANT ALL PRIVILEGES ON tpv_auth.* TO 'tpv_user'@'localhost';
GRANT ALL PRIVILEGES ON tpv_pos.* TO 'tpv_user'@'localhost';
GRANT ALL PRIVILEGES ON tpv_billing.* TO 'tpv_user'@'localhost';
GRANT ALL PRIVILEGES ON tpv_auth.* TO 'tpv_user'@'127.0.0.1';
GRANT ALL PRIVILEGES ON tpv_pos.* TO 'tpv_user'@'127.0.0.1';
GRANT ALL PRIVILEGES ON tpv_billing.* TO 'tpv_user'@'127.0.0.1';

FLUSH PRIVILEGES;
