package ru.netology.cloud;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.netology.cloud.auth.User;
import ru.netology.cloud.auth.UserRepository;
import ru.netology.cloud.file.FileInfo;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Полный сценарий на настоящем Postgres. Без Docker тест пропускается. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CloudScenarioIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final byte[] CONTENT = "hello, cloud".getBytes();

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void fullScenario() {
        String token = login("user@example.com", "password");

        // upload
        assertThat(upload(token, "doc.txt").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(upload(token, "doc.txt").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // list
        assertThat(list(token).getBody()).containsExactly(new FileInfo("doc.txt", CONTENT.length));

        // rename (поле "filename", как шлёт фронт)
        ResponseEntity<Void> renamed = rest.exchange("/file?filename=doc.txt", HttpMethod.PUT,
                new HttpEntity<>(Map.of("filename", "renamed.txt"), jsonHeaders(token)), Void.class);
        assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list(token).getBody()).extracting(FileInfo::filename).containsExactly("renamed.txt");

        // download
        ResponseEntity<byte[]> downloaded = rest.exchange("/file?filename=renamed.txt", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), byte[].class);
        assertThat(downloaded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(downloaded.getBody()).isEqualTo(CONTENT);

        // delete
        ResponseEntity<Void> deleted = rest.exchange("/file?filename=renamed.txt", HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list(token).getBody()).isEmpty();

        // logout -> 401
        ResponseEntity<Void> logout = rest.exchange("/logout", HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> afterLogout = rest.exchange("/list?limit=3", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(afterLogout.getBody()).containsKeys("message", "id");
    }

    @Test
    void wrongPasswordReturns400() {
        ResponseEntity<Map> response = rest.postForEntity("/login",
                Map.of("login", "user@example.com", "password", "wrong"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKeys("message", "id");
    }

    @Test
    void filesAreIsolatedBetweenUsers() {
        users.save(new User(null, "alice@example.com", passwordEncoder.encode("secret")));
        String owner = login("user@example.com", "password");
        String alice = login("alice@example.com", "secret");

        upload(owner, "private.txt");

        assertThat(list(alice).getBody()).isEmpty();
        ResponseEntity<Map> download = rest.exchange("/file?filename=private.txt", HttpMethod.GET,
                new HttpEntity<>(authHeaders(alice)), Map.class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // своё имя у Алисы занимать можно — пространства имён разные
        assertThat(upload(alice, "private.txt").getStatusCode()).isEqualTo(HttpStatus.OK);

        rest.exchange("/file?filename=private.txt", HttpMethod.DELETE, new HttpEntity<>(authHeaders(owner)), Void.class);
        rest.exchange("/file?filename=private.txt", HttpMethod.DELETE, new HttpEntity<>(authHeaders(alice)), Void.class);
    }

    private String login(String login, String password) {
        ResponseEntity<Map> response = rest.postForEntity("/login",
                Map.of("login", login, "password", password), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("auth-token");
    }

    private ResponseEntity<Void> upload(String token, String filename) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(CONTENT) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        body.add("hash", "any-hash");
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange("/file?filename=" + filename, HttpMethod.POST, new HttpEntity<>(body, headers), Void.class);
    }

    private ResponseEntity<List<FileInfo>> list(String token) {
        return rest.exchange("/list?limit=10", HttpMethod.GET, new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {
                });
    }

    private static HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("auth-token", "Bearer " + token);
        return headers;
    }

    private static HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
