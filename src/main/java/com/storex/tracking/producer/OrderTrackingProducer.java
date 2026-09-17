package com.storex.tracking.producer;

import com.storex.tracking.config.KafkaTopicConfig;
import com.storex.tracking.model.OrderStatus;
import com.storex.tracking.model.OrderTrackingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Producer gửi sự kiện cập nhật đơn hàng vào Kafka topic "order-tracking":
 * - BẮT BUỘC sử dụng partitionKey = String.valueOf(orderId).
 * - Thuật toán băm MurmurHash2 của Kafka bảo đảm 100% các sự kiện của cùng 1 orderId
 *   (CREATED -> PAID -> SHIPPED) luôn luôn rơi vào cùng MỘT Partition duy nhất.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderTrackingProducer {

    private final KafkaTemplate<String, OrderTrackingEvent> kafkaTemplate;

    /**
     * Gửi sự kiện với Partition Key = orderId.
     *
     * @param event Sự kiện OrderTrackingEvent
     * @return CompletableFuture chứa kết quả gửi bản tin (kèm Partition và Offset thực tế)
     */
    public CompletableFuture<SendResult<String, OrderTrackingEvent>> sendTrackingEvent(OrderTrackingEvent event) {
        // GIẢI PHÁP THEN CHỐT: Chỉ định rõ ràng partitionKey = String.valueOf(orderId)
        String partitionKey = String.valueOf(event.getOrderId());

        log.info("Producer đang gửi sự kiện [orderId={}, status={}] với Partition Key=[{}] sang topic [{}]",
                event.getOrderId(), event.getStatus(), partitionKey, KafkaTopicConfig.ORDER_TRACKING_TOPIC);

        CompletableFuture<SendResult<String, OrderTrackingEvent>> future =
                kafkaTemplate.send(KafkaTopicConfig.ORDER_TRACKING_TOPIC, partitionKey, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Bản tin [orderId={}, status={}] đã được ghi vào Partition [{}] tại Offset [{}]",
                        event.getOrderId(), event.getStatus(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Gửi bản tin [orderId={}] thất bại: {}", event.getOrderId(), ex.getMessage(), ex);
            }
        });

        return future;
    }

    public CompletableFuture<SendResult<String, OrderTrackingEvent>> sendOrderCreated(Long orderId) {
        return sendTrackingEvent(OrderTrackingEvent.builder()
                .orderId(orderId)
                .status(OrderStatus.CREATED)
                .timestamp(System.currentTimeMillis())
                .description("Đơn hàng được khởi tạo thành công")
                .build());
    }

    public CompletableFuture<SendResult<String, OrderTrackingEvent>> sendOrderPaid(Long orderId) {
        return sendTrackingEvent(OrderTrackingEvent.builder()
                .orderId(orderId)
                .status(OrderStatus.PAID)
                .timestamp(System.currentTimeMillis())
                .description("Đơn hàng đã được thanh toán")
                .build());
    }

    public CompletableFuture<SendResult<String, OrderTrackingEvent>> sendOrderShipped(Long orderId) {
        return sendTrackingEvent(OrderTrackingEvent.builder()
                .orderId(orderId)
                .status(OrderStatus.SHIPPED)
                .timestamp(System.currentTimeMillis())
                .description("Đơn hàng đã xuất kho và đang giao hàng")
                .build());
    }
}
