package com.plantshop.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;

/**
 * A plant in the catalog. Read-only for the application: there is no admin or checkout, so the
 * catalog (including stock) is only changed by Flyway migrations.
 */
@Entity
@Immutable
@Table(name = "product")
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 32)
  private String sku;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal price;

  @Column(nullable = false)
  private int stock;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private LightRequirement light;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private WateringFrequency watering;

  @Column(name = "pet_safe", nullable = false)
  private boolean petSafe;

  @Column(name = "image_path", nullable = false)
  private String imagePath;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "category_id", nullable = false)
  private Category category;

  @Version
  @Column(nullable = false)
  private long version;

  /** Required by JPA. */
  protected Product() {}

  public Long getId() {
    return id;
  }

  public String getSku() {
    return sku;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public int getStock() {
    return stock;
  }

  public LightRequirement getLight() {
    return light;
  }

  public WateringFrequency getWatering() {
    return watering;
  }

  public boolean isPetSafe() {
    return petSafe;
  }

  public String getImagePath() {
    return imagePath;
  }

  public Category getCategory() {
    return category;
  }

  public long getVersion() {
    return version;
  }
}
