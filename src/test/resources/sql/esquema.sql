-- Esquema minimo de una tienda. Hace de "base de la aplicacion" para poder
-- demostrar las consultas de verificacion y los fixtures desde base.

CREATE TABLE IF NOT EXISTS clientes (
    id          SERIAL PRIMARY KEY,
    usuario     VARCHAR(50)  NOT NULL UNIQUE,
    email       VARCHAR(120) NOT NULL,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS productos (
    id          SERIAL PRIMARY KEY,
    nombre      VARCHAR(120)  NOT NULL,
    precio      NUMERIC(10,2) NOT NULL CHECK (precio >= 0),
    stock       INTEGER       NOT NULL CHECK (stock >= 0),
    categoria   VARCHAR(60)   NOT NULL
);

CREATE TABLE IF NOT EXISTS ordenes (
    id          SERIAL PRIMARY KEY,
    cliente_id  INTEGER       NOT NULL REFERENCES clientes(id),
    total       NUMERIC(10,2) NOT NULL CHECK (total >= 0),
    estado      VARCHAR(20)   NOT NULL DEFAULT 'pendiente',
    creada_en   TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS orden_items (
    id          SERIAL PRIMARY KEY,
    orden_id    INTEGER       NOT NULL REFERENCES ordenes(id) ON DELETE CASCADE,
    producto_id INTEGER       NOT NULL REFERENCES productos(id),
    cantidad    INTEGER       NOT NULL CHECK (cantidad > 0),
    precio_unit NUMERIC(10,2) NOT NULL
);
