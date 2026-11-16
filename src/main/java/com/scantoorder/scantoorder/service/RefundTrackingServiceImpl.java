package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderItem;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.Refund;
import com.scantoorder.scantoorder.data.model.RefundClosedBy;
import com.scantoorder.scantoorder.data.model.RefundMethod;
import com.scantoorder.scantoorder.data.model.RefundReason;
import com.scantoorder.scantoorder.data.model.RefundStatus;
import com.scantoorder.scantoorder.data.model.SessionSeat;
import com.scantoorder.scantoorder.data.repository.OrderItemRepo;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.data.repository.RefundRepo;
import com.scantoorder.scantoorder.dtos.request.GuestBankDetailsRequest;
import com.scantoorder.scantoorder.dtos.request.GuestEmailRequest;
import com.scantoorder.scantoorder.dtos.request.SettleRefundRequest;
import com.scantoorder.scantoorder.dtos.respond.GuestRefundResponse;
import com.scantoorder.scantoorder.dtos.respond.ManagerRefundResponse;
import com.scantoorder.scantoorder.dtos.respond.PlatformRefundResponse;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.service.Interface.RefundTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RefundTrackingServiceImpl implements RefundTrackingService {

    // A refund still unpaid after this long shows to the platform owner.
    public static final Duration OVERDUE_AFTER = Duration.ofHours(48);
    public static final String ADD_EMAIL_FIRST = "Add your email first.";
    static final String NOT_FOUND = "We cannot find that refund";

    @Autowired
    private RefundRepo refundRepo;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private OrderItemRepo orderItemRepo;
    @Autowired
    private BankResolver bankResolver;
    @Autowired
    private RefundService refundService;

    // ---- guest ----

    @Transactional(readOnly = true)
    @Override
    public GuestRefundResponse guestView(String refundId, String token) {
        return guestResponse(refundRepo.findByToken(refundId, token).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND)));
    }

    @Transactional
    @Override
    public GuestRefundResponse guestEmail(String refundId, String token, GuestEmailRequest request) {
        Refund refund = guestLock(refundId, token);
        if (refund.getStatus() == RefundStatus.CONFIRMED) {
            throw new ConflictException("This refund is already closed.");
        }
        boolean first = refund.getGuestEmail() == null;
        refund.setGuestEmail(request.getEmail().trim());
        refundRepo.save(refund);
        // The first time, the link they are already on is also sent to them, so they can come back to it.
        if (first) {
            refundService.sendLink(refund);
        }
        return guestResponse(refund);
    }

    @Transactional
    @Override
    public GuestRefundResponse guestBankDetails(String refundId, String token, GuestBankDetailsRequest request) {
        Refund refund = guestLockWithEmail(refundId, token);
        if (refund.getStatus() == RefundStatus.CONFIRMED) {
            throw new ConflictException("This refund is already closed.");
        }
        // Checked with the bank before it is kept. The number is never logged.
        BankResolver.Resolved resolved = bankResolver.resolve(request.getBankCode(), request.getAccountNumber());
        refund.setGuestBankCode(request.getBankCode());
        refund.setGuestBankName(resolved.bankName());
        refund.setGuestAccountNumber(request.getAccountNumber());
        refund.setGuestAccountName(resolved.accountName());
        refundRepo.save(refund);
        return guestResponse(refund);
    }

    @Transactional
    @Override
    public GuestRefundResponse guestReceived(String refundId, String token) {
        Refund refund = guestLockWithEmail(refundId, token);
        if (refund.getStatus() == RefundStatus.CONFIRMED) {
            throw new ConflictException("You have already told us this refund arrived.");
        }
        boolean wasDue = refund.getStatus() == RefundStatus.DUE;
        refund.setStatus(RefundStatus.CONFIRMED);
        refund.setGuestConfirmedAt(LocalDateTime.now());
        if (wasDue) {
            // The guest was paid at the table, in cash: nothing is left for the restaurant to do.
            refund.setClosedBy(RefundClosedBy.GUEST);
            finishOrder(refund);
        }
        refundRepo.save(refund);
        return guestResponse(refund);
    }

    @Transactional
    @Override
    public GuestRefundResponse guestNotReceived(String refundId, String token) {
        Refund refund = guestLockWithEmail(refundId, token);
        if (refund.getStatus() != RefundStatus.SETTLED) {
            throw new ConflictException("You can say a refund hasn't arrived once the restaurant says it has sent it.");
        }
        if (refund.getGuestReportedMissingAt() == null) {
            refund.setGuestReportedMissingAt(LocalDateTime.now());
            refundRepo.save(refund);
        }
        return guestResponse(refund);
    }

    // ---- manager ----

    @Transactional(readOnly = true)
    @Override
    public List<ManagerRefundResponse> list(String tenantId, String state) {
        String wanted = state == null || state.isBlank() ? "open" : state.trim().toLowerCase();
        if (!List.of("open", "closed", "all").contains(wanted)) {
            throw new InvalidRequestException("Ask for open, closed or all refunds.");
        }
        return refundRepo.findByTenant_TenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(refund -> wanted.equals("all") || (wanted.equals("open") == refund.isOpen()))
                .map(this::managerResponse)
                .toList();
    }

    @Transactional
    @Override
    public ManagerRefundResponse settle(String tenantId, String workerId, String refundId, SettleRefundRequest request) {
        Refund refund = refundRepo.findForUpdate(refundId, tenantId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (refund.getStatus() != RefundStatus.DUE) {
            throw new ConflictException("This refund has already been settled.");
        }
        String reference = request.getTransferReference() == null ? "" : request.getTransferReference().trim();
        if (request.getMethod() == RefundMethod.TRANSFER && reference.isEmpty()) {
            throw new InvalidRequestException("Add the bank's transfer reference so the payment can be found later.");
        }
        refund.setMethod(request.getMethod());
        refund.setTransferReference(request.getMethod() == RefundMethod.TRANSFER ? reference : null);
        refund.setStatus(RefundStatus.SETTLED);
        refund.setSettledAt(LocalDateTime.now());
        refund.setSettledBy(workerId);
        refund.setClosedBy(RefundClosedBy.MANAGER);
        finishOrder(refund);
        refundRepo.save(refund);
        // Only when the guest gave an email; a cash refund at the table settles without one.
        refundService.tellTheGuestItWasSent(refund);
        return managerResponse(refund);
    }

    // ---- platform ----

    @Transactional(readOnly = true)
    @Override
    public List<PlatformRefundResponse> platformProblems() {
        LocalDateTime now = LocalDateTime.now();
        return refundRepo.findProblems(now.minus(OVERDUE_AFTER)).stream()
                .map(refund -> new PlatformRefundResponse(
                        refund.getId(), refund.getTenant().getName(), refund.getOrder().getOrderNumber(),
                        naira(refund), refund.getReason().name(), refund.getStatus().name(),
                        refund.getCreatedAt(), isOverdue(refund, now), refund.getGuestReportedMissingAt() != null))
                .toList();
    }

    // ---- shared ----

    // Paying back a kitchen failure ends the order's story as REFUNDED.
    private void finishOrder(Refund refund) {
        if (refund.getReason() == RefundReason.KITCHEN_UNAVAILABLE) {
            Order order = refund.getOrder();
            order.setOrderStatus(OrderStatus.REFUNDED);
            orderRepo.save(order);
        }
    }

    // Until the guest has said where to reach them, nothing else on the refund page works.
    private Refund guestLockWithEmail(String refundId, String token) {
        Refund refund = guestLock(refundId, token);
        if (refund.getGuestEmail() == null || refund.getGuestEmail().isBlank()) {
            throw new ConflictException(ADD_EMAIL_FIRST);
        }
        return refund;
    }

    private Refund guestLock(String refundId, String token) {
        return refundRepo.findForUpdateByToken(refundId, token).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    private boolean isOverdue(Refund refund, LocalDateTime now) {
        return refund.getStatus() == RefundStatus.DUE && refund.getCreatedAt().isBefore(now.minus(OVERDUE_AFTER));
    }

    private BigDecimal naira(Refund refund) {
        return BigDecimal.valueOf(refund.getAmountKobo(), 2);
    }

    private List<String> itemLines(Order order) {
        return orderItemRepo.findByOrder_OrderId(order.getOrderId()).stream()
                .filter(line -> !line.isSwappedOut())
                .map((OrderItem line) -> line.getQuantity() + " x " + (line.getItem() == null ? "Item" : line.getItem().getItemName()))
                .toList();
    }

    private GuestRefundResponse guestResponse(Refund refund) {
        String restaurant = refund.getTenant().getName();
        String amount = Money.naira(refund.getAmountKobo());
        boolean missing = refund.getGuestReportedMissingAt() != null && refund.getStatus() == RefundStatus.SETTLED;
        String todo = switch (refund.getStatus()) {
            case DUE -> restaurant + " owes you " + amount + ". Add your bank details so they can transfer it, "
                    + "or tell us here once they have paid you, for example in cash at the table.";
            case SETTLED -> missing
                    ? "You told us the money hasn't arrived. We have let " + restaurant + " and Plately know. Tell us here when it does."
                    : restaurant + " says they sent your " + amount + ". Tell us here when it arrives, or tell us if it hasn't.";
            case CONFIRMED -> "You have confirmed your refund of " + amount + ". There is nothing more to do.";
        };
        GuestRefundResponse response = new GuestRefundResponse();
        response.setRefundId(refund.getId());
        response.setRestaurant(restaurant);
        response.setOrderNumber(refund.getOrder().getOrderNumber());
        response.setItems(itemLines(refund.getOrder()));
        response.setAmount(naira(refund));
        response.setStatus(refund.getStatus().name());
        boolean needsEmail = refund.getGuestEmail() == null || refund.getGuestEmail().isBlank();
        response.setNeedsEmail(needsEmail);
        response.setGuestEmail(refund.getGuestEmail());
        response.setWhatToDo(needsEmail && refund.getStatus() != RefundStatus.CONFIRMED
                ? "Where should we tell you about your refund?" : todo);
        response.setBankName(refund.getGuestBankName());
        response.setAccountName(refund.getGuestAccountName());
        String number = refund.getGuestAccountNumber();
        if (number != null && number.length() >= 4) {
            response.setAccountLast4(number.substring(number.length() - 4));
        }
        response.setCanAddBankDetails(!needsEmail && refund.getStatus() != RefundStatus.CONFIRMED);
        response.setCanConfirmReceived(!needsEmail && refund.getStatus() != RefundStatus.CONFIRMED);
        response.setCanReportMissing(!needsEmail && refund.getStatus() == RefundStatus.SETTLED && !missing);
        response.setReportedMissing(missing);
        return response;
    }

    private ManagerRefundResponse managerResponse(Refund refund) {
        Order order = refund.getOrder();
        SessionSeat guest = order.getSessionSeat();
        ManagerRefundResponse response = new ManagerRefundResponse();
        response.setRefundId(refund.getId());
        response.setGuestName(guest == null ? null : guest.getGuestName());
        response.setGuestPhone(guest == null ? null : guest.getGuestPhone());
        response.setTableNumber(order.getTable() == null ? null : order.getTable().getTableNumber());
        response.setSeatNumber(order.getSeat() == null ? null : order.getSeat().getSeatNumber());
        response.setOrderNumber(order.getOrderNumber());
        response.setItems(itemLines(order));
        response.setAmount(naira(refund));
        response.setPaymentReference(refund.getPayment().getReference());
        response.setDate(refund.getCreatedAt());
        response.setReason(refund.getReason().name());
        response.setStatus(refund.getStatus().name());
        response.setClosedBy(refund.getClosedBy() == null ? null : refund.getClosedBy().name());
        response.setGuestEmail(refund.getGuestEmail());
        response.setGuestLink(refundService.linkFor(refund));
        response.setGuestBankName(refund.getGuestBankName());
        response.setGuestAccountName(refund.getGuestAccountName());
        response.setGuestAccountNumber(refund.getGuestAccountNumber());
        response.setOverdue(isOverdue(refund, LocalDateTime.now()));
        response.setReportedMissing(refund.getGuestReportedMissingAt() != null && refund.getStatus() == RefundStatus.SETTLED);
        response.setMethod(refund.getMethod() == null ? null : refund.getMethod().name());
        response.setTransferReference(refund.getTransferReference());
        response.setSettledAt(refund.getSettledAt());
        return response;
    }
}
