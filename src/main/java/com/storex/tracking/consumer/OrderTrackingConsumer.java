package com.storex.tracking.consumer;

import com.storex.tracking.model.OrderStatus;
import com.storex.tracking.model.OrderTrackingEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * OrderTrackingConsumer - Lắng nghe các sự kiện cập nhật đơn hàng:
 * - Cấu hình concurrency = "5" tương ứng tối đa với 5 Partitions của topic "order-tracking".
 * - Kiểm tra tính toàn vẹn thứ tự thời gian của từng đơn hàng:
 *   + CREATED -> PAID -> SHIPPED.
 * - Phát hiện và cảnh báo ngay lập tức nếu xảy ra hiện tượng "nhảy cóc" trạng thái (Ví dụ: SHIPPED trước PAID).
 */
@Service
@Slf4j
public class OrderTrackingConsumer {

    // Bảng lưu vết trạng thái gần nhất của từng đơn hàng để kiểm tra tính toàn vẹn thứ tự
    private final ConcurrentHashMap<Long, OrderStatus> orderCurrentState = new ConcurrentHashMap<>();

    @KafkaListener(topics = "order-tracking", groupId = "order-tracking-group", concurrency = "5")
    public void consume(
            ConsumerRecord<String, OrderTrackingEvent> record,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        OrderTrackingEvent event = record.value();
        String key = record.key();
        Long orderId = event.getOrderId();
        OrderStatus newStatus = event.getStatus();

        log.info("📥 [Consumer Thread: {}] Nhận sự kiện từ Partition [{}], Offset [{}], Key [{}]: orderId={}, status={}",
                Thread.currentThread().getName(), partition, offset, key, orderId, newStatus);

        validateAndProcessStatusTransition(orderId, newStatus, partition, offset);
    }

    /**
     * Xác thực thứ tự trạng thái hợp lệ của đơn hàng.
     */
    public synchronized void validateAndProcessStatusTransition(Long orderId, OrderStatus newStatus, int partition, long offset) {
        OrderStatus currentStatus = orderCurrentState.get(orderId);

        if (currentStatus == null) {
            if (newStatus != OrderStatus.CREATED) {
                log.error("❌ LỖI NGHIỆP VỤ MẤT THỨ TỰ: Đơn hàng [{}] bắt đầu bằng trạng thái [{}] thay vì CREATED! (Partition: {}, Offset: {})",
                        orderId, newStatus, partition, offset);
                throw new IllegalStateException("Lỗi thứ tự: Đơn hàng " + orderId + " chưa có CREATED nhưng nhận " + newStatus);
            }
            orderCurrentState.put(orderId, OrderStatus.CREATED);
            log.info("✅ Đơn hàng [{}] khởi tạo CREATED hợp lệ.", orderId);
            return;
        }

        switch (currentStatus) {
            case CREATED:
                if (newStatus == OrderStatus.PAID) {
                    orderCurrentState.put(orderId, OrderStatus.PAID);
                    log.info("✅ Đơn hàng [{}] chuyển trạng thái CREATED -> PAID hợp lệ.", orderId);
                } else {
                    log.error("❌ LỖI NGHIỆP VỤ: Đơn hàng [{}] đang CREATED nhưng nhận trạng thái [{}] (chưa thanh toán mà đã giao)!",
                            orderId, newStatus);
                    throw new IllegalStateException("Lỗi thứ tự: Đơn hàng " + orderId + " nhận " + newStatus + " khi đang CREATED");
                }
                break;

            case PAID:
                if (newStatus == OrderStatus.SHIPPED) {
                    orderCurrentState.put(orderId, OrderStatus.SHIPPED);
                    log.info("✅ Đơn hàng [{}] chuyển trạng thái PAID -> SHIPPED hợp lệ.", orderId);
                } else {
                    log.warn("⚠️ Đơn hàng [{}] đang ở trạng thái PAID nhưng nhận lại [{}]", orderId, newStatus);
                }
                break;

            case SHIPPED:
                log.warn("⚠️ Đơn hàng [{}] đã hoàn tất SHIPPED, bỏ qua sự kiện thừa: [{}]", orderId, newStatus);
                break;
        }
    }

    public OrderStatus getCurrentStatus(Long orderId) {
        return orderCurrentState.get(orderId);
    }

    public void reset() {
        orderCurrentState.clear();
    }
}
