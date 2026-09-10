DROP TABLE IF EXISTS json2qgs.vector_point;
DROP TABLE IF EXISTS json2qgs.vector_line;
DROP TABLE IF EXISTS json2qgs.vector_polygon;
DROP TABLE IF EXISTS json2qgs.vector_multipart_polygon;

CREATE TABLE json2qgs.vector_point (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    geom geometry(Point, 2056) NOT NULL
);
CREATE TABLE json2qgs.vector_line (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    geom geometry(LineString, 2056) NOT NULL
);
CREATE TABLE json2qgs.vector_polygon (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    geom geometry(Polygon, 2056) NOT NULL
);
CREATE TABLE json2qgs.vector_multipart_polygon (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    geom geometry(MultiPolygon, 2056) NOT NULL
);

INSERT INTO json2qgs.vector_point (geom)
VALUES (ST_SetSRID(ST_MakePoint(2600050, 1200050), 2056));
INSERT INTO json2qgs.vector_line (geom)
VALUES (ST_SetSRID(ST_MakeLine(ST_MakePoint(2600020, 1200050), ST_MakePoint(2600080, 1200050)), 2056));
INSERT INTO json2qgs.vector_polygon (geom)
VALUES (ST_GeomFromText('POLYGON((2600020 1200060,2600040 1200060,2600040 1200080,2600020 1200080,2600020 1200060))', 2056));
INSERT INTO json2qgs.vector_multipart_polygon (geom)
VALUES (ST_GeomFromText('MULTIPOLYGON(((2600060 1200020,2600075 1200020,2600075 1200035,2600060 1200035,2600060 1200020)),((2600060 1200060,2600075 1200060,2600075 1200075,2600060 1200075,2600060 1200060)))', 2056));
