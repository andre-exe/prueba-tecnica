CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE reservations (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID          NOT NULL REFERENCES users (id),
    space_id       UUID          NOT NULL REFERENCES spaces (id),
    start_time     TIMESTAMPTZ   NOT NULL,
    end_time       TIMESTAMPTZ   NOT NULL,
    attendees      INTEGER       NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    total_price    NUMERIC(10,2) NOT NULL,
    payment_method VARCHAR(100)  NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_reservation_time CHECK (end_time > start_time),
    CONSTRAINT chk_reservation_attendees CHECK (attendees > 0),
    CONSTRAINT chk_reservation_price CHECK (total_price >= 0),
    CONSTRAINT chk_reservation_status CHECK (status IN ('PENDING', 'PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'COMPLETED'))
);

-- esta es la red de seguridad final: aunque dos requests pasen la validacion a la vez, la bd no deja guardar dos reservas activas que se pisen en el mismo espacio
-- el rango es [) asi que una que termina a las 11:00 y otra que empieza a las 11:00 no chocan
ALTER TABLE reservations
    ADD CONSTRAINT no_overlapping_reservations
    EXCLUDE USING gist (
        space_id WITH =,
        tstzrange(start_time, end_time, '[)') WITH &&
    ) WHERE (status IN ('PENDING', 'PENDING_PAYMENT', 'CONFIRMED'));

CREATE INDEX idx_reservations_space_time ON reservations (space_id, start_time, end_time);
CREATE INDEX idx_reservations_user ON reservations (user_id);
CREATE INDEX idx_reservations_status ON reservations (status);
