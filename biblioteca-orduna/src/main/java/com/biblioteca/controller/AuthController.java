package com.biblioteca.controller;

import com.biblioteca.dto.LoginDTO;
import com.biblioteca.dto.LoginResponseDTO;
import com.biblioteca.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** RF05 - Inicio de sesión. Endpoint público. Solo traduce HTTP; la lógica está en AuthService. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/login
     * Body: {"email":"admin@biblioteca.pe","password":"admin123"}
     * 200: {"token":"eyJ...","tipo":"Bearer","expira_en_ms":28800000,"id_usuario":1,"nombre":"...","email":"...","rol":"GERENTE"}
     * 401: credenciales incorrectas o cuenta desactivada
     * 403: lector con sanción activa (el documento indica que el acceso queda restringido)
     */
    @PostMapping("/login")
    public LoginResponseDTO login(@Valid @RequestBody LoginDTO datos) {
        return authService.login(datos);
    }
}