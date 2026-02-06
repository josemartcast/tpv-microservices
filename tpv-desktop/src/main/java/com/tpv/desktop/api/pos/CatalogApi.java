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
}
