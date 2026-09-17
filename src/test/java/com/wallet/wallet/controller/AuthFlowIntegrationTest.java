package com.wallet.wallet.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.wallet.wallet.entity.Account;
import com.wallet.wallet.entity.AccountStatus;
import com.wallet.wallet.entity.TransactionType;
import com.wallet.wallet.repository.AccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AccountRepository accountRepository;

    private Long aliceAccountId;
    private Long adminAccountId;
    private Long blockedAccountId;
    private final List<Long> createdIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        aliceAccountId = createAccount("1000.00", 1L, AccountStatus.ACTIVE);
        adminAccountId = createAccount("500.00", 2L, AccountStatus.ACTIVE);
        blockedAccountId = createAccount("50.00", 3L, AccountStatus.BLOCKED);
    }

    @AfterEach
    void tearDown() {
        createdIds.forEach(accountRepository::deleteById);
    }

    private Long createAccount(String balance, Long ownerId, AccountStatus status) {
        Account account = new Account();
        account.setBalance(new BigDecimal(balance));
        account.setOwnerId(ownerId);
        account.setStatus(status);
        Long id = accountRepository.save(account).getId();
        createdIds.add(id);
        return id;
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void login_withValidCredentials_returnsTokenAndRole() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(blankOrNullString())))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
    }

    @Test
    void login_asAdmin_returnsAdminRole() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"));
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withUnknownUser_returns401() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ghost\",\"password\":\"password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedRequest_returns401() throws Exception {
        mockMvc.perform(get("/accounts/{id}", aliceAccountId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithGarbageToken_returns401() throws Exception {
        mockMvc.perform(get("/accounts/{id}", aliceAccountId)
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void owner_canAccessOwnAccount() throws Exception {
        String token = login("alice", "password");

        mockMvc.perform(get("/accounts/{id}", aliceAccountId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(aliceAccountId))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void user_cannotAccessAnotherUsersAccount() throws Exception {
        String token = login("alice", "password");

        mockMvc.perform(get("/accounts/{id}", adminAccountId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_canAccessAnyAccount() throws Exception {
        String token = login("admin", "admin");

        mockMvc.perform(get("/accounts/{id}", aliceAccountId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(aliceAccountId));
    }

    @Test
    void deposit_withToken_updatesBalance() throws Exception {
        String token = login("alice", "password");

        mockMvc.perform(post("/accounts/{id}/deposit", aliceAccountId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1100.0));

        assertEquals(0, new BigDecimal("1100.00")
                .compareTo(accountRepository.findById(aliceAccountId).orElseThrow().getBalance()));
    }

    @Test
    void withdraw_withToken_updatesBalance() throws Exception {
        String token = login("alice", "password");

        mockMvc.perform(post("/accounts/{id}/withdraw", aliceAccountId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 250.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(750.0));
    }

    @Test
    void withdraw_moreThanBalance_returns400AndChangesNothing() throws Exception {
        String token = login("alice", "password");

        mockMvc.perform(post("/accounts/{id}/withdraw", aliceAccountId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 5000.00}"))
                .andExpect(status().isBadRequest());

        assertEquals(0, new BigDecimal("1000.00")
                .compareTo(accountRepository.findById(aliceAccountId).orElseThrow().getBalance()));
    }

    @Test
    void deposit_negativeAmount_returns400ValidationError() throws Exception {
        String token = login("alice", "password");

        mockMvc.perform(post("/accounts/{id}/deposit", aliceAccountId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": -5.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.amount").exists());
    }

    @Test
    void transfer_endToEnd_movesMoneyAndRecordsHistory() throws Exception {
        String token = login("alice", "password");

        mockMvc.perform(post("/transfers")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\": " + aliceAccountId
                                + ", \"destinationAccountId\": " + adminAccountId
                                + ", \"amount\": 200.00}"))
                .andExpect(status().isOk());

        assertEquals(0, new BigDecimal("800.00")
                .compareTo(accountRepository.findById(aliceAccountId).orElseThrow().getBalance()));
        assertEquals(0, new BigDecimal("700.00")
                .compareTo(accountRepository.findById(adminAccountId).orElseThrow().getBalance()));

        MvcResult history = mockMvc.perform(get("/accounts/{id}/transactions", aliceAccountId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode historyBody = objectMapper.readTree(history.getResponse().getContentAsString());
        assertTrue(historyBody.isArray() && !historyBody.isEmpty(), "History should contain rows");
        boolean hasTransfer = false;
        for (JsonNode tx : historyBody) {
            if (TransactionType.TRANSFER.name().equals(tx.get("type").asText())
                    && tx.get("sourceAccountId").asLong() == aliceAccountId
                    && tx.get("destinationAccountId").asLong() == adminAccountId) {
                hasTransfer = true;
                break;
            }
        }
        assertTrue(hasTransfer, "History should contain the TRANSFER row");
    }

    @Test
    void transfer_toBlockedAccount_returns403() throws Exception {
        String token = login("alice", "password");

        mockMvc.perform(post("/transfers")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\": " + aliceAccountId
                                + ", \"destinationAccountId\": " + blockedAccountId
                                + ", \"amount\": 10.00}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void transfer_toSameAccount_returns400() throws Exception {
        String token = login("alice", "password");

        mockMvc.perform(post("/transfers")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\": " + aliceAccountId
                                + ", \"destinationAccountId\": " + aliceAccountId
                                + ", \"amount\": 10.00}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_fromSomeoneElsesAccount_returns403() throws Exception {
        String token = login("alice", "password");

        mockMvc.perform(post("/transfers")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\": " + adminAccountId
                                + ", \"destinationAccountId\": " + aliceAccountId
                                + ", \"amount\": 10.00}"))
                .andExpect(status().isForbidden());

        assertEquals(0, new BigDecimal("500.00")
                .compareTo(accountRepository.findById(adminAccountId).orElseThrow().getBalance()));
    }

    @Test
    void transfer_withSameIdempotencyKeyTwice_returns409AndMovesMoneyOnce() throws Exception {
        String token = login("alice", "password");
        String key = "it-" + UUID.randomUUID();
        String body = "{\"sourceAccountId\": " + aliceAccountId
                + ", \"destinationAccountId\": " + adminAccountId
                + ", \"amount\": 10.00, \"idempotencyKey\": \"" + key + "\"}";

        mockMvc.perform(post("/transfers")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/transfers")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());

        assertEquals(0, new BigDecimal("990.00")
                .compareTo(accountRepository.findById(aliceAccountId).orElseThrow().getBalance()));
        assertEquals(0, new BigDecimal("510.00")
                .compareTo(accountRepository.findById(adminAccountId).orElseThrow().getBalance()));
    }

    @Test
    void healthEndpoint_isPublicAndReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void unknownRoute_returns404Json() throws Exception {
        String token = login("alice", "password");

        mockMvc.perform(get("/does-not-exist")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }
}
