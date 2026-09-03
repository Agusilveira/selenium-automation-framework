-- Datos sembrados. Deliberadamente incluyen casos que un test deberia distinguir:
-- un cliente inactivo, un producto sin stock y una orden cancelada.

INSERT INTO clientes (usuario, email, activo) VALUES
    ('standard_user',  'standard@tienda.test',  TRUE),
    ('problem_user',   'problem@tienda.test',   TRUE),
    ('locked_out_user','locked@tienda.test',    FALSE);

INSERT INTO productos (nombre, precio, stock, categoria) VALUES
    ('Sauce Labs Backpack',     29.99, 10, 'mochilas'),
    ('Sauce Labs Bike Light',    9.99, 25, 'luces'),
    ('Sauce Labs Bolt T-Shirt', 15.99,  0, 'remeras'),
    ('Sauce Labs Fleece Jacket',49.99,  5, 'abrigos'),
    ('Sauce Labs Onesie',        7.99, 40, 'bebes');

INSERT INTO ordenes (cliente_id, total, estado) VALUES
    (1, 39.98, 'confirmada'),
    (1, 15.99, 'cancelada'),
    (2, 49.99, 'pendiente');

INSERT INTO orden_items (orden_id, producto_id, cantidad, precio_unit) VALUES
    (1, 1, 1, 29.99),
    (1, 2, 1,  9.99),
    (2, 3, 1, 15.99),
    (3, 4, 1, 49.99);
