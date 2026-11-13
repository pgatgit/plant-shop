package com.plantshop.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantshop.catalog.domain.Product;
import com.plantshop.catalog.domain.SearchTerm;
import com.plantshop.catalog.repository.ProductRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  @Mock private ProductRepository repository;

  private ProductService service() {
    return new ProductService(repository);
  }

  @Test
  void list_withoutSearch_pagesAllProductsWithRequestedSort() {
    Page<Product> page = new PageImpl<>(List.of());
    when(repository.findAll(any(Pageable.class))).thenReturn(page);

    Page<Product> result = service().list(new ProductQuery(2, 12, ProductSort.PRICE_DESC, null));

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).findAll(pageable.capture());
    verify(repository, never()).findByNameStartingWith(any(), any());
    assertThat(result).isSameAs(page);
    assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
    assertThat(pageable.getValue().getPageSize()).isEqualTo(12);
    assertThat(pageable.getValue().getSort()).isEqualTo(ProductSort.PRICE_DESC.toSort());
  }

  @Test
  void list_withSearch_usesPrefixQueryWithNormalisedTerm() {
    Page<Product> page = new PageImpl<>(List.of());
    when(repository.findByNameStartingWith(any(), any())).thenReturn(page);

    service().list(new ProductQuery(0, 12, ProductSort.NAME_ASC, new SearchTerm("peace li")));

    verify(repository).findByNameStartingWith(org.mockito.ArgumentMatchers.eq("peace li"), any());
    verify(repository, never()).findAll(any(Pageable.class));
  }
}
