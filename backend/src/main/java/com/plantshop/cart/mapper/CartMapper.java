package com.plantshop.cart.mapper;

import com.plantshop.cart.api.dto.CartItemResponse;
import com.plantshop.cart.api.dto.CartResponse;
import com.plantshop.cart.service.CartLineView;
import com.plantshop.cart.service.CartView;
import com.plantshop.common.money.Money;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps cart read models to API DTOs; money is formatted as two-decimal strings. */
@Mapper(imports = Money.class)
public interface CartMapper {

  @Mapping(target = "items", source = "lines")
  @Mapping(target = "subtotal", expression = "java(Money.format(view.subtotal()))")
  @Mapping(target = "currency", constant = Money.CURRENCY)
  CartResponse toResponse(CartView view);

  @Mapping(target = "unitPrice", expression = "java(Money.format(line.unitPrice()))")
  @Mapping(target = "lineTotal", expression = "java(Money.format(line.lineTotal()))")
  CartItemResponse toResponse(CartLineView line);
}
