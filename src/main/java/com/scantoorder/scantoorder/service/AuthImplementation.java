package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.TenantStatus;
import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.model.WorkerPrincipal;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import com.scantoorder.scantoorder.dtos.request.LoginRequest;
import com.scantoorder.scantoorder.dtos.respond.AuthResponse;
import com.scantoorder.scantoorder.exception.BadSignInException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.SuspendedTenantException;
import com.scantoorder.scantoorder.service.Interface.Auth;
import com.scantoorder.scantoorder.service.Interface.JwtService;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthImplementation implements Auth {

    public static final String BAD_SIGN_IN = "That username and password don't match";
    public static final String SUSPENDED = "This business is suspended on Plately. Contact us to sort it out.";

    @Autowired
    private JwtService jwtService;
    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    @org.springframework.context.annotation.Lazy
    private AuthenticationManager authenticationManager;

    // The same answer for an unknown username, a wrong password and a removed account.
    @Override
    public AuthResponse login(LoginRequest loginRequest) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername() == null ? null : loginRequest.getUsername().trim(),
                            loginRequest.getPassword()
                    )
            );
        } catch (AuthenticationException e) {
            throw new BadSignInException(BAD_SIGN_IN);
        }
        Worker worker = (Worker) authentication.getPrincipal();
        if (worker.getTenant() != null && worker.getTenant().getStatus() == TenantStatus.SUSPENDED) {
            throw new SuspendedTenantException(SUSPENDED);
        }
        return responseFor(worker);
    }

    @Transactional
    @Override
    public AuthResponse changePassword(WorkerPrincipal caller, String newPassword) {
        Worker worker = workerRepo.findById(caller.getWorkerId())
                .filter(Worker::isActive)
                .orElseThrow(() -> new BadSignInException(BAD_SIGN_IN));
        if (!worker.isMustChangePassword()) {
            throw new InvalidRequestException("Your password is already your own. There is nothing to change right now.");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new InvalidRequestException("Your new password needs at least 8 characters.");
        }
        if (passwordEncoder.matches(newPassword, worker.getPassword())) {
            throw new InvalidRequestException("Choose a password that is different from the temporary one.");
        }
        worker.setPassword(passwordEncoder.encode(newPassword));
        worker.setMustChangePassword(false);
        workerRepo.save(worker);
        return responseFor(worker);
    }

    private AuthResponse responseFor(Worker worker) {
        AuthResponse authResponse = new AuthResponse();
        authResponse.setWorkerId(worker.getWorkerId());
        authResponse.setUsername(worker.getUsername());
        authResponse.setRole(worker.getRole());
        authResponse.setMustChangePassword(worker.isMustChangePassword());
        authResponse.setFullName(worker.getFullName());
        if (worker.getTenant() != null) {
            authResponse.setBusinessName(worker.getTenant().getName());
            authResponse.setTenantType(worker.getTenant().getType().name());
        }
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
