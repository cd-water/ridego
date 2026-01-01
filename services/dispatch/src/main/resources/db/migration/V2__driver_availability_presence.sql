ALTER TABLE driver_availability
    ADD COLUMN presence varchar(8) NOT NULL DEFAULT 'OFFLINE';
