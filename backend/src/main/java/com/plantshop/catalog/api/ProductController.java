package com.plantshop.catalog.api;

import com.plantshop.catalog.api.dto.ProductResponse;
import com.plantshop.catalog.domain.SearchTerm;
import com.plantshop.catalog.mapper.ProductMapper;
import com.plantshop.catalog.service.ProductQuery;
import com.plantshop.catalog.service.ProductService;
import com.plantshop.catalog.service.ProductSort;
import com.plantshop.common.error.InvalidParameterException;
import com.plantshop.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Catalog", description = "Browse and search the plant catalog")
public class ProductController {

  private static final String PROBLEM_JSON = "application/problem+json";

  private final ProductService productService;
  private final ProductMapper productMapper;

  public ProductController(ProductService productService, ProductMapper productMapper) {
    this.productService = productService;
    this.productMapper = productMapper;
  }

  @GetMapping
  @Operation(
      operationId = "listProducts",
      summary = "List or search products",
      description =
          "Returns one page of products. With `q`, only products whose name **starts with** the"
              + " term are returned, ignoring case and accents. Requires scope `catalog.read`.")
  @ApiResponse(responseCode = "200", description = "A page of products")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid paging, sort or search parameter",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Missing or invalid access token",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Token lacks scope catalog.read",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class)))
  public PageResponse<ProductResponse> listProducts(
      @Parameter(description = "0-based page index") @RequestParam(defaultValue = "0") @Min(0)
          int page,
      @Parameter(description = "Page size (1–48)")
          @RequestParam(defaultValue = "12")
          @Min(1)
          @Max(48)
          int size,
      @Parameter(
              description = "Ordering",
              schema =
                  @Schema(
                      allowableValues = {"name,asc", "name,desc", "price,asc", "price,desc"},
                      defaultValue = "name,asc"))
          @RequestParam(defaultValue = "name,asc")
          String sort,
      @Parameter(
              description =
                  "Name prefix to search for. Trimmed and whitespace-collapsed; 2–50 characters"
                      + " after normalisation. Blank means no search.")
          @RequestParam(required = false)
          String q) {
    ProductSort productSort =
        ProductSort.fromParameter(sort)
            .orElseThrow(
                () ->
                    new InvalidParameterException(
                        "sort", "must be one of name,asc, name,desc, price,asc, price,desc", sort));
    SearchTerm term = SearchTerm.parse(q).orElse(null);
    ProductQuery query = new ProductQuery(page, size, productSort, term);
    return PageResponse.of(
        productService.list(query),
        productMapper::toResponse,
        productSort.parameter(),
        term == null ? null : term.value());
  }
}
