-- ===== Identity =====
CREATE TABLE roles (
  id   TINYINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  UNIQUE KEY uq_roles_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO roles (code) VALUES ('RESIDENT'),('WATER_PROVIDER'),('COMMUNITY_MANAGER'),('ADMIN');

CREATE TABLE users (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  public_id          CHAR(36)     NOT NULL,
  phone_e164         VARCHAR(16)  NOT NULL,
  email              VARCHAR(255) NULL,
  password_hash      VARCHAR(100) NULL,               -- NULL for USSD-only residents
  full_name          VARCHAR(120) NOT NULL,
  preferred_language CHAR(2)      NOT NULL DEFAULT 'en',
  status             ENUM('ACTIVE','SUSPENDED','DELETED') NOT NULL DEFAULT 'ACTIVE',
  created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_users_public_id (public_id),
  UNIQUE KEY uq_users_phone (phone_e164),
  UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE communities (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  public_id        CHAR(36)      NOT NULL,
  name             VARCHAR(150)  NOT NULL,
  country_code     CHAR(2)       NOT NULL,
  region           VARCHAR(100)  NULL,
  center_lat       DECIMAL(9,6)  NOT NULL,
  center_lng       DECIMAL(9,6)  NOT NULL,
  pooling_radius_m INT UNSIGNED  NOT NULL DEFAULT 300,
  timezone         VARCHAR(40)   NOT NULL DEFAULT 'Africa/Nairobi',
  status           ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_communities_public_id (public_id),
  KEY idx_communities_country_region (country_code, region)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Role grants, optionally scoped to a community (NULL = global).
-- scope_key makes the unique key NULL-safe. Base-column FK must be RESTRICT (generated-column limitation).
CREATE TABLE user_roles (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id      BIGINT UNSIGNED  NOT NULL,
  role_id      TINYINT UNSIGNED NOT NULL,
  community_id BIGINT UNSIGNED  NULL,
  scope_key    BIGINT UNSIGNED AS (COALESCE(community_id, 0)) STORED,
  UNIQUE KEY uq_user_roles (user_id, role_id, scope_key),
  KEY idx_user_roles_community_role (community_id, role_id),
  CONSTRAINT fk_ur_user      FOREIGN KEY (user_id)      REFERENCES users(id)       ON DELETE CASCADE,
  CONSTRAINT fk_ur_role      FOREIGN KEY (role_id)      REFERENCES roles(id)       ON DELETE RESTRICT,
  CONSTRAINT fk_ur_community FOREIGN KEY (community_id) REFERENCES communities(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ===== Supply side =====
CREATE TABLE water_sources (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  public_id           CHAR(36)     NOT NULL,
  community_id        BIGINT UNSIGNED NOT NULL,
  name                VARCHAR(150) NOT NULL,
  source_type         ENUM('PIPED_UTILITY','BOREHOLE','KIOSK','TANKER_DEPOT','RAINWATER','SPRING','OTHER') NOT NULL,
  operational_status  ENUM('OPERATIONAL','DEGRADED','DOWN','UNKNOWN') NOT NULL DEFAULT 'UNKNOWN',
  latitude            DECIMAL(9,6) NOT NULL,
  longitude           DECIMAL(9,6) NOT NULL,
  capacity_l_per_day  INT UNSIGNED NULL,
  reliability_score   DECIMAL(5,2) NULL,                  -- derived by analytics, 0-100
  created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_sources_public_id (public_id),
  KEY idx_sources_community_status (community_id, operational_status),
  CONSTRAINT fk_sources_community FOREIGN KEY (community_id) REFERENCES communities(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Delivery/metering point. INDIVIDUAL_METER => 1 household; shared => N households.
CREATE TABLE service_points (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  public_id       CHAR(36)     NOT NULL,
  community_id    BIGINT UNSIGNED NOT NULL,
  source_id       BIGINT UNSIGNED NULL,
  arrangement     ENUM('INDIVIDUAL_METER','SHARED_COMMUNITY_METER','COMMUNAL_TAP') NOT NULL,
  meter_number    VARCHAR(40)  NULL,
  label           VARCHAR(120) NOT NULL,
  latitude        DECIMAL(9,6) NOT NULL,
  longitude       DECIMAL(9,6) NOT NULL,
  manager_user_id BIGINT UNSIGNED NULL,                   -- community/institution manager
  status          ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_sp_public_id (public_id),
  UNIQUE KEY uq_sp_meter (community_id, meter_number),    -- NULLs are not equal: unmetered points allowed
  KEY idx_sp_community_arr (community_id, arrangement, status),
  KEY idx_sp_source (source_id),
  KEY idx_sp_manager (manager_user_id),
  CONSTRAINT fk_sp_community FOREIGN KEY (community_id)    REFERENCES communities(id)   ON DELETE RESTRICT,
  CONSTRAINT fk_sp_source    FOREIGN KEY (source_id)       REFERENCES water_sources(id) ON DELETE SET NULL,
  CONSTRAINT fk_sp_manager   FOREIGN KEY (manager_user_id) REFERENCES users(id)         ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE storage_tanks (
  id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  public_id               CHAR(36)     NOT NULL,
  community_id            BIGINT UNSIGNED NOT NULL,
  service_point_id        BIGINT UNSIGNED NULL,
  source_id               BIGINT UNSIGNED NULL,
  name                    VARCHAR(120) NOT NULL,
  capacity_l              INT UNSIGNED NOT NULL,
  current_level_l         INT UNSIGNED NULL,
  level_source            ENUM('SENSOR','MANUAL','UNKNOWN') NOT NULL DEFAULT 'UNKNOWN',
  level_updated_at        DATETIME(3)  NULL,
  sensor_device_id        VARCHAR(64)  NULL,
  low_level_threshold_pct TINYINT UNSIGNED NOT NULL DEFAULT 20,
  created_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_tank_public_id (public_id),
  UNIQUE KEY uq_tank_sensor (sensor_device_id),
  KEY idx_tank_community (community_id),
  KEY idx_tank_sp (service_point_id),
  CONSTRAINT chk_tank_level CHECK (current_level_l IS NULL OR current_level_l <= capacity_l),
  CONSTRAINT fk_tank_community FOREIGN KEY (community_id)     REFERENCES communities(id)    ON DELETE RESTRICT,
  CONSTRAINT fk_tank_sp        FOREIGN KEY (service_point_id) REFERENCES service_points(id) ON DELETE SET NULL,
  CONSTRAINT fk_tank_source    FOREIGN KEY (source_id)        REFERENCES water_sources(id)  ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ===== Demand side =====
CREATE TABLE households (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  public_id        CHAR(36)     NOT NULL,
  community_id     BIGINT UNSIGNED NOT NULL,
  service_point_id BIGINT UNSIGNED NOT NULL,              -- own meter or the shared meter/tap
  display_name     VARCHAR(120) NOT NULL,
  persons          TINYINT UNSIGNED NOT NULL DEFAULT 1,
  latitude         DECIMAL(9,6) NOT NULL,
  longitude        DECIMAL(9,6) NOT NULL,
  vulnerable       BOOLEAN      NOT NULL DEFAULT FALSE,   -- drives order priority / voucher eligibility
  status           ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_hh_public_id (public_id),
  KEY idx_hh_sp (service_point_id, status),
  KEY idx_hh_community (community_id, status),
  CONSTRAINT fk_hh_community FOREIGN KEY (community_id)     REFERENCES communities(id)    ON DELETE RESTRICT,
  CONSTRAINT fk_hh_sp        FOREIGN KEY (service_point_id) REFERENCES service_points(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE household_users (
  household_id BIGINT UNSIGNED NOT NULL,
  user_id      BIGINT UNSIGNED NOT NULL,
  is_primary   BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (household_id, user_id),
  KEY idx_hu_user (user_id),
  CONSTRAINT fk_hu_household FOREIGN KEY (household_id) REFERENCES households(id) ON DELETE CASCADE,
  CONSTRAINT fk_hu_user      FOREIGN KEY (user_id)      REFERENCES users(id)      ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE water_providers (
  id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  public_id            CHAR(36)     NOT NULL,
  user_id              BIGINT UNSIGNED NOT NULL,
  business_name        VARCHAR(150) NOT NULL,
  vehicle_type         ENUM('HANDCART','TUKTUK','TRUCK','TANKER','KIOSK') NOT NULL,
  trip_capacity_l      INT UNSIGNED NOT NULL,
  price_per_litre      DECIMAL(10,4) NOT NULL,
  currency             CHAR(3)      NOT NULL DEFAULT 'KES',
  verification_status  ENUM('PENDING','VERIFIED','REJECTED','SUSPENDED') NOT NULL DEFAULT 'PENDING',
  availability         ENUM('AVAILABLE','BUSY','OFFLINE') NOT NULL DEFAULT 'OFFLINE',
  last_latitude        DECIMAL(9,6) NULL,
  last_longitude       DECIMAL(9,6) NULL,
  last_seen_at         DATETIME(3)  NULL,
  rating_avg           DECIMAL(3,2) NOT NULL DEFAULT 0.00,
  completed_deliveries INT UNSIGNED NOT NULL DEFAULT 0,
  active               BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_prov_public_id (public_id),
  UNIQUE KEY uq_prov_user (user_id),
  KEY idx_prov_match (availability, verification_status, active, trip_capacity_l),
  CONSTRAINT chk_prov_price CHECK (price_per_litre > 0 AND trip_capacity_l > 0),
  CONSTRAINT fk_prov_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE provider_service_areas (
  provider_id  BIGINT UNSIGNED NOT NULL,
  community_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (provider_id, community_id),
  KEY idx_psa_community (community_id, provider_id),      -- "who serves community X"
  CONSTRAINT fk_psa_provider  FOREIGN KEY (provider_id)  REFERENCES water_providers(id) ON DELETE CASCADE,
  CONSTRAINT fk_psa_community FOREIGN KEY (community_id) REFERENCES communities(id)     ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ===== Orders =====
-- INDIVIDUAL: one household request. POOLED: fulfillable unit; quantity_l == SUM(pooled_order_members.quantity_l)
-- (invariant maintained in WaterOrder.addMember inside the pooling transaction).
CREATE TABLE water_orders (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  public_id           CHAR(36)     NOT NULL,
  order_type          ENUM('INDIVIDUAL','POOLED') NOT NULL,
  community_id        BIGINT UNSIGNED NOT NULL,
  service_point_id    BIGINT UNSIGNED NOT NULL,           -- delivery / rendezvous point
  household_id        BIGINT UNSIGNED NULL,               -- NULL for POOLED
  requested_by_user_id BIGINT UNSIGNED NULL,              -- NULL for POOLED
  arrangement         ENUM('INDIVIDUAL_METER','SHARED_COMMUNITY_METER','COMMUNAL_TAP') NOT NULL, -- snapshot of service_points.arrangement
  status              ENUM('PENDING','POOLED','AWAITING_PROVIDER','PROVIDER_OFFERED','ASSIGNED',
                           'IN_TRANSIT','DELIVERED','CANCELLED','EXPIRED','FAILED') NOT NULL,
  quantity_l          INT UNSIGNED NOT NULL,
  latitude            DECIMAL(9,6) NOT NULL,
  longitude           DECIMAL(9,6) NOT NULL,
  window_start        DATETIME(3)  NOT NULL,
  window_end          DATETIME(3)  NOT NULL,
  pool_by             DATETIME(3)  NOT NULL,              -- latest time pooling may wait for partners
  priority            TINYINT UNSIGNED NOT NULL DEFAULT 0,
  provider_id         BIGINT UNSIGNED NULL,
  offer_expires_at    DATETIME(3)  NULL,
  unit_price          DECIMAL(10,4) NULL,                 -- snapshot at match; no savings claimed
  currency            CHAR(3)      NULL,
  idempotency_key     VARCHAR(64)  NULL,
  notes               VARCHAR(255) NULL,
  cancelled_reason    VARCHAR(255) NULL,
  version             INT UNSIGNED NOT NULL DEFAULT 0,
  created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_orders_public_id (public_id),
  UNIQUE KEY uq_orders_idem (household_id, idempotency_key),  -- USSD/SMS retry safety
  KEY idx_orders_anchor_scan (community_id, status, order_type, priority, pool_by),
  KEY idx_orders_pool_geo    (community_id, status, order_type, latitude),
  KEY idx_orders_sp_status   (service_point_id, status, order_type),
  KEY idx_orders_provider    (provider_id, status, window_start),
  KEY idx_orders_household   (household_id, created_at),
  CONSTRAINT chk_orders_qty    CHECK (quantity_l >= 0),
  CONSTRAINT chk_orders_window CHECK (window_end > window_start),
  CONSTRAINT chk_orders_shape  CHECK (
       (order_type = 'INDIVIDUAL' AND household_id IS NOT NULL AND quantity_l > 0)
    OR (order_type = 'POOLED'     AND household_id IS NULL)),
  CONSTRAINT fk_orders_community FOREIGN KEY (community_id)         REFERENCES communities(id)     ON DELETE RESTRICT,
  CONSTRAINT fk_orders_sp        FOREIGN KEY (service_point_id)     REFERENCES service_points(id)  ON DELETE RESTRICT,
  CONSTRAINT fk_orders_household FOREIGN KEY (household_id)         REFERENCES households(id)      ON DELETE RESTRICT,
  CONSTRAINT fk_orders_user      FOREIGN KEY (requested_by_user_id) REFERENCES users(id)           ON DELETE RESTRICT,
  CONSTRAINT fk_orders_provider  FOREIGN KEY (provider_id)          REFERENCES water_providers(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pooled_order_members (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  pooled_order_id       BIGINT UNSIGNED NOT NULL,
  source_request_id     BIGINT UNSIGNED NOT NULL,         -- the absorbed INDIVIDUAL order
  household_id          BIGINT UNSIGNED NOT NULL,
  quantity_l            INT UNSIGNED NOT NULL,            -- household's contribution
  unit_price            DECIMAL(10,4) NULL,
  estimated_cost        DECIMAL(12,2) NULL,
  delivered_quantity_l  INT UNSIGNED NULL,
  status                ENUM('COMMITTED','DELIVERED','SHORT_DELIVERED','NO_SHOW') NOT NULL DEFAULT 'COMMITTED',
  joined_at             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_pom_source (source_request_id),           -- a request joins at most one pool; withdrawal deletes the row
  KEY idx_pom_pool (pooled_order_id),
  KEY idx_pom_household (household_id, status),
  CONSTRAINT chk_pom_qty CHECK (quantity_l > 0),
  CONSTRAINT fk_pom_pool      FOREIGN KEY (pooled_order_id)   REFERENCES water_orders(id) ON DELETE CASCADE,
  CONSTRAINT fk_pom_source    FOREIGN KEY (source_request_id) REFERENCES water_orders(id) ON DELETE RESTRICT,
  CONSTRAINT fk_pom_household FOREIGN KEY (household_id)      REFERENCES households(id)   ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE deliveries (
  id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  public_id               CHAR(36)     NOT NULL,
  order_id                BIGINT UNSIGNED NOT NULL,       -- the POOLED (or directly fulfilled INDIVIDUAL) order
  provider_id             BIGINT UNSIGNED NOT NULL,
  source_id               BIGINT UNSIGNED NULL,
  destination_service_point_id BIGINT UNSIGNED NOT NULL,
  destination_tank_id     BIGINT UNSIGNED NULL,
  status                  ENUM('SCHEDULED','DISPATCHED','ARRIVED','COMPLETED','FAILED','CANCELLED') NOT NULL DEFAULT 'SCHEDULED',
  scheduled_at            DATETIME(3)  NOT NULL,
  dispatched_at           DATETIME(3)  NULL,
  completed_at            DATETIME(3)  NULL,
  planned_quantity_l      INT UNSIGNED NOT NULL,
  delivered_quantity_l    INT UNSIGNED NULL,
  total_price             DECIMAL(12,2) NULL,
  currency                CHAR(3)      NULL,
  failure_reason          VARCHAR(255) NULL,
  version                 INT UNSIGNED NOT NULL DEFAULT 0,
  created_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_del_public_id (public_id),
  KEY idx_del_order (order_id),
  KEY idx_del_provider_status (provider_id, status, scheduled_at),
  KEY idx_del_status_sched (status, scheduled_at),
  KEY idx_del_dest (destination_service_point_id, completed_at),
  CONSTRAINT fk_del_order    FOREIGN KEY (order_id)    REFERENCES water_orders(id)    ON DELETE RESTRICT,
  CONSTRAINT fk_del_provider FOREIGN KEY (provider_id) REFERENCES water_providers(id) ON DELETE RESTRICT,
  CONSTRAINT fk_del_source   FOREIGN KEY (source_id)   REFERENCES water_sources(id)   ON DELETE SET NULL,
  CONSTRAINT fk_del_dest_sp  FOREIGN KEY (destination_service_point_id) REFERENCES service_points(id) ON DELETE RESTRICT,
  CONSTRAINT fk_del_tank     FOREIGN KEY (destination_tank_id) REFERENCES storage_tanks(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;