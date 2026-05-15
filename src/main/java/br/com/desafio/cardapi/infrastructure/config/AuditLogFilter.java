package br.com.desafio.cardapi.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AuditLogFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
            
        long startTime = System.currentTimeMillis();
        
        logger.info("Incoming Request: {} {}", request.getMethod(), request.getRequestURI());
        
        filterChain.doFilter(request, response);
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("Outgoing Response: Status {} | Duration: {}ms", response.getStatus(), duration);
    }
}
