package com.arguspay.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountFlowTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    Environment env;

    private final HttpClient http = HttpClient.newHttpClient();
    private String tokenA;
    private String tokenB;

    @BeforeAll
    void registerUsers() throws Exception {
        tokenA = register("a-" + UUID.randomUUID() + "@test.com");
        tokenB = register("b-" + UUID.randomUUID() + "@test.com");
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        HttpResponse<String> response = call("POST", "/api/accounts", null, null);
        assertEquals(401, response.statusCode());
    }

    @Test
    void depositoActualizaElSaldo() throws Exception {
        String id = newAccount(tokenA);
        deposit(tokenA, id, "50.00", UUID.randomUUID().toString());
        assertEquals(0, balance(tokenA, id).compareTo(new BigDecimal("50")));
    }

    @Test
    void idempotencyKeyRepetidaDevuelve409YNoDuplicaElSaldo() throws Exception {
        String id = newAccount(tokenA);
        String key = UUID.randomUUID().toString();

        assertEquals(200, deposit(tokenA, id, "50.00", key).statusCode());
        assertEquals(409, deposit(tokenA, id, "50.00", key).statusCode());
        assertEquals(0, balance(tokenA, id).compareTo(new BigDecimal("50")));
    }

    @Test
    void retiroQueExcedeElSaldoDevuelve422() throws Exception {
        String id = newAccount(tokenA);
        deposit(tokenA, id, "10.00", UUID.randomUUID().toString());

        HttpResponse<String> response = call("POST", "/api/accounts/" + id + "/withdraw", tokenA,
                "{\"amount\": 99999.00, \"idempotencyKey\": \"" + UUID.randomUUID() + "\"}");

        assertEquals(422, response.statusCode());
        assertEquals(0, balance(tokenA, id).compareTo(new BigDecimal("10")));
    }

    @Test
    void transferenciaMueveElDineroYQuedaEnElHistorial() throws Exception {
        String from = newAccount(tokenA);
        String to = newAccount(tokenB);
        deposit(tokenA, from, "100.00", UUID.randomUUID().toString());

        HttpResponse<String> response = call("POST", "/api/accounts/" + from + "/transfer", tokenA,
                "{\"toAccountId\": \"" + to + "\", \"amount\": 20.00, \"idempotencyKey\": \""
                        + UUID.randomUUID() + "\"}");

        assertEquals(200, response.statusCode());
        assertEquals(0, balance(tokenA, from).compareTo(new BigDecimal("80")));
        assertEquals(0, balance(tokenB, to).compareTo(new BigDecimal("20")));

        HttpResponse<String> history = call("GET", "/api/accounts/" + from + "/transactions", tokenA, null);
        assertTrue(history.body().contains("TRANSFER_OUT"));
    }

    @Test
    void otroUsuarioNoPuedeVerLaCuentaAjena() throws Exception {
        String id = newAccount(tokenA);
        HttpResponse<String> response = call("GET", "/api/accounts/" + id + "/balance", tokenB, null);
        assertEquals(403, response.statusCode());
    }

    @Test
    void dosRetirosSimultaneosNuncaDejanSaldoNegativo() throws Exception {
        String id = newAccount(tokenA);
        deposit(tokenA, id, "100.00", UUID.randomUUID().toString());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            String key = UUID.randomUUID().toString();
            results.add(pool.submit(() -> {
                start.await();
                return call("POST", "/api/accounts/" + id + "/withdraw", tokenA,
                        "{\"amount\": 100.00, \"idempotencyKey\": \"" + key + "\"}").statusCode();
            }));
        }

        start.countDown();
        int exitosos = 0;
        for (Future<Integer> result : results) {
            if (result.get() == 200) {
                exitosos++;
            }
        }
        pool.shutdown();

        assertEquals(1, exitosos);
        assertEquals(0, balance(tokenA, id).compareTo(BigDecimal.ZERO));
    }

    private String baseUrl() {
        return "http://localhost:" + env.getProperty("local.server.port");
    }

    private HttpResponse<String> call(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String register(String email) throws Exception {
        HttpResponse<String> response = call("POST", "/api/auth/register", null,
                "{\"email\": \"" + email + "\", \"password\": \"clave12345\"}");
        assertEquals(201, response.statusCode());
        return extract(response.body(), "token");
    }

    private String newAccount(String token) throws Exception {
        HttpResponse<String> response = call("POST", "/api/accounts", token, null);
        assertEquals(200, response.statusCode());
        return extract(response.body(), "id");
    }

    private HttpResponse<String> deposit(String token, String id, String amount, String key) throws Exception {
        return call("POST", "/api/accounts/" + id + "/deposit", token,
                "{\"amount\": " + amount + ", \"idempotencyKey\": \"" + key + "\"}");
    }

    private BigDecimal balance(String token, String id) throws Exception {
        HttpResponse<String> response = call("GET", "/api/accounts/" + id + "/balance", token, null);
        Matcher matcher = Pattern.compile("\"balance\":(-?[0-9.]+)").matcher(response.body());
        assertTrue(matcher.find());
        return new BigDecimal(matcher.group(1));
    }

    private String extract(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]+)\"").matcher(json);
        assertTrue(matcher.find());
        return matcher.group(1);
    }
}