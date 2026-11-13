-- One cart per owner (github:{id} | local:{name}); lines are unique per product (BR-06, BR-07).
CREATE TABLE cart (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  owner_id   VARCHAR(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  version    BIGINT       NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT pk_cart PRIMARY KEY (id),
  CONSTRAINT uk_cart_owner UNIQUE (owner_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE cart_item (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  cart_id    BIGINT       NOT NULL,
  product_id BIGINT       NOT NULL,
  quantity   SMALLINT     NOT NULL,
  added_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT pk_cart_item PRIMARY KEY (id),
  CONSTRAINT uk_cart_item_cart_product UNIQUE (cart_id, product_id),
  CONSTRAINT fk_cart_item_cart FOREIGN KEY (cart_id) REFERENCES cart (id) ON DELETE CASCADE,
  CONSTRAINT fk_cart_item_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE RESTRICT,
  CONSTRAINT ck_cart_item_quantity CHECK (quantity BETWEEN 1 AND 10),
  INDEX ix_cart_item_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
