package com.tpv.desktop.api.pos;

import com.tpv.desktop.api.ApiClient;

public final class CatalogApi {
  private CatalogApi(){}

  public static CategoryResponse[] categories() throws Exception {
    return ApiClient.get("/api/v1/pos/categories", CategoryResponse[].class);
  }

  public static ProductResponse[] products(Long categoryId) throws Exception {
    String path = (categoryId == null) ? "/api/v1/pos/products"
        : "/api/v1/pos/products?categoryId=" + categoryId;
    return ApiClient.get(path, ProductResponse[].class);
  }

  public static CategoryResponse createCategory(String name) throws Exception {
    return ApiClient.post("/api/v1/pos/categories", new CreateCategoryRequest(name), CategoryResponse.class);
  }

  public static CategoryResponse updateCategory(long id, String name) throws Exception {
    return ApiClient.put("/api/v1/pos/categories/" + id, new UpdateCategoryRequest(name), CategoryResponse.class);
  }

  public static void deleteCategory(long id) throws Exception {
    ApiClient.delete("/api/v1/pos/categories/" + id, Void.class);
  }

  public static ProductResponse createProduct(String name, int priceCents, long categoryId, int vatRateBps) throws Exception {
    return ApiClient.post(
        "/api/v1/pos/products",
        new CreateProductRequest(name, priceCents, categoryId, vatRateBps),
        ProductResponse.class
    );
  }

  public static ProductResponse updateProduct(long id, String name, int priceCents, long categoryId, int vatRateBps) throws Exception {
    return ApiClient.put(
        "/api/v1/pos/products/" + id,
        new UpdateProductRequest(name, priceCents, categoryId, vatRateBps),
        ProductResponse.class
    );
  }

  public static void deleteProduct(long id) throws Exception {
    ApiClient.delete("/api/v1/pos/products/" + id, Void.class);
  }
}
