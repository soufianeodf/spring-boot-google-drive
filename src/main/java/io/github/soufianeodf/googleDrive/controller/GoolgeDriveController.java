package io.github.soufianeodf.googleDrive.controller;

import io.github.soufianeodf.googleDrive.service.GoogleDriveService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.GeneralSecurityException;

@RestController
public class GoolgeDriveController {

    private final GoogleDriveService googleDriveService;

    public GoolgeDriveController(GoogleDriveService googleDriveService) {
        this.googleDriveService = googleDriveService;
    }

    @GetMapping("/")
    public String showHomePage() throws Exception {
        return googleDriveService.showHomePage();
    }

    @GetMapping(value = { "/googlesignin" })
    public void doGoogleSignIn(HttpServletResponse response) throws Exception {
        googleDriveService.doGoogleSignIn(response);
    }

    @GetMapping(value = { "/oauth" })
    public String saveAuthorizationCode(HttpServletRequest request) throws Exception {
        return googleDriveService.saveAuthorizationCode(request);
    }

    @GetMapping("/main")
    public void main() throws IOException, GeneralSecurityException {
        googleDriveService.main("1ZZWExvIj9t0eHzCr-z3uAXUEHwFt9DnefId2DabecDU");
    }
}
