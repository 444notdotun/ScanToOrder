export interface ItemResponse {
  // If the backend doesn't provide id or isAvailable natively in ItemResponse, 
  // we add optional fallbacks depending on what gets serialized.
  itemId?: string;
  ItemName: string;
  ItemDescription?: string;
  ItemPrice: string | number;
  isAvailable?: boolean;
}

export interface CategoryAndItemResponse {
  CategoryName: string;
  itemResponse: ItemResponse[];
}

export interface MenuCatalogApiResponse {
  status: string;
  message?: string;
  data: {
    categoryAndItemResponse: CategoryAndItemResponse[];
  };
}
