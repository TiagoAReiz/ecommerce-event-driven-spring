-- Adiciona motivo de cancelamento ao envio
ALTER TABLE shipment ADD COLUMN cancel_reason TEXT;
