package teknofest.signa.producer.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import teknofest.signa.producer.model.dto.auth.AuthResponse;
import teknofest.signa.producer.model.dto.auth.ForgotPasswordRequest;
import teknofest.signa.producer.model.dto.auth.LoginRequest;
import teknofest.signa.producer.model.dto.auth.RegisterRequest;
import teknofest.signa.producer.model.dto.auth.ResetPasswordRequest;
import teknofest.signa.producer.service.AuthService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest loginRequestDto) {
        return authService.login(loginRequestDto);
    }

    @PostMapping("/admin-register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest registerRequest, @RequestParam String token) {
        return authService.register(registerRequest, token);
    }

    @PostMapping("/reset-password")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        authService.resetPassword(resetPasswordRequest);
    }

    @PostMapping("/forgot-password")
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest forgotPasswordRequestDto) {
        authService.forgotPassword(forgotPasswordRequestDto);
    }
}
