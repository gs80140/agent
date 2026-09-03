package com.example.supportagent.workflow;

/**
 * 表示流程正常执行后得到的“不允许继续”业务结论，而不是系统故障。
 * code 供接口调用方稳定判断，userMessage 可以安全地直接展示给客户。
 */
public class BusinessRuleRejection extends RuntimeException {
    private final String code;
    private final String userMessage;

    public BusinessRuleRejection(String code, String userMessage) {
        super(userMessage);
        this.code = code;
        this.userMessage = userMessage;
    }

    public String code() { return code; }

    public String userMessage() { return userMessage; }
}
