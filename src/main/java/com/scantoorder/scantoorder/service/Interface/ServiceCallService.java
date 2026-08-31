package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.request.CreateServiceCallRequest;
import com.scantoorder.scantoorder.data.model.ServiceCall;

public interface ServiceCallService {
    ServiceCall createServiceCall(CreateServiceCallRequest request, String sessionId);
    java.util.List<ServiceCall> getAllServiceCalls();
    ServiceCall updateServiceCallStatus(String id, String status, String waiterName);
}
