package com.scantoorder.scantoorder.config;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.WorkerPrincipal;
import com.scantoorder.scantoorder.service.Interface.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
@Slf4j
@Component
public class JwtFilter extends OncePerRequestFilter {
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserDetailsService auth;

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {

        String jwt = null;

        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("access_token".equals(cookie.getName())) {
                    jwt = cookie.getValue();
                    break;
                }
            }
        }

//        // 2. Fall back to Authorization header
//        if (jwt == null) {
//            String authHeader = request.getHeader("Authorization");
//            if (authHeader != null && authHeader.startsWith("Bearer ")) {
//                jwt = authHeader.substring(7);
//            }
//        }

        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            log.info("JWT Token: " + jwt);
            Claims claims = jwtService.extractClaims(jwt);
            String type = claims.get("type", String.class);
            log.info(type);
            
            if (type != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (type.equals("CUSTOMER")) {
                    log.info("Security Context Authentication: " + SecurityContextHolder.getContext().getAuthentication());
                    CustomerPrincipal principal = new CustomerPrincipal(
                            claims.get("sessionId", String.class),
                            claims.get("seatId", String.class),
                            claims.get("tableId", String.class)
                    );
                    log.info("CustomerPrincipal: " + principal);
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(principal, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                } else if ("WORKER".equals(type)) {
                    String username = claims.getSubject();
                    UserDetails userDetails = auth.loadUserByUsername(username);
                    if (jwtService.validateToken(jwt, userDetails)) {
                        WorkerPrincipal principal = new WorkerPrincipal(
                                claims.get("workerId", String.class),
                                claims.get("role", String.class)
                        );
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(principal, null,
                                        userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("JWT validation failed (e.g. expired): " + e.getMessage());
            // Do NOT throw or return 401 here; let Spring Security decide based on permitAll() rules
            // Clear the SecurityContext just to be safe
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
