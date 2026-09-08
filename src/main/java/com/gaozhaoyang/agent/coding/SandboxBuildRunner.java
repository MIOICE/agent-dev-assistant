package com.gaozhaoyang.agent.coding;

import java.nio.file.Path;

public interface SandboxBuildRunner {
    BuildVerification verify(Path workspace, AutonomyBudget budget);
}
