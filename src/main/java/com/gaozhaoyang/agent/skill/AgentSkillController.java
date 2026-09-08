package com.gaozhaoyang.agent.skill;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/skills")
public class AgentSkillController {

    private final AgentSkillCatalog catalog;

    public AgentSkillController(AgentSkillCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<AgentSkillSummary> list() {
        return catalog.list();
    }
}
