package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.data.model.UnavailableReason;
import com.scantoorder.scantoorder.dtos.request.SwapRequest;
import com.scantoorder.scantoorder.dtos.respond.OrderActionResponse;
import com.scantoorder.scantoorder.dtos.respond.RefundQueueResponse;

/**
 * What happens when the kitchen cannot make something that has been paid for.
 */
public interface OrderRecoveryService {
    OrderActionResponse markUnavailable(String tenantId, String orderId, UnavailableReason reason);

    OrderActionResponse swap(String tenantId, String orderId, SwapRequest request);

    OrderActionResponse requestRefund(String tenantId, String orderId);

    RefundQueueResponse refundQueue(String tenantId);

    OrderActionResponse approveRefund(String tenantId, String orderId);
}
