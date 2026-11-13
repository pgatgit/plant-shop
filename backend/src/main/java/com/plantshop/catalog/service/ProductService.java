package com.plantshop.catalog.service;

import com.plantshop.catalog.domain.Product;
import com.plantshop.catalog.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalog listing and name-prefix search (US-03, US-04, US-05). */
@Service
@Transactional(readOnly = true)
public class ProductService {

  private final ProductRepository products;

  /**
   * Creates the service.
   *
   * @param products the product repository
   */
  public ProductService(ProductRepository products) {
    this.products = products;
  }

  /**
   * Lists one page of products, optionally restricted to names starting with the search term.
   *
   * @param query the validated listing request
   * @return the requested page (empty content when the page is beyond the last one)
   */
  public Page<Product> list(ProductQuery query) {
    Pageable pageable = PageRequest.of(query.page(), query.size(), query.sort().toSort());
    return query
        .search()
        .map(term -> products.findByNameStartingWith(term.value(), pageable))
        .orElseGet(() -> products.findAll(pageable));
  }
}
