package com.storex.tracking.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Event đại diện cho sự kiện thay đổi trạng thái của đơn hàng được gửi vào topic "order-tracking".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderTrackingEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long orderId;
    private OrderStatus status;
    private Long timestamp;
    private String description;
}
