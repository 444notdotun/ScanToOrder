package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.data.model.ServiceCall;
import com.scantoorder.scantoorder.dtos.request.CreateServiceCallRequest;

import java.util.List;

public interface ServiceCallService {
    ServiceCall createServiceCall(CreateServiceCallRequest request, String tenantId, String sessionId);

    List<ServiceCall> getAllServiceCalls(String tenantId);

    ServiceCall updateServiceCallStatus(String tenantId, String id, String status, String waiterName);
}
