package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.DinningSession;
import com.scantoorder.scantoorder.data.model.ServiceCall;
import com.scantoorder.scantoorder.data.repository.DinningSessionRepo;
import com.scantoorder.scantoorder.data.repository.ServiceCallRepo;
import com.scantoorder.scantoorder.dtos.request.CreateServiceCallRequest;
import com.scantoorder.scantoorder.service.Interface.ServiceCallService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ServiceCallServiceImpl implements ServiceCallService {

    @Autowired
    private ServiceCallRepo serviceCallRepo;

    @Autowired
    private DinningSessionRepo dinningSessionRepo;

    @Override
    public ServiceCall createServiceCall(CreateServiceCallRequest request, String sessionId) {
        Optional<DinningSession> sessionOpt = dinningSessionRepo.findDinningSessionBySessionId(sessionId);

        ServiceCall serviceCall = new ServiceCall();
        sessionOpt.ifPresent(serviceCall::setSessionId);
        
        String description = request.getRequestType();
        if (request.getNote() != null && !request.getNote().isEmpty()) {
            description += " - " + request.getNote();
        }
        serviceCall.setServiceDescription(description);

        return serviceCallRepo.save(serviceCall);
    }

    @Override
    public java.util.List<ServiceCall> getAllServiceCalls() {
        return serviceCallRepo.findAll();
    }

    @Override
    public ServiceCall updateServiceCallStatus(String id, String status, String waiterName) {
        ServiceCall call = serviceCallRepo.findById(id).orElseThrow(() -> new RuntimeException("Service call not found"));
        
        com.scantoorder.scantoorder.data.model.ServiceStatus newStatus = com.scantoorder.scantoorder.data.model.ServiceStatus.valueOf(status);
        call.setServiceStatus(newStatus);
        
        if (newStatus == com.scantoorder.scantoorder.data.model.ServiceStatus.RESOLVED) {
            call.setResolvedAt(java.time.LocalDateTime.now());
        }
        
        return serviceCallRepo.save(call);
    }
}
