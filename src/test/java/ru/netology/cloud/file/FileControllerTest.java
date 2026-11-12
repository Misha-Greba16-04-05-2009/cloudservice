package ru.netology.cloud.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.netology.cloud.auth.AuthService;
import ru.netology.cloud.error.BadRequestException;
import ru.netology.cloud.error.UnauthorizedException;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
class FileControllerTest {

    private static final String TOKEN = "Bearer token-1";
    private static final Long USER_ID = 7L;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FileService fileService;
    @MockitoBean
    private AuthService authService;

    @BeforeEach
    void authenticate() {
        when(authService.authenticate(TOKEN)).thenReturn(USER_ID);
    }

    @Test
    void uploadPassesFileToService() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

        mvc.perform(multipart("/file").file(file).param("filename", "a.txt").param("hash", "h")
                        .header("auth-token", TOKEN))
                .andExpect(status().isOk());

        verify(fileService).upload(eq(USER_ID), eq("a.txt"), any(), eq("h"));
    }

    @Test
    void uploadWithoutFilePartReturns400() throws Exception {
        mvc.perform(multipart("/file").param("filename", "a.txt").header("auth-token", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Error input data"));
    }

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        when(authService.authenticate(isNull())).thenThrow(new UnauthorizedException("Unauthorized error"));

        mvc.perform(get("/list").param("limit", "3"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized error"))
                .andExpect(jsonPath("$.id").isNumber());
        verifyNoInteractions(fileService);
    }

    @Test
    void listReturnsFilenameAndSize() throws Exception {
        when(fileService.list(USER_ID, 3)).thenReturn(List.of(new FileInfo("a.txt", 5)));

        mvc.perform(get("/list").param("limit", "3").header("auth-token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("a.txt"))
                .andExpect(jsonPath("$[0].size").value(5));
    }

    @Test
    void downloadReturnsFileBytes() throws Exception {
        when(fileService.download(USER_ID, "a.txt"))
                .thenReturn(new StoredFile(USER_ID, "a.txt", "h", "hello".getBytes(), Instant.now()));

        mvc.perform(get("/file").param("filename", "a.txt").header("auth-token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .andExpect(content().bytes("hello".getBytes()));
    }

    @Test
    void renameAcceptsNameFromSpec() throws Exception {
        mvc.perform(put("/file").param("filename", "a.txt").header("auth-token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"b.txt\"}"))
                .andExpect(status().isOk());

        verify(fileService).rename(USER_ID, "a.txt", "b.txt");
    }

    @Test
    void renameAcceptsFilenameFromFrontend() throws Exception {
        mvc.perform(put("/file").param("filename", "a.txt").header("auth-token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"filename\":\"c.txt\"}"))
                .andExpect(status().isOk());

        verify(fileService).rename(USER_ID, "a.txt", "c.txt");
    }

    @Test
    void deleteMissingFileReturns400() throws Exception {
        doThrow(new BadRequestException("File not found")).when(fileService).delete(USER_ID, "x.txt");

        mvc.perform(delete("/file").param("filename", "x.txt").header("auth-token", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File not found"));
    }

    @Test
    void unexpectedErrorReturns500() throws Exception {
        doThrow(new IllegalStateException("db down")).when(fileService).delete(USER_ID, "a.txt");

        mvc.perform(delete("/file").param("filename", "a.txt").header("auth-token", TOKEN))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }
}
