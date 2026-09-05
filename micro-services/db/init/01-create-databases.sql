-- Um banco por serviço: cada Flyway precisa da sua própria
-- flyway_schema_history, senão o segundo serviço a subir quebra
-- com checksum mismatch no V1.
CREATE DATABASE user_db;
CREATE DATABASE inventory_db;
CREATE DATABASE order_db;
CREATE DATABASE shipment_db;
CREATE DATABASE payment_db;
