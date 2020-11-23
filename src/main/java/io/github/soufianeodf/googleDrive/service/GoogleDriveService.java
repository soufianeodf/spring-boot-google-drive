package io.github.soufianeodf.googleDrive.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

@Service
public class GoogleDriveService {

    private static final String CALLBACK_URI = "http://localhost:8081/oauth";
    private final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();;

    private static final String APPLICATION_NAME = "Google Drive API Java Quickstart";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final String USER_IDENTIFIER_KEY = "MY_DUMMY_USER";
    private static GoogleAuthorizationCodeFlow flow;

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    public GoogleDriveService() {
    }

    @PostConstruct
    private void init() throws IOException {
        // Load client secrets.
        InputStream in = GoogleDriveService.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
    }

    public void main(String fileId) {
        // Build a new authorized API client service.
        Credential cred = null;
        try {
            cred = flow.loadCredential(USER_IDENTIFIER_KEY);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Drive service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred)
                .setApplicationName(APPLICATION_NAME).build();

        // get file
        File file = null;
        try {
            file = service.files().get(fileId).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("the file id is: " + file.getId());

        // copy the file
        File fileCopy = null;
        try {
            File copiedFile = new File();
            copiedFile.setName("copy");
            fileCopy = service.files().copy(file.getId(), copiedFile).execute();
            System.out.println("File ID: " + fileCopy.getId());
        } catch (IOException e) {
            e.printStackTrace();
        }

        // ***************************************************
        final String uri = "https://spring-boot-google-docs.herokuapp.com/" + fileCopy.getId();

        RestTemplate restTemplate = new RestTemplate();
        String theResult = restTemplate.getForObject(uri, String.class);
        // ***************************************************

        // download and export document to pdf
        OutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream("src/main/resources/" + file.getName());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            service.files().export(fileCopy.getId(), "application/pdf")
                    .executeMediaAndDownloadTo(outputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // upload pdf file to google drive
        File fileMetadata = new File();
        fileMetadata.setName(file.getName());
        java.io.File filePath = new java.io.File("src/main/resources/" + file.getName());
        FileContent mediaContent = new FileContent("application/pdf", filePath);
        File fileToUpload = null;
        try {
            fileToUpload = service.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute();
            System.out.println("File ID: " + fileToUpload.getId());
        } catch (IOException e) {
            e.printStackTrace();
        }

        // remove the copied file
        try {
            service.files().delete(fileCopy.getId()).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // delete pdf file from resources folder
        if (filePath.delete()) {
            System.out.println("Deleted the file: " + filePath.getName());
        } else {
            System.out.println("Failed to delete the file.");
        }
    }

    public String showHomePage() {
        boolean isUserAuthenticated = false;

        Credential credential = null;
        try {
            credential = flow.loadCredential(USER_IDENTIFIER_KEY);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (credential != null) {
            boolean tokenValid = false;
            try {
                tokenValid = credential.refreshToken();
                if (tokenValid) {
                    isUserAuthenticated = true;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return isUserAuthenticated ? "you are logged in" : "sorry, you need to log in first. Go to /googlesignin";
    }

    public void doGoogleSignIn(HttpServletResponse response) {
        GoogleAuthorizationCodeRequestUrl url = flow.newAuthorizationUrl();
        String redirectURL = url.setRedirectUri(CALLBACK_URI).setAccessType("offline").build();
        try {
            response.sendRedirect(redirectURL);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String saveAuthorizationCode(HttpServletRequest request) {
        String code = request.getParameter("code");
        if (code != null) {
            try {
                saveToken(code);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return "dashboard.html";
        }

        return "index.html";
    }

    private void saveToken(String code) throws Exception {
        GoogleTokenResponse response = flow.newTokenRequest(code).setRedirectUri(CALLBACK_URI).execute();
        flow.createAndStoreCredential(response, USER_IDENTIFIER_KEY);
    }
}