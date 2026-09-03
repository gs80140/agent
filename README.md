# Spring AI 电商售后智能客服 Agent

一个可运行的 Spring Boot 4 + Spring AI Alibaba Graph 项目。领域 Ontology 严格采用
`O=(C,R,F,A,I)` 五元组；企业流程由独立 WorkflowRepository 管理，Capability 从 Spring Bean
自动扫描，知识正文保存在独立知识库中：

`用户语言 → LLM Ontology 意图映射 → 已发布 Workflow → 公理/Schema 校验 → StateGraph → 节点知识检索 → HITL`

意图识别优先匹配 Ontology Concept 上由企业维护的 `recognitionTerms`，常见表达无需调用模型；未命中或候选冲突时
才由 LLM 在“已被 BusinessGoal 引用的叶子意图”中消歧，并缓存同一 Ontology 版本下的相同问题。
LLM 不生成流程节点，也不决定能力顺序，只把自然语言映射到 Ontology 中已有的可执行业务意图。
`WorkflowResolver` 根据意图唯一选择企业已发布流程并检查本体流程偏序公理；Graph Compiler 会再次核对
流程 ID、版本和完整 Capability 序列。企业 Workflow 还可以在任意节点声明结构化人工交互；Graph 会在节点前写入
checkpoint，等待客户补充资料或审批，然后从同一个 execution 恢复，而不是重新规划流程。

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

Chat UI 默认调用下面两个 POST SSE 接口。本体意图识别、企业流程解析、Schema 校验、Graph 编译、
每个节点的开始/完成以及 HITL 等待状态都会通过 `progress` 事件实时显示：

- `POST /api/agent/chat/stream`
- `POST /api/agent/executions/{executionId}/interactions/{interactionId}`（JSON）
- `POST /api/agent/executions/{executionId}/interactions/{interactionId}/stream`
- `POST /api/agent/executions/{executionId}/decision/stream`
- 事件名：`progress`、`result`、`error`

浏览器使用 `fetch + ReadableStream` 消费 SSE，因为标准 `EventSource` 不能发送 POST JSON。
原有 JSON 接口继续保留，方便已有调用方兼容迁移。

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
  "status": "WAITING_INPUT",
  "content": "请补充故障描述和图片或视频地址。",
  "ontologyVersion": "2026.09.04",
  "goal": "AfterSaleCompleted",
  "plannedCapabilities": ["理解客户诉求", "查询客户身份和最近订单", "..."],
  "interaction": {
    "interactionId": "...",
    "nodeId": "collect-evidence",
    "type": "FORM_INPUT",
    "title": "补充商品故障资料",
    "required": ["problemDescription", "evidenceUrls"],
    "properties": {
      "problemDescription": { "type": "string", "label": "故障详细描述" },
      "evidenceUrls": { "type": "array", "label": "图片或视频地址" }
    }
  }
}
```

提交资料并从 checkpoint 恢复：

```powershell
$interaction = @{
  values = @{
    problemDescription = "按键一次会连续输入三次"
    evidenceUrls = @("https://example.test/evidence.jpg")
  }
} | ConvertTo-Json -Depth 4
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agent/executions/$executionId/interactions/$interactionId" `
  -ContentType "application/json" `
  -Body $interaction
```

资料由后端按 Workflow 中发布的 Schema 做确定性校验；缺少必填字段、类型错误、超长内容和未知字段都会被拒绝。
本 Demo 用 URL 表示举证附件，生产系统应先上传到受控对象存储，再将文件 ID 写入交互数据。

批准并恢复执行：

```powershell
$decision = @{ approved = $true } | ConvertTo-Json
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/agent/executions/$executionId/decision" `
  -ContentType "application/json" `
  -Body $decision
```

内置 Chat UI 会根据 `interaction` 自动渲染补充资料表单；资料提交成功并执行到审批节点后，再显示
“批准执行 / 拒绝”按钮。一次 execution 可以经历多次中断与恢复。

政策核验不通过属于正常业务结果，接口返回 `status: NOT_ELIGIBLE` 和面向客户的说明，不返回 Graph、异常类或
堆栈措辞。此时流程立即终止，审批、创建工单和通知节点保持 `PENDING`，便于业务指标与系统故障指标分开统计。

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

自动化测试不调用真实模型，也不需要 API Key。`OntologyGraphRuntimeTest` 从五元组 Ontology 和
企业已发布 Workflow 解析执行计划，覆盖知识节点、Schema 校验、Graph 编译、资料补充中断、非法资料拒绝、
审批中断和两次 checkpoint 恢复。
应用真实运行时，Ontology Intent Resolver 和政策节点的语义知识检索会调用 ChatModel。

## 关键设计

- Ontology 只保存 Concept、Relation、Function、Axiom、Instance，不保存知识正文、能力目录或执行拓扑。
- `standard-return-refund` 是 `BusinessProcess` 本体实例对应的独立已发布 Workflow，企业决定完整节点顺序。
- LLM 只通过 structured output 返回已有 Ontology intentId，不能生成 Workflow 或 Capability Plan。
- 父概念 `business-intent` 不进入模型候选；退款、换货等高频表达由 Concept 的自然语言识别词走本地快速路径。
- Capability ID、名称、implementation、输入输出和副作用全部来自 Spring 自动扫描的 `CapabilityHandler`。
- Workflow Resolver 校验流程实例绑定、发布状态和本体偏序公理；Plan Validator 再校验事实依赖与副作用。
- Graph Compiler 只接受与 WorkflowRepository 中已发布版本完整一致的节点序列。
- 企业知识位于 `resources/knowledge`；只有政策评估节点执行时才检索相关证据，知识不能改变流程拓扑。
- 每次 execution 固定 Ontology 版本，并独享 CompiledGraph 与 MemorySaver checkpoint。
- 用户拒绝时直接终止 execution，不执行创建工单和通知等副作用。
- `FORM_INPUT/FILE_UPLOAD/CLARIFICATION/SELECTION/APPROVAL` 是通用人工交互类型；本流程使用
  `FORM_INPUT` 收集质量问题证据，再使用 `APPROVAL` 守护创建工单这一副作用。
- Graph 和业务能力解耦；增加新能力只需新增 `CapabilityHandler`，并由企业 Workflow 显式引用其稳定 ID。

当前数据与 checkpoint 是内存模拟。接入生产时应把业务能力替换为 Repository/HTTP Client，将 `MemorySaver` 替换为 Redis/JDBC Saver，并为写操作增加持久化幂等键、权限校验、审计日志和补偿动作。
