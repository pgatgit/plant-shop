-- Catalog products. `name` uses utf8mb4_0900_ai_ci so `LIKE 'term%'` is case- and
-- accent-insensitive and can use ix_product_name (ADR-005).
CREATE TABLE product (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  sku         VARCHAR(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  name        VARCHAR(100)  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  description TEXT          NOT NULL,
  price       DECIMAL(10,2) NOT NULL,
  stock       INT           NOT NULL,
  light       VARCHAR(20)   NOT NULL,
  watering    VARCHAR(20)   NOT NULL,
  pet_safe    BOOLEAN       NOT NULL,
  image_path  VARCHAR(255)  NOT NULL,
  category_id BIGINT        NOT NULL,
  version     BIGINT        NOT NULL DEFAULT 0,
  created_at  TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT pk_product PRIMARY KEY (id),
  CONSTRAINT uk_product_sku UNIQUE (sku),
  CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id),
  CONSTRAINT ck_product_price CHECK (price > 0),
  CONSTRAINT ck_product_stock CHECK (stock >= 0),
  CONSTRAINT ck_product_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
  CONSTRAINT ck_product_light CHECK (light IN ('LOW', 'MEDIUM', 'BRIGHT_INDIRECT', 'DIRECT')),
  CONSTRAINT ck_product_watering CHECK (watering IN ('LOW', 'MODERATE', 'HIGH')),
  INDEX ix_product_name (name, id),
  INDEX ix_product_price (price, name, id),
  INDEX ix_product_category (category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
