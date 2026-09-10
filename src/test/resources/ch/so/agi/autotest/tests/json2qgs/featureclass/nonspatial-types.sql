DROP TABLE IF EXISTS json2qgs.nonspatial_types;

CREATE TABLE json2qgs.nonspatial_types (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    text_value text NOT NULL,
    integer_value integer NOT NULL,
    decimal_value numeric(10, 2) NOT NULL,
    boolean_value boolean NOT NULL,
    uuid_value uuid NOT NULL,
    json_value jsonb NOT NULL,
    date_value date NOT NULL,
    time_value time NOT NULL,
    timestamp_value timestamp NOT NULL,
    nullable_value text,
    geom geometry(Point, 2056) NOT NULL
);

INSERT INTO json2qgs.nonspatial_types
    (text_value, integer_value, decimal_value, boolean_value, uuid_value, json_value,
     date_value, time_value, timestamp_value, nullable_value, geom)
VALUES
    ('text', 42, 12.34, true, '123e4567-e89b-12d3-a456-426614174000', '{"kind":"fixture"}',
     DATE '2024-01-02', TIME '03:04:05', TIMESTAMP '2024-01-02 03:04:05', NULL,
     ST_SetSRID(ST_MakePoint(2600000, 1200000), 2056));
