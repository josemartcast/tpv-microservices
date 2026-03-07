CREATE DATABASE IF NOT EXISTS tpv_auth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS tpv_pos CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS tpv_billing CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON tpv_auth.* TO 'tpv_user'@'%';
GRANT ALL PRIVILEGES ON tpv_pos.* TO 'tpv_user'@'%';
GRANT ALL PRIVILEGES ON tpv_billing.* TO 'tpv_user'@'%';

FLUSH PRIVILEGES;
