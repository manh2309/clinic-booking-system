package com.xxxx.clinicbookingsystem;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTests {
    @Autowired MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "PATIENT")
    void patientCannotReadAccounts() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    void specialtiesArePublic() throws Exception {
        mockMvc.perform(get("/api/v1/specialties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000));
    }
}
