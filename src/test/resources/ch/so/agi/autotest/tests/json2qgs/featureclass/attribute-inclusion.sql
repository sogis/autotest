DROP TABLE IF EXISTS json2qgs.attribute_inclusion;

CREATE TABLE json2qgs.attribute_inclusion (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    included_text text NOT NULL,
    excluded_text text NOT NULL,
    geom geometry(Point, 2056) NOT NULL
);

INSERT INTO json2qgs.attribute_inclusion (included_text, excluded_text, geom)
VALUES ('visible', 'hidden', ST_SetSRID(ST_MakePoint(2600000, 1200000), 2056));
