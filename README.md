# Spring AI 电商售后智能客服 Agent

一个可运行的 Spring Boot 4 + Spring AI 2 项目。`ChatClient` 挂载 4 个业务工具，由模型在同一次请求中自主完成：

`查用户与订单 → 查订单政策 → 创建售后工单 → 发送通知 → 汇总答复`

项目使用 Spring AI 2 的 `defaultTools(...)` 与 `@Tool` API。Spring AI 2 中工具循环由 `ChatClient` 自动注册的 `ToolCallingAdvisor` 驱动；旧版 `defaultFunctions(...)` / 按 Bean 名解析 Function 的写法已不再适用。

提示词由 Nacos 3.2 AI Prompt 管理。应用通过业务别名批量订阅；当前 `support-system` 默认订阅 `SupportAgentService_SYSTEM_PROMPT` 的 `production` 标签。Nacos 通过 SDK 通知应用 Prompt 变更，修改后无需重启。

新增 Prompt 时，在 `support-agent.prompt.bindings` 下增加绑定：

```yaml
refund-policy:
  key: RefundPolicy_PROMPT
  label: production
  required: false
```

`version` 与 `label` 只能配置一个；两者都省略时订阅 latest。必需 Prompt 在启动时必须存在，可选 Prompt 可以暂时不可用。

## 环境要求

- JDK 21+
- Maven 3.9+
- 一个支持 tool calling 的 OpenAI 兼容模型与 API Key

## 启动

PowerShell：

```powershell
$env:OPENAI_API_KEY = "your-api-key"
# 可选：兼容服务地址和模型
$env:OPENAI_BASE_URL = "https://api.openai.com"
$env:OPENAI_MODEL = "gpt-4.1-mini"
mvn spring-boot:run
```

如果已创建仅本机使用的 `application-local.yml`，可直接运行：

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

本机 8080 被占用时，可在 `application-local.yml` 中设置 `server.port: 8081`；对应接口为 `http://localhost:8081/api/agent/chat`。

兼容服务的 `base-url` 应填写到 API 根地址，具体是否需要 `/v1` 取决于服务商实现。

## 调用

浏览器直接访问 `http://localhost:8081/` 即可使用内置 Chat UI。

也可以通过 API 调用：

```powershell
$body = @{
  prompt = "我是张三，我之前买的机械键盘按键连击失灵了，我想申请退货退款，帮我处理一下。"
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/agent/chat `
  -ContentType "application/json" `
  -Body $body
```

响应格式：

```json
{
  "content": "您好，张三先生……工单 TCK-xxx 已提交审核，短信通知已发送。"
}
```

查看健康状态：`GET http://localhost:8080/actuator/health`。

## 测试

```powershell
mvn test
```

测试不调用真实模型，也不需要 API Key：工具层测试覆盖完整成功链路和异常输入，Controller 测试覆盖 JSON 响应及参数校验。

## 关键设计

- 工具用 `@Tool` / `@ToolParam` 暴露，Schema 和描述直接提供给模型。
- 系统提示词强制“先查再改”，并禁止虚构工具结果。
- 创建工单时校验用户与订单归属，通知工具校验工单号。
- `temperature=0.1` 降低工具选择的不稳定性。
- 工具异常默认抛给应用，由统一异常处理返回 RFC 9457 Problem Details。

当前数据是内存模拟。接入生产时应把工具内部替换为 Repository/HTTP Client，并为 `create_support_ticket` 增加幂等键、权限校验、审计日志和人工审批门禁。
