package com.hamco.site;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hamco.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** BrowserRouter needs the server to return the shell on direct GETs, not only Vite. */
@Import(PostgresTestConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SpaRoutingTest {
    @Autowired MockMvc mvc;

    @Test
    void publicAndAccountDeepLinksForwardToTheSameSpaShellWithoutLogin() throws Exception {
        for (String path : new String[] {"/", "/about", "/notes", "/notes/1",
                "/auth/login?verified=1", "/auth/reset-password?token=one-time"}) {
            // Forward, rather than redirect: query parameters and the URL in the
            // browser must remain available for React Router to interpret.
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void adminPageShellIsPublicButTheAdminApiRemainsProtected() throws Exception {
        mvc.perform(get("/admin/notes/edit/1"))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/api/admin/api-keys")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/notes").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/admin/notes")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingAssetsAndApiRoutesAreNeverReplacedByIndexHtml() throws Exception {
        mvc.perform(get("/assets/does-not-exist.js")).andExpect(status().isNotFound());
        mvc.perform(get("/img/does-not-exist.png")).andExpect(status().isNotFound());
        mvc.perform(get("/api/notes/987654321")).andExpect(status().isNotFound());
        mvc.perform(get("/api/unknown")).andExpect(status().isUnauthorized());
    }

    @Test
    void aHeadRequestForTheShellIsNotRejectedAsAnApiWrite() throws Exception {
        mvc.perform(head("/auth/login")).andExpect(status().isOk());
    }
}
