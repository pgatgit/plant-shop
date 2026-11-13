package com.plantshop.catalog.api;

import static com.plantshop.support.TestAuth.shopper;
import static com.plantshop.support.TestAuth.withScopes;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plantshop.catalog.mapper.ProductMapperImpl;
import com.plantshop.catalog.service.ProductQuery;
import com.plantshop.catalog.service.ProductService;
import com.plantshop.catalog.service.ProductSort;
import com.plantshop.support.WebMvcSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@Import({WebMvcSecurityTestConfig.class, ProductMapperImpl.class})
@Tag("US-03")
class ProductControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private ProductService productService;

  private void emptyPage() {
    when(productService.list(any()))
        .thenAnswer(
            inv -> {
              ProductQuery q = inv.getArgument(0);
              return new PageImpl<>(List.of(), PageRequest.of(q.page(), q.size()), 0);
            });
  }

  @Test
  @DisplayName("[BR-02][BR-03] defaults: page 0, size 12, name ascending, no search")
  void list_withoutParameters_usesDefaults() throws Exception {
    emptyPage();

    mvc.perform(get("/api/v1/products").with(shopper()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(12))
        .andExpect(jsonPath("$.sort").value("name,asc"))
        .andExpect(jsonPath("$.query").doesNotExist())
        .andExpect(jsonPath("$.content", hasSize(0)));

    ArgumentCaptor<ProductQuery> query = ArgumentCaptor.forClass(ProductQuery.class);
    verify(productService).list(query.capture());
    org.assertj.core.api.Assertions.assertThat(query.getValue())
        .isEqualTo(new ProductQuery(0, 12, ProductSort.NAME_ASC, null));
  }

  @Test
  @Tag("US-05")
  @DisplayName("[BR-04] the normalised search term is echoed back")
  void list_withSearch_echoesNormalisedQuery() throws Exception {
    emptyPage();

    mvc.perform(get("/api/v1/products").param("q", "  peace   li ").with(shopper()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.query").value("peace li"));
  }

  @Test
  @DisplayName("[BR-02] size above 48 is a validation problem")
  void list_sizeTooLarge_returnsValidationProblem() throws Exception {
    mvc.perform(get("/api/v1/products").param("size", "49").with(shopper()))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.type").value("https://plantshop.example/problems/validation-failed"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.instance").value("/api/v1/products"))
        .andExpect(jsonPath("$.errors[0].field").value("size"))
        .andExpect(jsonPath("$.errors[0].rejectedValue").value(49));
    verifyNoInteractions(productService);
  }

  @Test
  void list_negativePage_returnsValidationProblem() throws Exception {
    mvc.perform(get("/api/v1/products").param("page", "-1").with(shopper()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.errors[0].field").value("page"));
  }

  @Test
  void list_nonNumericPage_returnsMalformedRequestProblem() throws Exception {
    mvc.perform(get("/api/v1/products").param("page", "abc").with(shopper()))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("malformed-request"));
  }

  @Test
  @Tag("US-04")
  @DisplayName("[BR-03] unknown sort values are rejected with the accepted values")
  void list_unknownSort_returnsValidationProblem() throws Exception {
    mvc.perform(get("/api/v1/products").param("sort", "stock,asc").with(shopper()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.errors[0].field").value("sort"))
        .andExpect(jsonPath("$.errors[0].rejectedValue").value("stock,asc"));
  }

  @Test
  @Tag("US-05")
  @DisplayName("[BR-04] a one-character search is an invalid-search-term problem")
  void list_searchTooShort_returnsInvalidSearchTermProblem() throws Exception {
    mvc.perform(get("/api/v1/products").param("q", " m ").with(shopper()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-search-term"))
        .andExpect(jsonPath("$.minLength").value(2))
        .andExpect(jsonPath("$.maxLength").value(50));
  }

  @Test
  @Tag("BR-01")
  @DisplayName("[BR-01] unauthenticated requests get a 401 problem and a Bearer challenge")
  void list_withoutAuthentication_returns401Problem() throws Exception {
    mvc.perform(get("/api/v1/products"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("unauthenticated"))
        .andExpect(jsonPath("$.instance").value("/api/v1/products"));
    verifyNoInteractions(productService);
  }

  @Test
  @DisplayName("[NFR-SEC-02] a token without catalog.read gets a 403 problem")
  void list_withoutCatalogScope_returns403Problem() throws Exception {
    mvc.perform(get("/api/v1/products").with(withScopes("someone", "cart.read")))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("forbidden"));
    verifyNoInteractions(productService);
  }

  @Test
  void unexpectedFailure_returnsGenericInternalErrorProblem() throws Exception {
    when(productService.list(any())).thenThrow(new IllegalStateException("db password is x"));

    mvc.perform(get("/api/v1/products").with(shopper()))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("internal-error"))
        .andExpect(
            jsonPath("$.detail").value("An unexpected error occurred. Please try again later."));
  }

  @Test
  void unsupportedMethod_returnsMethodNotAllowedProblem() throws Exception {
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/v1/products")
                .with(shopper()))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("method-not-allowed"));
  }
}
