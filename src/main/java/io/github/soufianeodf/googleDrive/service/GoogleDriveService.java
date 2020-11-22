package io.github.soufianeodf.googleDrive.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
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

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

@Service
public class GoogleDriveService {

    private final Drive service;
    private final NetHttpTransport HTTP_TRANSPORT;

    private static final String APPLICATION_NAME = "Google Drive API Java Quickstart";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    public GoogleDriveService() throws GeneralSecurityException, IOException {
        // Build a new authorized API client service.
        HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = GoogleDriveService.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    public void main(String fileId) {
        // get file
        File file = null;
        try {
            file = service.files().get(fileId).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }

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
}