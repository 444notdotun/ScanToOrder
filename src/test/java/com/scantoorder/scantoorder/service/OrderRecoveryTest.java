package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.data.repository.OrderItemRepo;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import com.scantoorder.scantoorder.dtos.request.CreateOrderItemRequest;
import com.scantoorder.scantoorder.dtos.request.SwapRequest;
import com.scantoorder.scantoorder.dtos.respond.KitchenOrderResponse;
import com.scantoorder.scantoorder.dtos.respond.ReceiptResponse;
import com.scantoorder.scantoorder.dtos.respond.RefundQueueResponse;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.OrderNotFoundException;
import com.scantoorder.scantoorder.exception.OrderStatusException;
import com.scantoorder.scantoorder.exception.RefundRejectedException;
import com.scantoorder.scantoorder.service.Interface.OrderRecoveryService;
import com.scantoorder.scantoorder.service.Interface.OrderService;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import com.scantoorder.scantoorder.service.Interface.ReceiptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class OrderRecoveryTest {
    @Autowired
    private OrderRecoveryService recovery;
    @Autowired
    private OrderService orderService;
    @Autowired
    private ReceiptService receiptService;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private OrderItemRepo orderItemRepo;
    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private PaymentTestSupport support;
    @MockitoBean
    private PaystackClient paystackClient;

    private Tenant tenant;
    private String tenantId;
    private Item jollof;
    private Item rice;

    @BeforeEach
    void setUp() {
        tenant = tenants.main();
        tenantId = tenant.getTenantId();
        jollof = support.item(tenant, "Jollof recovery", 2500, true);
        rice = support.item(tenant, "Rice recovery", 1800, true);
    }

    private Payment paid(OrderStatus status) {
        return support.paidOrder(tenant, status, jollof);
    }

    private String id(Payment payment) {
        return payment.getOrder().getOrderId();
    }

    private Order order(Payment payment) {
        return orderRepo.findOrderByOrderId(id(payment));
    }

    private SwapRequest swapTo(String name, int quantity) {
        CreateOrderItemRequest line = new CreateOrderItemRequest();
        line.setItemName(name);
        line.setQuantity(quantity);
        SwapRequest request = new SwapRequest();
        request.getItems().add(line);
        return request;
    }

    // ---- unavailable

    @Test
    void testThatTheKitchenCanGiveUpOnAnOrderThatIsPaidOrBeingCooked() {
        for (OrderStatus from : List.of(OrderStatus.PAID, OrderStatus.PREPARING)) {
            Payment payment = paid(from);
            assertEquals("UNAVAILABLE", recovery.markUnavailable(tenantId, id(payment), UnavailableReason.EQUIPMENT_DOWN).getStatus());
            assertEquals(OrderStatus.UNAVAILABLE, order(payment).getOrderStatus());
            assertEquals(UnavailableReason.EQUIPMENT_DOWN, order(payment).getUnavailableReason());
        }
    }

    @Test
    void testThatAnOrderInAnyOtherStateCannotBeMarkedUnavailable() {
        for (OrderStatus from : List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.READY, OrderStatus.DELIVERED, OrderStatus.CANCELED,
                OrderStatus.UNAVAILABLE, OrderStatus.REFUND_REQUESTED, OrderStatus.REFUNDED)) {
            Payment payment = paid(from);
            assertThrows(OrderStatusException.class, () -> recovery.markUnavailable(tenantId, id(payment), UnavailableReason.RAN_OUT), from.name());
            assertEquals(from, order(payment).getOrderStatus());
        }
    }

    @Test
    void testThatRunningOutTakesThePlatesOffTheMenuButOtherReasonsDoNot() {
        Item soup = support.item(tenant, "Soup recovery", 1000, true);
        Payment ranOut = support.paidOrder(tenant, OrderStatus.PAID, soup);
        recovery.markUnavailable(tenantId, id(ranOut), UnavailableReason.RAN_OUT);
        assertFalse(itemRepo.findById(soup.getItemId()).orElseThrow().isAvailable());
        assertTrue(itemRepo.findById(rice.getItemId()).orElseThrow().isAvailable());

        Payment equipment = paid(OrderStatus.PAID);
        recovery.markUnavailable(tenantId, id(equipment), UnavailableReason.SOMETHING_WRONG);
        assertTrue(itemRepo.findById(jollof.getItemId()).orElseThrow().isAvailable());
    }

    // ---- swap

    @Test
    void testThatAWaiterCanSwapAnUnavailableOrderBackToPaidWithNewItemsAndANewTicket() {
        Payment payment = paid(OrderStatus.UNAVAILABLE);
        long before = order(payment).getTotalAmount().longValue();

        var response = recovery.swap(tenantId, id(payment), swapTo("Rice recovery", 2));

        assertEquals("PAID", response.getStatus());
        Order saved = order(payment);
        assertEquals(OrderStatus.PAID, saved.getOrderStatus());
        assertNotNull(saved.getSwappedAt());
        assertEquals(before, saved.getTotalAmount().longValue());
        assertTrue(saved.getNote().contains("was NGN 2,500.00, now NGN 3,600.00"));
        List<OrderItem> lines = orderItemRepo.findByOrder_OrderId(id(payment));
        assertEquals(2, lines.size());
        assertTrue(lines.stream().filter(l -> l.getItem().getItemName().equals("Jollof recovery")).allMatch(OrderItem::isSwappedOut));
        assertTrue(lines.stream().filter(l -> l.getItem().getItemName().equals("Rice recovery")).noneMatch(OrderItem::isSwappedOut));
        assertEquals(PaymentStatus.SUCCESSFUL, paymentRepo.findByReference(payment.getReference()).orElseThrow().getStatus());
        verifyNoInteractions(paystackClient);
    }

    @Test
    void testThatTheKitchenSeesTheSwappedOrderAsANewTicketWithOnlyTheNewItems() {
        Payment payment = paid(OrderStatus.UNAVAILABLE);
        recovery.swap(tenantId, id(payment), swapTo("Rice recovery", 1));

        KitchenOrderResponse ticket = orderService.getKitchenOrders(tenantId, null).stream()
                .filter(o -> o.getOrderId().equals(id(payment))).findFirst().orElseThrow();

        assertEquals("PAID", ticket.getStatus());
        assertEquals(1, ticket.getItems().size());
        assertEquals("Rice recovery", ticket.getItems().get(0).getItemName());
        assertEquals(order(payment).getSwappedAt(), ticket.getCreatedAt());
        assertNotNull(ticket.getNote());
    }

    @Test
    void testThatASwapToSomethingOutOfStockOrUnknownChangesNothing() {
        Item gone = support.item(tenant, "Gone recovery", 900, false);
        Payment payment = paid(OrderStatus.UNAVAILABLE);

        ConflictException exception = assertThrows(ConflictException.class, () -> recovery.swap(tenantId, id(payment), swapTo(gone.getItemName(), 1)));
        assertTrue(exception.getMessage().contains("Gone recovery"));
        assertThrows(ConflictException.class, () -> recovery.swap(tenantId, id(payment), swapTo("Not on the menu", 1)));
        assertThrows(InvalidRequestException.class, () -> recovery.swap(tenantId, id(payment), swapTo("Rice recovery", 0)));
        assertThrows(InvalidRequestException.class, () -> recovery.swap(tenantId, id(payment), new SwapRequest()));

        assertEquals(OrderStatus.UNAVAILABLE, order(payment).getOrderStatus());
        assertTrue(orderItemRepo.findByOrder_OrderId(id(payment)).stream().noneMatch(OrderItem::isSwappedOut));
        assertEquals(1, orderItemRepo.findByOrder_OrderId(id(payment)).size());
    }

    @Test
    void testThatOnlyAnUnavailableOrderCanBeSwapped() {
        for (OrderStatus from : List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.READY,
                OrderStatus.DELIVERED, OrderStatus.CANCELED, OrderStatus.REFUND_REQUESTED, OrderStatus.REFUNDED)) {
            Payment payment = paid(from);
            assertThrows(OrderStatusException.class, () -> recovery.swap(tenantId, id(payment), swapTo("Rice recovery", 1)), from.name());
        }
    }

    @Test
    void testThatASwapNoteKeepsWhatTheWaiterWrote() {
        Payment payment = paid(OrderStatus.UNAVAILABLE);
        SwapRequest request = swapTo("Rice recovery", 1);
        request.setNote("Guest paid the difference in cash");
        recovery.swap(tenantId, id(payment), request);
        assertTrue(order(payment).getNote().endsWith("Guest paid the difference in cash"));
    }

    // ---- refund request

    @Test
    void testThatAWaiterCanAskForARefundOnlyForAnUnavailableOrder() {
        Payment payment = paid(OrderStatus.UNAVAILABLE);
        assertEquals("REFUND_REQUESTED", recovery.requestRefund(tenantId, id(payment)).getStatus());
        assertEquals(OrderStatus.REFUND_REQUESTED, order(payment).getOrderStatus());

        for (OrderStatus from : List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.READY,
                OrderStatus.DELIVERED, OrderStatus.CANCELED, OrderStatus.REFUND_REQUESTED, OrderStatus.REFUNDED)) {
            Payment other = paid(from);
            assertThrows(OrderStatusException.class, () -> recovery.requestRefund(tenantId, id(other)), from.name());
        }
    }

    @Test
    void testThatTheRefundListShowsRequestsAndFailedRefundsOfThisBusinessOnly() {
        Payment requested = paid(OrderStatus.UNAVAILABLE);
        recovery.markUnavailable(tenantId, id(paid(OrderStatus.PAID)), UnavailableReason.RAN_OUT);
        Order asked = order(requested);
        asked.setUnavailableReason(UnavailableReason.EQUIPMENT_DOWN);
        orderRepo.save(asked);
        recovery.requestRefund(tenantId, id(requested));
        Payment failed = support.payment(support.order(tenant, OrderStatus.REFUNDED, jollof, 1), PaymentStatus.REFUND_FAILED);
        Tenant other = tenants.other();
        Item theirs = support.item(other, "Their refund plate", 700, true);
        support.paidOrder(other, OrderStatus.REFUND_REQUESTED, theirs);
        support.payment(support.order(other, OrderStatus.REFUNDED, theirs, 1), PaymentStatus.REFUND_FAILED);

        RefundQueueResponse queue = recovery.refundQueue(tenantId);

        assertEquals(1, queue.getRequests().stream().filter(r -> r.getOrderId().equals(id(requested))).count());
        assertEquals("EQUIPMENT_DOWN", queue.getRequests().stream().filter(r -> r.getOrderId().equals(id(requested))).findFirst().orElseThrow().getReason());
        assertEquals(1, queue.getRequests().stream().filter(r -> r.getOrderId().equals(id(requested))).findFirst().orElseThrow().getAmount().compareTo(java.math.BigDecimal.ZERO));
        assertTrue(queue.getFailedRefunds().stream().anyMatch(f -> f.getPaymentReference().equals(failed.getReference())));
        assertTrue(queue.getRequests().stream().allMatch(r -> orderRepo.findOrderByOrderId(r.getOrderId()).getTenant().getTenantId().equals(tenantId)));
        assertEquals(queue.getFailedRefunds().size(), queue.getFailedRefunds().stream().filter(f -> paymentRepo.findByReference(f.getPaymentReference()).orElseThrow().getTenant().getTenantId().equals(tenantId)).count());
    }

    // ---- approve

    @Test
    void testThatApprovingARefundAsksPaystackForTheFullAmountAndEndsTheOrderAsRefunded() {
        Payment payment = paid(OrderStatus.REFUND_REQUESTED);

        var response = recovery.approveRefund(tenantId, id(payment));

        assertEquals("REFUNDED", response.getStatus());
        assertEquals(OrderStatus.REFUNDED, order(payment).getOrderStatus());
        Payment saved = paymentRepo.findByReference(payment.getReference()).orElseThrow();
        assertEquals(PaymentStatus.REFUND_PENDING, saved.getStatus());
        assertEquals("RF-" + payment.getReference() + "-1", saved.getRefundReference());
        verify(paystackClient, times(1)).createRefund(payment.getReference(), "RF-" + payment.getReference() + "-1");
    }

    @Test
    void testThatApprovingTwiceDoesNotRefundTwice() {
        Payment payment = paid(OrderStatus.REFUND_REQUESTED);
        recovery.approveRefund(tenantId, id(payment));
        assertThrows(OrderStatusException.class, () -> recovery.approveRefund(tenantId, id(payment)));
        verify(paystackClient, times(1)).createRefund(anyString(), anyString());
    }

    @Test
    void testThatARefundCannotBeApprovedFromAnyOtherState() {
        for (OrderStatus from : List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.READY,
                OrderStatus.DELIVERED, OrderStatus.CANCELED, OrderStatus.UNAVAILABLE)) {
            Payment payment = paid(from);
            assertThrows(OrderStatusException.class, () -> recovery.approveRefund(tenantId, id(payment)), from.name());
            assertEquals(from, order(payment).getOrderStatus());
        }
        verifyNoInteractions(paystackClient);
    }

    @Test
    void testThatAnOrderWithNoPaymentCannotBeRefunded() {
        Order unpaid = support.order(tenant, OrderStatus.REFUND_REQUESTED, jollof, 1);
        assertThrows(OrderStatusException.class, () -> recovery.approveRefund(tenantId, unpaid.getOrderId()));
    }

    @Test
    void testThatWhenPaystackCannotStartTheRefundNothingChanges() {
        Payment payment = paid(OrderStatus.REFUND_REQUESTED);
        doThrow(new RefundRejectedException("Paystack could not start the refund")).when(paystackClient).createRefund(anyString(), anyString());

        assertThrows(RefundRejectedException.class, () -> recovery.approveRefund(tenantId, id(payment)));

        assertEquals(PaymentStatus.SUCCESSFUL, paymentRepo.findByReference(payment.getReference()).orElseThrow().getStatus());
    }

    @Test
    void testThatAnotherBusinessesOrderIsNotFoundForEveryAction() {
        Tenant other = tenants.other();
        Item theirs = support.item(other, "Hidden plate", 700, true);
        Payment payment = support.paidOrder(other, OrderStatus.UNAVAILABLE, theirs);
        String theirOrder = id(payment);

        assertThrows(OrderNotFoundException.class, () -> recovery.markUnavailable(tenantId, theirOrder, UnavailableReason.RAN_OUT));
        assertThrows(OrderNotFoundException.class, () -> recovery.swap(tenantId, theirOrder, swapTo("Rice recovery", 1)));
        assertThrows(OrderNotFoundException.class, () -> recovery.requestRefund(tenantId, theirOrder));
        assertThrows(OrderNotFoundException.class, () -> recovery.approveRefund(tenantId, theirOrder));
        assertThrows(OrderNotFoundException.class, () -> recovery.approveRefund(tenantId, "no-such-order"));
        assertEquals(OrderStatus.UNAVAILABLE, order(payment).getOrderStatus());
    }

    // ---- receipt and lists

    @Test
    void testThatTheReceiptShowsRefundedForARefundedOrderEvenWhileThePaymentIsStillPending() {
        Payment payment = paid(OrderStatus.REFUND_REQUESTED);
        recovery.approveRefund(tenantId, id(payment));

        ReceiptResponse receipt = receiptService.getReceiptData(tenantId, payment.getReference());

        assertEquals(PaymentStatus.REFUNDED, receipt.getStatus());
        assertEquals(2500, receipt.getTotalPaid().intValue());
        assertTrue(new String(receiptService.generateReceiptCsv(tenantId, payment.getReference())).contains("REFUNDED"));
    }

    @Test
    void testThatAReceiptOfASwappedOrderListsOnlyWhatWasServed() {
        Payment payment = paid(OrderStatus.UNAVAILABLE);
        recovery.swap(tenantId, id(payment), swapTo("Rice recovery", 1));
        ReceiptResponse receipt = receiptService.getReceiptData(tenantId, payment.getReference());
        assertEquals(1, receipt.getItems().size());
        assertEquals("Rice recovery", receipt.getItems().get(0).getItemName());
    }

    @Test
    void testThatUnavailableOrdersAppearOnTheKitchenAndWaiterLists() {
        Payment payment = paid(OrderStatus.PAID);
        recovery.markUnavailable(tenantId, id(payment), UnavailableReason.SOMETHING_WRONG);

        KitchenOrderResponse onKitchenList = orderService.getKitchenOrders(tenantId, null).stream()
                .filter(o -> o.getOrderId().equals(id(payment))).findFirst().orElseThrow();
        assertEquals("UNAVAILABLE", onKitchenList.getStatus());
        assertEquals("SOMETHING_WRONG", onKitchenList.getUnavailableReason());

        assertTrue(orderService.getKitchenOrders(tenantId, "READY").stream().anyMatch(o -> o.getOrderId().equals(id(payment)) && o.getStatus().equals("UNAVAILABLE")));
        recovery.requestRefund(tenantId, id(payment));
        assertTrue(orderService.getKitchenOrders(tenantId, "READY").stream().anyMatch(o -> o.getOrderId().equals(id(payment)) && o.getStatus().equals("REFUND_REQUESTED")));
        assertTrue(orderService.getKitchenOrders(tenantId, null).stream().noneMatch(o -> o.getOrderId().equals(id(payment))));
    }
}
