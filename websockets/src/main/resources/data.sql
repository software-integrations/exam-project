-- 1: on its way, 2: not started yet, 3: already delivered (courier is at the destination)
INSERT INTO deliveries (recipient, address, dest_lat, dest_lng, status, last_lat, last_lng) VALUES
    ('Anna Jensen',  'Norrebrogade 10, Copenhagen', 55.6900, 12.5500, 'IN_TRANSIT', 55.6761, 12.5683),
    ('Bo Nielsen',   'Park Alle 5, Aarhus',         56.1520, 10.2030, 'PENDING',    NULL,    NULL),
    ('Clara Hansen', 'Vestergade 22, Odense',       55.3959, 10.3883, 'DELIVERED',  55.3959, 10.3883);
