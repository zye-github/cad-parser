package com.cadparser.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsUp() throws Exception {
        mockMvc.perform(get("/api/cad/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void parseSampleDxf() throws Exception {
        byte[] dxf = getClass().getResourceAsStream("/fixtures/sample.dxf").readAllBytes();
        MockMultipartFile file = new MockMultipartFile("file", "sample.dxf", "application/dxf", dxf);

        mockMvc.perform(multipart("/api/cad/parse").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("AC1027"))
                .andExpect(jsonPath("$.entityCount").value(5))
                .andExpect(jsonPath("$.entities[0].type").value("LINE"));
    }
}
