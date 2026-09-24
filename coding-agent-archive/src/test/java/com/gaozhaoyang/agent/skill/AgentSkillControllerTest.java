package com.gaozhaoyang.agent.skill;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentSkillControllerTest {

    @Test
    void shouldExposeCheapSkillMetadataWithoutFullInstructions() throws Exception {
        AgentSkillCatalog catalog = new AgentSkillCatalog(
                "classpath*:agent-skills/*/SKILL.md");
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AgentSkillController(catalog))
                .build();

        mockMvc.perform(get("/api/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].name").value("export-reliability"))
                .andExpect(jsonPath("$[0].description").isNotEmpty())
                .andExpect(jsonPath("$[0].trusted").value(true))
                .andExpect(jsonPath("$[0].sha256").isNotEmpty())
                .andExpect(jsonPath("$[0].instructions").doesNotExist());
    }
}
