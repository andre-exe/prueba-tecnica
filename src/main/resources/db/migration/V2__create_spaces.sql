CREATE TABLE spaces (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(120)  NOT NULL,
    type        VARCHAR(30)   NOT NULL,
    capacity    INTEGER       NOT NULL,
    location    VARCHAR(150)  NOT NULL,
    hourly_rate NUMERIC(10,2) NOT NULL,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version     BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_spaces_name UNIQUE (name),
    CONSTRAINT chk_spaces_type CHECK (type IN ('MEETING_ROOM', 'HOT_DESK', 'PRIVATE_OFFICE')),
    CONSTRAINT chk_spaces_capacity CHECK (capacity > 0),
    CONSTRAINT chk_spaces_rate CHECK (hourly_rate > 0)
);

CREATE INDEX idx_spaces_type ON spaces (type);
CREATE INDEX idx_spaces_active ON spaces (active);
