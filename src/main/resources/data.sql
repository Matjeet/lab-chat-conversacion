-- Datos de ejemplo para el recurso Product (solo perfil dev / H2).
-- Se carga tras la creacion del esquema por Hibernate gracias a
-- spring.jpa.defer-datasource-initialization=true. Borra este archivo junto con el paquete "sample".

insert into products (name, description, price, sku, active, version, created_at, updated_at) values
  ('Teclado mecanico', 'Teclado retroiluminado con switches rojos', 79.90, 'SKU-001', true, 0, current_timestamp, current_timestamp),
  ('Mouse inalambrico', 'Mouse ergonomico 2.4GHz', 39.50, 'SKU-002', true, 0, current_timestamp, current_timestamp),
  ('Monitor 27"', 'Panel IPS 144Hz QHD', 289.00, 'SKU-003', false, 0, current_timestamp, current_timestamp);
