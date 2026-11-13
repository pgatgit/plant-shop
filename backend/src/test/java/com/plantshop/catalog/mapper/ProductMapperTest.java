package com.plantshop.catalog.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantshop.catalog.api.dto.ProductResponse;
import com.plantshop.catalog.domain.Category;
import com.plantshop.catalog.domain.LightRequirement;
import com.plantshop.catalog.domain.Product;
import com.plantshop.catalog.domain.WateringFrequency;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class ProductMapperTest {

  private final ProductMapper mapper = Mappers.getMapper(ProductMapper.class);

  private static Product product(BigDecimal price) {
    Product product = BeanUtils.instantiateClass(Product.class);
    ReflectionTestUtils.setField(product, "id", 11L);
    ReflectionTestUtils.setField(product, "sku", "FRN-BIRDNEST");
    ReflectionTestUtils.setField(product, "name", "Bird's Nest Fern");
    ReflectionTestUtils.setField(product, "description", "Glossy fronds.");
    ReflectionTestUtils.setField(product, "price", price);
    ReflectionTestUtils.setField(product, "stock", 3);
    ReflectionTestUtils.setField(product, "light", LightRequirement.MEDIUM);
    ReflectionTestUtils.setField(product, "watering", WateringFrequency.HIGH);
    ReflectionTestUtils.setField(product, "petSafe", true);
    ReflectionTestUtils.setField(product, "imagePath", "/images/plants/frn-birdnest.svg");
    ReflectionTestUtils.setField(product, "category", new Category("FERNS", "Ferns"));
    return product;
  }

  @Test
  void toResponse_mapsEveryField_andFormatsMoneyAsTwoDecimalString() {
    ProductResponse response = mapper.toResponse(product(new BigDecimal("26.9")));

    assertThat(response.id()).isEqualTo(11L);
    assertThat(response.sku()).isEqualTo("FRN-BIRDNEST");
    assertThat(response.name()).isEqualTo("Bird's Nest Fern");
    assertThat(response.description()).isEqualTo("Glossy fronds.");
    assertThat(response.price()).isEqualTo("26.90");
    assertThat(response.currency()).isEqualTo("USD");
    assertThat(response.stock()).isEqualTo(3);
    assertThat(response.category().code()).isEqualTo("FERNS");
    assertThat(response.category().name()).isEqualTo("Ferns");
    assertThat(response.light()).isEqualTo(LightRequirement.MEDIUM);
    assertThat(response.watering()).isEqualTo(WateringFrequency.HIGH);
    assertThat(response.petSafe()).isTrue();
    assertThat(response.imagePath()).isEqualTo("/images/plants/frn-birdnest.svg");
  }

  @Test
  void nullInputs_mapToNull() {
    assertThat(mapper.toResponse((Product) null)).isNull();
    assertThat(mapper.toResponse((Category) null)).isNull();
  }
}
