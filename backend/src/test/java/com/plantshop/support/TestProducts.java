package com.plantshop.support;

import com.plantshop.catalog.domain.LightRequirement;
import com.plantshop.catalog.domain.Product;
import com.plantshop.catalog.domain.WateringFrequency;
import java.math.BigDecimal;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

/** Builds detached {@link Product} instances for unit tests (the entity is read-only in code). */
public final class TestProducts {

  private TestProducts() {}

  /**
   * Creates a product.
   *
   * @param id product id
   * @param name display name
   * @param price unit price, e.g. {@code "26.99"}
   * @param stock units in stock
   * @return the product
   */
  public static Product product(long id, String name, String price, int stock) {
    Product product = BeanUtils.instantiateClass(Product.class);
    ReflectionTestUtils.setField(product, "id", id);
    ReflectionTestUtils.setField(product, "sku", "SKU-" + id);
    ReflectionTestUtils.setField(product, "name", name);
    ReflectionTestUtils.setField(product, "description", name + " description.");
    ReflectionTestUtils.setField(product, "price", new BigDecimal(price));
    ReflectionTestUtils.setField(product, "stock", stock);
    ReflectionTestUtils.setField(product, "light", LightRequirement.MEDIUM);
    ReflectionTestUtils.setField(product, "watering", WateringFrequency.MODERATE);
    ReflectionTestUtils.setField(product, "imagePath", "/images/plants/sku-" + id + ".svg");
    return product;
  }
}
