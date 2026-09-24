package com.gaozhaoyang.agent.casework;

public class CaseNotFoundException extends RuntimeException {
    public CaseNotFoundException(String caseId) {
        super("需求 Case 不存在或当前账号无权访问: " + caseId);
    }
}
