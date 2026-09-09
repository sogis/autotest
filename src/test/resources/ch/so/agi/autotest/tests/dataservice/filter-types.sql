DROP TABLE IF EXISTS dataservice.filter_types;

CREATE TABLE dataservice.filter_types (
    id integer PRIMARY KEY,
    category text NOT NULL,
    geometry geometry(POINT, 2056) NOT NULL
);

INSERT INTO dataservice.filter_types (id, category, geometry) VALUES
    (1, 'selected', ST_SetSRID(ST_Point(2600000, 1200000), 2056)),
    (2, 'other', ST_SetSRID(ST_Point(2600500, 1200500), 2056)),
    (3, 'selected', ST_SetSRID(ST_Point(2610000, 1210000), 2056)),
    (4, 'other', ST_SetSRID(ST_Point(2610500, 1210500), 2056));
