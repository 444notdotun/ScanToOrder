package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import com.scantoorder.scantoorder.dtos.request.LoginRequest;
import com.scantoorder.scantoorder.dtos.respond.AuthResponse;
import com.scantoorder.scantoorder.service.Interface.Auth;
import com.scantoorder.scantoorder.service.Interface.JwtService;
import org.jspecify.annotations.NonNull;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AuthImplementation implements Auth {
    @Autowired
    private JwtService jwtService;
    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    @org.springframework.context.annotation.Lazy
    private AuthenticationManager authenticationManager;

    ModelMapper modelMapper = new ModelMapper();
    
    @Override
    public AuthResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );
        Worker worker = (Worker) authentication.getPrincipal();
        AuthResponse authResponse = new AuthResponse();
        authResponse.setWorkerId(worker.getWorkerId());
        authResponse.setUsername(worker.getUsername());
        authResponse.setRole(worker.getRole());
        authResponse.setToken(jwtService.generateToken(worker));
        return authResponse;
    }
    @Override
    public UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        String cleanUsername = username.trim();
        return workerRepo.findByUsername(cleanUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Worker not found"));
    }
}
