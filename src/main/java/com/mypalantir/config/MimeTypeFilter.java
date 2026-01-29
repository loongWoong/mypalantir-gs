package com.mypalantir.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter to set correct MIME types for JavaScript module files
 * Fixes the issue where .js files are served with text/plain instead of application/javascript
 */
@Component
@Order(1)
public class MimeTypeFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String requestURI = httpRequest.getRequestURI();
        
        // Set correct MIME type for JavaScript files
        if (requestURI.endsWith(".js") || requestURI.endsWith(".mjs")) {
            httpResponse.setContentType("application/javascript; charset=utf-8");
        } else if (requestURI.endsWith(".css")) {
            httpResponse.setContentType("text/css; charset=utf-8");
        } else if (requestURI.endsWith(".json")) {
            httpResponse.setContentType("application/json; charset=utf-8");
        } else if (requestURI.endsWith(".svg")) {
            httpResponse.setContentType("image/svg+xml");
        } else if (requestURI.endsWith(".woff") || requestURI.endsWith(".woff2")) {
            httpResponse.setContentType("font/woff2");
        } else if (requestURI.endsWith(".ttf")) {
            httpResponse.setContentType("font/ttf");
        } else if (requestURI.endsWith(".eot")) {
            httpResponse.setContentType("application/vnd.ms-fontobject");
        }
        
        chain.doFilter(request, response);
    }
}




