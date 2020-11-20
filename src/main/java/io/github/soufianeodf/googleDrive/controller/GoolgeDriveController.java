package io.github.soufianeodf.googleDrive.controller;

import io.github.soufianeodf.googleDrive.service.GoogleDriveService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.GeneralSecurityException;

@RestController
public class GoolgeDriveController {

    private final GoogleDriveService googleDriveService;

    public GoolgeDriveController(GoogleDriveService googleDriveService) {
        this.googleDriveService = googleDriveService;
    }

    @GetMapping("/")
    public void main() throws IOException, GeneralSecurityException {
        googleDriveService.main();
    }
}
