package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.DinningSession;
import com.scantoorder.scantoorder.data.model.ServiceCall;
import com.scantoorder.scantoorder.data.model.ServiceStatus;
import com.scantoorder.scantoorder.data.repository.DinningSessionRepo;
import com.scantoorder.scantoorder.data.repository.ServiceCallRepo;
import com.scantoorder.scantoorder.dtos.request.CreateServiceCallRequest;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.exception.SessionNotFoundException;
import com.scantoorder.scantoorder.service.Interface.ServiceCallService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ServiceCallServiceImpl implements ServiceCallService {

    @Autowired
    private ServiceCallRepo serviceCallRepo;

    @Autowired
    private DinningSessionRepo dinningSessionRepo;

    @Autowired
    private LiveUpdates liveUpdates;

    @Override
    public ServiceCall createServiceCall(CreateServiceCallRequest request, String tenantId, String sessionId) {
        DinningSession session = dinningSessionRepo.findBySessionIdAndTenant_TenantId(sessionId, tenantId)
                .orElseThrow(() -> new SessionNotFoundException("We cannot find your session"));
        ServiceCall serviceCall = new ServiceCall();
        serviceCall.setTenant(session.getTenant());
        serviceCall.setSessionId(session);

        String description = request.getRequestType();
        if (request.getNote() != null && !request.getNote().isEmpty()) {
            description += " - " + request.getNote();
        }
        serviceCall.setServiceDescription(description);

        ServiceCall saved = serviceCallRepo.save(serviceCall);
        liveUpdates.publish(tenantId, sessionId, "call", saved.getServiceCallId(), "OPEN");
        return saved;
    }

    @Override
    public List<ServiceCall> getAllServiceCalls(String tenantId) {
        return serviceCallRepo.findAllByTenant_TenantId(tenantId);
    }

    @Override
    public ServiceCall updateServiceCallStatus(String tenantId, String id, String status, String waiterName) {
        ServiceCall call = serviceCallRepo.findByServiceCallIdAndTenant_TenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("We cannot find that request"));
        ServiceStatus newStatus = ServiceStatus.valueOf(status);
        call.setServiceStatus(newStatus);
        if (newStatus == ServiceStatus.RESOLVED) {
            call.setResolvedAt(LocalDateTime.now());
        }
        return serviceCallRepo.save(call);
    }
}
