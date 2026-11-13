package com.plantshop.cart.api;

import com.plantshop.auth.security.CurrentUserResolver;
import com.plantshop.cart.api.dto.AddCartItemRequest;
import com.plantshop.cart.api.dto.CartResponse;
import com.plantshop.cart.api.dto.UpdateCartItemRequest;
import com.plantshop.cart.mapper.CartMapper;
import com.plantshop.cart.service.CartService;
import com.plantshop.cart.service.CartView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in shopper's cart (US-06…US-09). The owner always comes from the access token, never
 * from the URL or body (BR-13). Every mutation returns the whole cart.
 */
@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart", description = "The signed-in shopper's cart")
@ApiResponse(
    responseCode = "401",
    description = "Missing or invalid access token",
    content =
        @Content(
            mediaType = CartController.PROBLEM_JSON,
            schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(
    responseCode = "503",
    description = "The identity provider could not validate the token",
    content =
        @Content(
            mediaType = CartController.PROBLEM_JSON,
            schema = @Schema(implementation = ProblemDetail.class)))
public class CartController {

  static final String PROBLEM_JSON = "application/problem+json";

  private final CartService cartService;
  private final CartMapper cartMapper;
  private final CurrentUserResolver currentUsers;

  public CartController(
      CartService cartService, CartMapper cartMapper, CurrentUserResolver currentUsers) {
    this.cartService = cartService;
    this.cartMapper = cartMapper;
    this.currentUsers = currentUsers;
  }

  @GetMapping
  @Operation(
      operationId = "getCart",
      summary = "Get the cart",
      description =
          "Returns the cart with **current** prices and stock. An empty cart is returned if the"
              + " shopper has never added anything. Requires scope `cart.read`.")
  @ApiResponse(responseCode = "200", description = "The cart")
  @ApiResponse(
      responseCode = "403",
      description = "Token lacks scope cart.read",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class)))
  public CartResponse getCart(Authentication authentication) {
    return respond(cartService.get(owner(authentication)));
  }

  @PostMapping("/items")
  @Operation(
      operationId = "addCartItem",
      summary = "Add a product to the cart",
      description =
          "Adds `quantity` (default 1) of a product. If the product is already in the cart its"
              + " line is increased. A line may hold at most min(stock, 10). Requires scope"
              + " `cart.write`.")
  @ApiResponse(responseCode = "200", description = "The updated cart")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid body (`validation-failed`, `malformed-request`)",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Token lacks scope cart.write",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "`product-not-found`",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "`out-of-stock`, `quantity-limit-exceeded` (with `limit`, `requested`, `productId`) or"
              + " `concurrent-modification`; the cart is unchanged",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class)))
  public CartResponse addCartItem(
      Authentication authentication, @Valid @RequestBody AddCartItemRequest request) {
    return respond(
        cartService.addItem(
            owner(authentication), request.productId(), request.quantityOrDefault()));
  }

  @PatchMapping("/items/{productId}")
  @Operation(
      operationId = "updateCartItem",
      summary = "Change a line's quantity",
      description =
          "Sets the quantity of the product's line (1–10). Decreasing is always allowed;"
              + " increasing is limited to min(stock, 10). Requires scope `cart.write`.")
  @ApiResponse(responseCode = "200", description = "The updated cart")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid body or product id",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Token lacks scope cart.write",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "`cart-item-not-found`",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "`out-of-stock`, `quantity-limit-exceeded` or `concurrent-modification`; the cart is"
              + " unchanged",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class)))
  public CartResponse updateCartItem(
      Authentication authentication,
      @Parameter(description = "Product id of the line") @PathVariable @Positive long productId,
      @Valid @RequestBody UpdateCartItemRequest request) {
    return respond(
        cartService.updateQuantity(owner(authentication), productId, request.quantity()));
  }

  @DeleteMapping("/items/{productId}")
  @Operation(
      operationId = "removeCartItem",
      summary = "Remove a line",
      description = "Removes the product's line from the cart. Requires scope `cart.write`.")
  @ApiResponse(responseCode = "200", description = "The updated cart")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid product id",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Token lacks scope cart.write",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "`cart-item-not-found`",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "`concurrent-modification`",
      content =
          @Content(
              mediaType = PROBLEM_JSON,
              schema = @Schema(implementation = ProblemDetail.class)))
  public CartResponse removeCartItem(
      Authentication authentication,
      @Parameter(description = "Product id of the line") @PathVariable @Positive long productId) {
    return respond(cartService.removeItem(owner(authentication), productId));
  }

  private String owner(Authentication authentication) {
    return currentUsers.resolve(authentication).ownerId();
  }

  private CartResponse respond(CartView view) {
    return cartMapper.toResponse(view);
  }
}
