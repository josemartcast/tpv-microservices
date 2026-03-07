USE tpv_pos;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO categories (active, created_at, name, updated_at) VALUES
(b'1', NOW(6), 'Bebidas', NOW(6)),
(b'1', NOW(6), 'Cervezas', NOW(6)),
(b'1', NOW(6), 'Entrantes', NOW(6)),
(b'1', NOW(6), 'Platos', NOW(6)),
(b'1', NOW(6), 'Postres', NOW(6)),
(b'1', NOW(6), 'Cafes', NOW(6));

INSERT IGNORE INTO products (active, created_at, name, price_cents, updated_at, vat_rate_bps, category_id)
SELECT b'1', NOW(6), 'Agua 50cl', 150, NOW(6), 1000, c.id FROM categories c WHERE c.name = 'Bebidas';
INSERT IGNORE INTO products (active, created_at, name, price_cents, updated_at, vat_rate_bps, category_id)
SELECT b'1', NOW(6), 'Refresco', 250, NOW(6), 1000, c.id FROM categories c WHERE c.name = 'Bebidas';
INSERT IGNORE INTO products (active, created_at, name, price_cents, updated_at, vat_rate_bps, category_id)
SELECT b'1', NOW(6), 'Zumo Naranja', 280, NOW(6), 1000, c.id FROM categories c WHERE c.name = 'Bebidas';

INSERT IGNORE INTO products (active, created_at, name, price_cents, updated_at, vat_rate_bps, category_id)
SELECT b'1', NOW(6), CONVERT(0x43657276657A61204361C3B161 USING utf8mb4), 220, NOW(6), 1000, c.id FROM categories c WHERE c.name = 'Cervezas';
INSERT IGNORE INTO products (active, created_at, name, price_cents, updated_at, vat_rate_bps, category_id)
SELECT b'1', NOW(6), 'Cerveza Doble', 320, NOW(6), 1000, c.id FROM categories c WHERE c.name = 'Cervezas';
INSERT IGNORE INTO products (active, created_at, name, price_cents, updated_at, vat_rate_bps, category_id)
SELECT b'1', NOW(6), 'Cerveza 0,0', 250, NOW(6), 1000, c.id FROM categories c WHERE c.name = 'Cervezas';

INSERT IGNORE INTO products (active, created_at, name, price_cents, updated_at, vat_rate_bps, category_id)
SELECT b'1', NOW(6), 'Bravas', 700, NOW(6), 1000, c.id FROM categories c WHERE c.name = 'Entrantes';
INSERT IGNORE INTO products (active, created_at, name, price_cents, updated_at, vat_rate_bps, category_id)
SELECT b'1', NOW(6), 'Ensaladilla', 650, NOW(6), 1000, c.id FROM categories c WHERE c.name = 'Entrantes';
INSERT IGNORE INTO products (active, created_at, name, price_cents, updated_at, vat_rate_bps, category_id)
SELECT b'1', NOW(6), 'Calamares', 950, NOW(6), 1000, c.id FROM categories c WHERE c.name = 'Entrantes';

INSERT IGNORE INTO products (active, created_at, name, price_cents, updated_at, vat_rate_bps, category_id)
SELECT b'1', NOW(6), 'Hamburguesa', 1150, NOW(6), 1000, c.id FROM categories c WHERE c.name = 'Platos';
INSERT IGNORE INTO products (active, created_at, name, price_cents, updated_at, vat_rate_bps, category_id)
SELECT b'1', NOW(6), 'Pizza Margarita', 1200, NOW(6), 1000, c.id FROM categories c WHERE c.name = 'Platos';
INSERT IGNORE INTO products (active, created_at, name, price_cents, updated_at, vat_rate_bps, category_id)
SELECT b'1', NOW(6), 'Entrecot', 1800, NOW(6), 1000, c.id FROM categories c WHERE c.name = 'Platos';

INSERT IGNORE INTO products (active, created_at, name, price_cents, updated_at, vat_rate_bps, category_id)
SELECT b'1', NOW(6), 'Tarta Queso', 550, NOW(6), 1000, c.id FROM categories c WHERE c.name = 'Postres';
INSERT IGNORE INTO products (active, created_at, name, price_cents, updated_at, vat_rate_bps, category_id)
SELECT b'1', NOW(6), 'Helado', 450, NOW(6), 1000, c.id FROM categories c WHERE c.name = 'Postres';

INSERT IGNORE INTO products (active, created_at, name, price_cents, updated_at, vat_rate_bps, category_id)
SELECT b'1', NOW(6), 'Cafe Solo', 150, NOW(6), 1000, c.id FROM categories c WHERE c.name = 'Cafes';
INSERT IGNORE INTO products (active, created_at, name, price_cents, updated_at, vat_rate_bps, category_id)
SELECT b'1', NOW(6), 'Cafe con Leche', 180, NOW(6), 1000, c.id FROM categories c WHERE c.name = 'Cafes';
