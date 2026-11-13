package com.plantshop.catalog.mapper;

import com.plantshop.catalog.api.dto.CategoryResponse;
import com.plantshop.catalog.api.dto.ProductResponse;
import com.plantshop.catalog.domain.Category;
import com.plantshop.catalog.domain.Product;
import com.plantshop.common.money.Money;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps catalog entities to API DTOs. */
@Mapper(imports = Money.class)
public interface ProductMapper {

  @Mapping(target = "price", expression = "java(Money.format(product.getPrice()))")
  @Mapping(target = "currency", constant = Money.CURRENCY)
  ProductResponse toResponse(Product product);

  CategoryResponse toResponse(Category category);
}
