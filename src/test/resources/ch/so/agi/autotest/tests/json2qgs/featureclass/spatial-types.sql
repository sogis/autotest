DROP TABLE IF EXISTS json2qgs.two_geometries;
DROP TABLE IF EXISTS json2qgs.optional_point;
DROP TABLE IF EXISTS json2qgs.multipart_polygon;

CREATE TABLE json2qgs.two_geometries (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    point_geom geometry(Point, 2056) NOT NULL,
    line_geom geometry(LineString, 2056) NOT NULL
);
CREATE TABLE json2qgs.optional_point (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    geom geometry(Point, 2056)
);
CREATE TABLE json2qgs.multipart_polygon (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    geom geometry(MultiPolygon, 2056) NOT NULL
);

INSERT INTO json2qgs.two_geometries (point_geom, line_geom)
VALUES (
    ST_SetSRID(ST_MakePoint(2600000, 1200000), 2056),
    ST_SetSRID(ST_MakeLine(ST_MakePoint(2600000, 1200000), ST_MakePoint(2600010, 1200010)), 2056)
);
INSERT INTO json2qgs.optional_point (geom) VALUES (NULL);
INSERT INTO json2qgs.multipart_polygon (geom)
VALUES (ST_GeomFromText('MULTIPOLYGON(((2600000 1200000,2600010 1200000,2600010 1200010,2600000 1200000)))', 2056));
