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
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("java-code-generation"))
                .andExpect(jsonPath("$[0].description").isNotEmpty())
                .andExpect(jsonPath("$[0].instructions").doesNotExist());
    }
}
