package com.storex.tracking.model;

/**
 * Các trạng thái theo vòng đời của đơn hàng tại StoreX.
 * Ràng buộc thứ tự tuần tự bắt buộc: CREATED -> PAID -> SHIPPED.
 */
public enum OrderStatus {
    CREATED,
    PAID,
    SHIPPED
}
