# Spring AI 电商售后智能客服 Agent

一个可运行的 Spring Boot 4 + Spring AI Alibaba Graph 项目。企业 Ontology 声明目标、业务能力、前置事实、产出事实、副作用和审批要求，运行时根据用户目标反向规划并动态编译 Graph：

`Ontology → Capability Planner → Plan Validator → Dynamic StateGraph → HITL → Resume`

退款流程不再作为 Java 固定编排。Planner 从 `ResponseComposed` 目标反向寻找能够产出所需事实的 Capability，得到本次执行计划。Graph 执行到具有 `approvalRequired: true` 的节点前写入 checkpoint 并中断，只有用户批准后才恢复执行写操作。

提示词由 Nacos 3.2 AI Prompt 管理。应用通过业务别名批量订阅；当前 `support-system` 默认订阅 `SupportAgentService_SYSTEM_PROMPT` 的 `production` 标签。Nacos 通过 SDK 通知应用 Prompt 变更，修改后无需重启。

Ontology 默认从 `classpath:ontology/support-agent.yaml` 加载，同时订阅 Nacos Config：

- Data ID：`support-agent-ontology.yaml`
- Group：`DEFAULT_GROUP`

Nacos 中存在配置时覆盖本地 fallback；配置发生变化时先解析和校验，只有合法版本才原子替换，非法更新继续使用最近有效版本。可以直接把 `src/main/resources/ontology/support-agent.yaml` 发布到该 Data ID。

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
  "executionId": "75cf...",
  "status": "WAITING_APPROVAL",
  "content": "已完成订单与售后政策核验……是否批准？",
  "ontologyVersion": "2026.09.01",
  "goal": "AfterSaleCompleted",
  "plannedCapabilities": ["UnderstandRequest", "QueryUserOrders", "..."]
}
```

批准并恢复执行：

```powershell
$decision = @{ approved = $true } | ConvertTo-Json
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agent/executions/$executionId/decision" `
  -ContentType "application/json" `
  -Body $decision
```

内置 Chat UI 会在中断时显示“批准执行 / 拒绝”按钮。

## 查看动态 Graph 与执行轨迹

每次 `/chat` 返回的 `executionId` 都可以查询实际生成的 Graph 和逐节点轨迹：

```http
GET /api/agent/executions/{executionId}
```

响应包含：

- 本次固定的 Ontology 版本和业务目标
- 实际生成的 `ExecutionPlan`
- 可复制到 Mermaid 编辑器的动态图源码
- 当前 execution 状态和所在节点
- 每个节点的 `PENDING/RUNNING/COMPLETED/FAILED` 状态
- 节点耗时、进入前的 state keys、输出增量和异常信息

Chat UI 会在每条 Agent 回复下显示“查看动态 Graph 执行轨迹”，展开即可看到节点链路。服务端也会为每个节点输出带 `executionId` 的开始、完成、耗时和失败日志，可以通过 executionId 串起一次完整调用。

Demo 的追踪数据保存在内存中，重启即清空。生产环境建议接入 Micrometer/OpenTelemetry，并对节点输出中的用户信息进行脱敏后再持久化。

查看健康状态：`GET http://localhost:8080/actuator/health`。

## 测试

```powershell
mvn test
```

测试不调用真实模型，也不需要 API Key。`OntologyGraphRuntimeTest` 覆盖 Ontology 反向规划、Graph 编译、审批前中断、批准恢复、创建工单和发送通知的完整链路。

## 关键设计

- Ontology 是企业知识源，流程由 Capability 的 `requires/produces` 动态推导，而不是读取固定工作流。
- Capability implementation 必须在代码白名单中注册，Ontology 不能执行任意 Bean 或脚本。
- Plan Validator 校验事实依赖、最大节点数、实现是否注册，以及写操作之前是否存在审批能力。
- 每次 execution 固定 Ontology 版本，并独享 CompiledGraph 与 MemorySaver checkpoint。
- 用户拒绝时直接终止 execution，不执行创建工单和通知等副作用。
- Graph 和业务能力解耦；增加新能力只需实现 `CapabilityHandler` 并在 Ontology 中声明语义。

当前数据与 checkpoint 是内存模拟。接入生产时应把业务能力替换为 Repository/HTTP Client，将 `MemorySaver` 替换为 Redis/JDBC Saver，并为写操作增加持久化幂等键、权限校验、审计日志和补偿动作。
