-- Product categories (display only; not filterable per D8).
CREATE TABLE category (
  id   BIGINT       NOT NULL AUTO_INCREMENT,
  code VARCHAR(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  name VARCHAR(64)  NOT NULL,
  CONSTRAINT pk_category PRIMARY KEY (id),
  CONSTRAINT uk_category_code UNIQUE (code),
  CONSTRAINT ck_category_code CHECK (code REGEXP '^[A-Z][A-Z_]*$')
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
