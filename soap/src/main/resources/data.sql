INSERT INTO pickup_points (name, city, address, capacity) VALUES
    ('Norreport Lockers',  'Copenhagen', 'Norre Voldgade 80', 3),
    ('Amager Kiosk',       'Copenhagen', 'Amagerbrogade 120', 2),
    ('Aarhus H Lockers',   'Aarhus',     'Banegardspladsen 1', 4),
    ('Odense Parcel Shop', 'Odense',     'Vestergade 60',      1);

-- Pickup point 4 has capacity 1 and one active reservation, so it is full.
INSERT INTO reservations (pickup_point_id, tracking_number, status) VALUES
    (1, 'DK-100001', 'ACTIVE'),
    (1, 'DK-100002', 'CANCELLED'),
    (3, 'DK-100003', 'ACTIVE'),
    (4, 'DK-100004', 'ACTIVE');
