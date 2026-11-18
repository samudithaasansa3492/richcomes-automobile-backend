package com.richcomes.garage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class GarageApplicationTests {
    @Autowired WebApplicationContext context;

    @Test
    void contextLoads() {}

    @Test
    void recoveryPageAndAssetsAreAvailableWithoutAuthentication() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/account-setup.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string(containsString("account-setup.js")));
        mvc.perform(get("/account-setup.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("window.history.replaceState")));
        mvc.perform(get("/account-setup.css")).andExpect(status().isOk());
        mvc.perform(get("/other-secret-file.txt")).andExpect(status().isForbidden());
    }
}
