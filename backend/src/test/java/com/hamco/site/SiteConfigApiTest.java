package com.hamco.site;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hamco.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** The SPA cannot read server environment variables, so it asks a safe public config route. */
@Import(PostgresTestConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SiteConfigApiTest {
    @Autowired MockMvc mvc;

    @Test
    void registrationIsAdvertisedAsClosedByDefault() throws Exception {
        mvc.perform(get("/api/site/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationEnabled").value(false));
    }
}
