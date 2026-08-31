package com.scantoorder.scantoorder.config;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    @Autowired
    private UserDetailsService auth;
    @Autowired
    private JwtFilter  jwtFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider(auth);
        daoAuthenticationProvider.setPasswordEncoder(passwordEncoder());
        return daoAuthenticationProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authentication) {
        return authentication.getAuthenticationManager();
    }
    @Bean
    public SecurityFilterChain securityConfiguration(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .cors(cors -> {})
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentTypeOptions(contentTypeOptions -> contentTypeOptions.disable())
                        .frameOptions(frame -> frame.deny())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/tables").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/tables/*/status").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/tables/*/seatMap").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/seats/claim").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/workers/login").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/payments/webhook").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
                    res.getWriter().write("{\"status\":\"ERROR\",\"message\":\"Unauthorized\"}");
                }))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return httpSecurity.build();
    }

    @Bean
    public ModelMapper modelMapper(){
        return new ModelMapper();
    }
}
