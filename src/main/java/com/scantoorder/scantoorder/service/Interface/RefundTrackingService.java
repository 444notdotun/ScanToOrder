package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.request.GuestBankDetailsRequest;
import com.scantoorder.scantoorder.dtos.request.SettleRefundRequest;
import com.scantoorder.scantoorder.dtos.respond.GuestRefundResponse;
import com.scantoorder.scantoorder.dtos.respond.ManagerRefundResponse;
import com.scantoorder.scantoorder.dtos.respond.PlatformRefundResponse;

import java.util.List;

/**
 * Refunds the restaurant owes guests: what the guest can do from their link, what the manager does,
 * and what the platform owner watches. Plately moves no money.
 */
public interface RefundTrackingService {
    GuestRefundResponse guestView(String refundId, String token);

    GuestRefundResponse guestBankDetails(String refundId, String token, GuestBankDetailsRequest request);

    GuestRefundResponse guestReceived(String refundId, String token);

    GuestRefundResponse guestNotReceived(String refundId, String token);

    // state is open, closed or all.
    List<ManagerRefundResponse> list(String tenantId, String state);

    ManagerRefundResponse settle(String tenantId, String workerId, String refundId, SettleRefundRequest request);

    // Refunds overdue or reported missing, across every business.
    List<PlatformRefundResponse> platformProblems();
}
