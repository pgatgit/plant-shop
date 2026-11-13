package com.plantshop.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** A display category of plants (e.g. Ferns). Read-only: managed by Flyway seed migrations. */
@Entity
@Immutable
@Table(name = "category")
public class Category {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 32)
  private String code;

  @Column(nullable = false, length = 64)
  private String name;

  /** Required by JPA. */
  protected Category() {}

  /**
   * Creates a category (used by tests; the application never writes categories).
   *
   * @param code stable upper-case code, e.g. {@code FERNS}
   * @param name display name
   */
  public Category(String code, String name) {
    this.code = code;
    this.name = name;
  }

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }
}
