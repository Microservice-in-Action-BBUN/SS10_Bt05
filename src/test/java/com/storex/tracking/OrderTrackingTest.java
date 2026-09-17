package com.storex.tracking;

import com.storex.tracking.config.KafkaTopicConfig;
import com.storex.tracking.consumer.OrderTrackingConsumer;
import com.storex.tracking.model.OrderStatus;
import com.storex.tracking.model.OrderTrackingEvent;
import com.storex.tracking.producer.OrderTrackingProducer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.utils.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderTrackingTest {

    @Mock
    private KafkaTemplate<String, OrderTrackingEvent> kafkaTemplate;

    private OrderTrackingProducer producer;
    private OrderTrackingConsumer consumer;

    @BeforeEach
    void setUp() {
        producer = new OrderTrackingProducer(kafkaTemplate);
        consumer = new OrderTrackingConsumer();
        consumer.reset();
    }

    @Test
    @DisplayName("Unit Test 1: Producer gán đúng orderId làm Partition Key cho toàn bộ sự kiện")
    void testProducer_UsesOrderIdAsPartitionKey() {
        // Arrange
        Long orderId = 8888L;
        String expectedKey = "8888";
        when(kafkaTemplate.send(eq(KafkaTopicConfig.ORDER_TRACKING_TOPIC), eq(expectedKey), any(OrderTrackingEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act: Gửi 3 sự kiện của cùng 1 đơn hàng
        producer.sendOrderCreated(orderId);
        producer.sendOrderPaid(orderId);
        producer.sendOrderShipped(orderId);

        // Assert: Xác nhận kafkaTemplate được gọi đúng 3 lần với key = "8888"
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<OrderTrackingEvent> eventCaptor = ArgumentCaptor.forClass(OrderTrackingEvent.class);

        verify(kafkaTemplate, times(3)).send(
                eq(KafkaTopicConfig.ORDER_TRACKING_TOPIC),
                keyCaptor.capture(),
                eventCaptor.capture()
        );

        // Tất cả 3 lần gọi đều dùng chung key "8888"
        assertEquals(expectedKey, keyCaptor.getAllValues().get(0));
        assertEquals(expectedKey, keyCaptor.getAllValues().get(1));
        assertEquals(expectedKey, keyCaptor.getAllValues().get(2));

        assertEquals(OrderStatus.CREATED, eventCaptor.getAllValues().get(0).getStatus());
        assertEquals(OrderStatus.PAID, eventCaptor.getAllValues().get(1).getStatus());
        assertEquals(OrderStatus.SHIPPED, eventCaptor.getAllValues().get(2).getStatus());
    }

    @Test
    @DisplayName("Unit Test 2: Consumer xử lý tuần tự hợp lệ (CREATED -> PAID -> SHIPPED)")
    void testConsumer_ProcessesEventsInOrder() {
        Long orderId = 1001L;

        // 1. Nhận sự kiện CREATED
        ConsumerRecord<String, OrderTrackingEvent> r1 = new ConsumerRecord<>("order-tracking", 2, 10L, "1001",
                new OrderTrackingEvent(orderId, OrderStatus.CREATED, 1000L, "Created"));
        consumer.consume(r1, 2, 10L);
        assertEquals(OrderStatus.CREATED, consumer.getCurrentStatus(orderId));

        // 2. Nhận sự kiện PAID
        ConsumerRecord<String, OrderTrackingEvent> r2 = new ConsumerRecord<>("order-tracking", 2, 11L, "1001",
                new OrderTrackingEvent(orderId, OrderStatus.PAID, 2000L, "Paid"));
        consumer.consume(r2, 2, 11L);
        assertEquals(OrderStatus.PAID, consumer.getCurrentStatus(orderId));

        // 3. Nhận sự kiện SHIPPED
        ConsumerRecord<String, OrderTrackingEvent> r3 = new ConsumerRecord<>("order-tracking", 2, 12L, "1001",
                new OrderTrackingEvent(orderId, OrderStatus.SHIPPED, 3000L, "Shipped"));
        consumer.consume(r3, 2, 12L);
        assertEquals(OrderStatus.SHIPPED, consumer.getCurrentStatus(orderId));
    }

    @Test
    @DisplayName("Unit Test 3: Consumer phát hiện và chặn lỗi khi sự kiện bị xáo trộn thứ tự (Out of order)")
    void testConsumer_DetectsOutOfOrderTransition() {
        Long orderId = 2002L;

        // 1. Nhận sự kiện CREATED
        ConsumerRecord<String, OrderTrackingEvent> r1 = new ConsumerRecord<>("order-tracking", 1, 5L, "2002",
                new OrderTrackingEvent(orderId, OrderStatus.CREATED, 1000L, "Created"));
        consumer.consume(r1, 1, 5L);

        // 2. Mô phỏng lỗi do mất thứ tự: Nhận SHIPPED khi chưa thanh toán (chưa có PAID)
        ConsumerRecord<String, OrderTrackingEvent> outOfOrderRecord = new ConsumerRecord<>("order-tracking", 3, 20L, "2002",
                new OrderTrackingEvent(orderId, OrderStatus.SHIPPED, 3000L, "Shipped prematurely"));

        assertThrows(IllegalStateException.class, () -> {
            consumer.consume(outOfOrderRecord, 3, 20L);
        }, "Hệ thống phải chặn lỗi khi nhận SHIPPED trước khi PAID");
    }

    @Test
    @DisplayName("Unit Test 4: Chứng minh thuật toán băm MurmurHash2 luôn ép cùng key vào 1 Partition duy nhất")
    void testMurmurHash2_GuaranteesSinglePartition() {
        int totalPartitions = 5;
        String key = "ORDER_99999";
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);

        // Thuật toán băm chuẩn mực của Kafka Partitioner
        int targetPartition = Utils.toPositive(Utils.murmur2(keyBytes)) % totalPartitions;

        // Thử nghiệm 1.000 lần liên tiếp với cùng một key
        for (int i = 0; i < 1000; i++) {
            int partition = Utils.toPositive(Utils.murmur2(keyBytes)) % totalPartitions;
            assertEquals(targetPartition, partition, "Cùng key bắt buộc phải luôn ra cùng một partition duy nhất!");
        }

        assertTrue(targetPartition >= 0 && targetPartition < totalPartitions,
                "Partition tính được phải nằm trong khoảng [0, 4]");
    }
}
