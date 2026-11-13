package com.plantshop.cart.service;

import com.plantshop.cart.domain.StockStatus;
import java.math.BigDecimal;

/**
 * One cart line with live price and stock information (BR-09, BR-12).
 *
 * @param productId product id
 * @param name product name
 * @param imagePath product illustration path
 * @param unitPrice current unit price
 * @param quantity line quantity
 * @param lineTotal {@code unitPrice × quantity}, scale 2
 * @param stock units currently in stock
 * @param maxQuantity {@code min(stock, 10)}
 * @param stockStatus line quantity compared with stock
 */
public record CartLineView(
    long productId,
    String name,
    String imagePath,
    BigDecimal unitPrice,
    int quantity,
    BigDecimal lineTotal,
    int stock,
    int maxQuantity,
    StockStatus stockStatus) {}
