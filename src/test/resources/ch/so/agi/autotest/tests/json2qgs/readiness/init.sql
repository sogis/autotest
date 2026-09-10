CREATE EXTENSION IF NOT EXISTS postgis;
CREATE SCHEMA json2qgs;

CREATE TABLE json2qgs.readiness_point (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    geom geometry(Point, 2056) NOT NULL
);

INSERT INTO json2qgs.readiness_point (geom)
VALUES (ST_SetSRID(ST_MakePoint(2600000, 1200000), 2056));
