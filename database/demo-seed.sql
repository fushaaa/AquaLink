INSERT INTO communities (
  id, public_id, name, country_code, region, center_lat, center_lng,
  pooling_radius_m, timezone, status
) VALUES (
  1, 'a1000000-0000-4000-8000-000000000001', 'AquaLink Demo Community',
  'KE', 'Nairobi', -1.286389, 36.817223, 300, 'Africa/Nairobi', 'ACTIVE'
);

INSERT INTO users (
  id, public_id, phone_e164, password_hash, full_name,
  preferred_language, status
) VALUES (
  1, 'a1000000-0000-4000-8000-000000000002', '+15555550123',
  '$2a$10$/nJ1BT7eZtOkRnmsverFuu3T2ti6uMPDR5ki0fnJq/GF9LaEdvYrK',
  'Demo Resident', 'en', 'ACTIVE'
);

INSERT INTO user_roles (user_id, role_id, community_id)
SELECT 1, id, 1 FROM roles WHERE code = 'RESIDENT';

INSERT INTO service_points (
  id, public_id, community_id, arrangement, label, latitude, longitude
) VALUES (
  1, 'a1000000-0000-4000-8000-000000000003', 1,
  'COMMUNAL_TAP', 'Community water point', -1.286389, 36.817223
);

INSERT INTO households (
  id, public_id, community_id, service_point_id, display_name,
  persons, latitude, longitude, vulnerable, status
) VALUES (
  1, 'a1000000-0000-4000-8000-000000000004', 1, 1,
  'Demo Household', 3, -1.286389, 36.817223, FALSE, 'ACTIVE'
);

INSERT INTO household_users (household_id, user_id, is_primary)
VALUES (1, 1, TRUE);