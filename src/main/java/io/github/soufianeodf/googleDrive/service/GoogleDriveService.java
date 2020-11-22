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

    public void main(String fileId) throws IOException, GeneralSecurityException {
        // Print the names and IDs for up to 10 files.
        FileList result = service.files().list()
                .setPageSize(10)
                .setFields("nextPageToken, files(id, name)")
                .execute();
        List<File> files = result.getFiles();
        if (files == null || files.isEmpty()) {
            System.out.println("No files found.");
        } else {
            System.out.println("Files:");
            int i =1;
            for (File file : files) {
                System.out.printf("%s %s (%s)\n", i++, file.getName(), file.getId());
            }
        }

        // create a file
//        File fileMetadata = new File();
//        fileMetadata.setName("new document");
//        fileMetadata.setMimeType("application/vnd.google-apps.document");
//
//        File file = service.files().create(fileMetadata).execute();
//        System.out.println("File ID: " + file.getId());

        // get file
        File file = service.files().get(fileId).execute();

        // copy the file
        File fileCopy = service.files().copy(file.getId(), new File().setName("copy")).execute();
        System.out.println("File ID: " + fileCopy.getId());

        // ***************************************************
        final String uri = "https://spring-boot-google-docs.herokuapp.com/" + fileCopy.getId();

        RestTemplate restTemplate = new RestTemplate();
        String theResult = restTemplate.getForObject(uri, String.class);

        System.out.println(result);
        // ***************************************************

        // download and export document to pdf
        OutputStream outputStream = new FileOutputStream("src/main/resources/" + file.getName());
        service.files().export(fileCopy.getId(), "application/pdf")
                .executeMediaAndDownloadTo(outputStream);
        outputStream.close();

        // upload pdf file to google drive
        File fileMetadata = new File();
        fileMetadata.setName(file.getName());
        java.io.File filePath = new java.io.File("src/main/resources/" + file.getName());
        FileContent mediaContent = new FileContent("application/pdf", filePath);
        File fileToUpload = service.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute();
        System.out.println("File ID: " + fileToUpload.getId());

        // remove the copied file
        service.files().delete(fileCopy.getId()).execute();

        // delete pdf file from resources folder
        if (filePath.delete()) {
            System.out.println("Deleted the file: " + filePath.getName());
        } else {
            System.out.println("Failed to delete the file.");
        }
    }
}