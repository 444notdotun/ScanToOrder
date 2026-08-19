package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Manager;
import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.repository.ManagerRepo;
import com.scantoorder.scantoorder.dtos.request.LoginRequest;
import com.scantoorder.scantoorder.dtos.respond.AuthResponse;
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
    private  JwtService jwtService;
    @Autowired
    private ManagerRepo managerRepo;
    @Autowired
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
        authResponse.setUsername(worker.getWorkerId());
        authResponse.setRole(worker.getRole());
        authResponse.setToken(jwtService.generateToken(worker));
        return authResponse;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return managerRepo.findManagerBy(username).orElseThrow(() -> new UsernameNotFoundException("Manager not found"));
    }
}
