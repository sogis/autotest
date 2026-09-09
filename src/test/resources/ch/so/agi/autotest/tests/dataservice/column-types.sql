DROP TABLE IF EXISTS dataservice.attribute_types;

CREATE TABLE dataservice.attribute_types (
    id integer PRIMARY KEY,
    integer_value integer NOT NULL,
    bigint_value bigint NOT NULL,
    decimal_value numeric(10, 2) NOT NULL,
    boolean_value boolean NOT NULL,
    uuid_value uuid NOT NULL,
    date_value date NOT NULL,
    datetime_value timestamp without time zone NOT NULL,
    varchar_value varchar(100) NOT NULL,
    text_value text NOT NULL,
    json_value json NOT NULL,
    jsonb_value jsonb NOT NULL,
    nullable_text_value text
);

INSERT INTO dataservice.attribute_types (
    id, integer_value, bigint_value, decimal_value, boolean_value, uuid_value,
    date_value, datetime_value, varchar_value, text_value, json_value, jsonb_value,
    nullable_text_value
) VALUES (
    1, 42, 2147483648, 12.34, true, '123e4567-e89b-12d3-a456-426614174000',
    '2025-02-03', '2025-02-03T04:05:06', 'Ada', 'Public datatype fixture',
    '{"category":"example","rank":2}',
    '{"category":"example","rank":2}', NULL
);
