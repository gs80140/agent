package com.example.supportagent.workflow.capability;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.supportagent.tools.CustomerSupportTools;
import com.example.supportagent.knowledge.KnowledgeRetriever;
import com.example.supportagent.workflow.CapabilityHandler;
import com.example.supportagent.workflow.CapabilitySchema;
import com.example.supportagent.workflow.BusinessRuleRejection;
import org.springframework.stereotype.Component;

import java.util.Map;

/** READ 能力：查询订单签收信息并把售后政策判断转化为可供规划使用的事实。 */
@Component
public class EvaluatePolicyHandler implements CapabilityHandler {
    private final CustomerSupportTools tools;
    private final KnowledgeRetriever knowledgeRetriever;
    public EvaluatePolicyHandler(CustomerSupportTools tools, KnowledgeRetriever knowledgeRetriever) {
        this.tools = tools;
        this.knowledgeRetriever = knowledgeRetriever;
    }
    @Override public CapabilitySchema schema() {
        return new CapabilitySchema("evaluate-after-sale-policy", "查询订单详情并判断售后政策", "evaluatePolicy",
                java.util.List.of("orderId", "evidenceCompleted"),
                java.util.List.of("refundAllowed", "signedDate", "logisticsStatus", "policy", "policyEvidenceIds"),
                CapabilitySchema.EffectLevel.READ, false);
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        throw new IllegalStateException("政策评估能力必须由包含知识概念元数据的企业 Workflow 调用");
    }

    @Override
    public Map<String, Object> execute(OverAllState state, com.example.supportagent.workflow.ExecutionPlan.PlanNode node) {
        // 知识检索发生在真正需要政策证据的节点，而不是用于生成或改变 Workflow。
        String query = "商品=" + state.value("productName", "") + "；问题=" + state.value("reason", "")
                + "；售后类型=" + state.value("serviceType", "");
        if (node.knowledgeConceptIds().isEmpty()) {
            throw new IllegalArgumentException("政策评估节点必须声明需要检索的 Ontology 概念");
        }
        var evidence = knowledgeRetriever.retrieve(query, node.knowledgeConceptIds());
        var detail = tools.getOrderDetail(state.value("orderId", ""));
        // 这是预期业务结论而非系统故障。运行时会转换成 NOT_ELIGIBLE 终态，不再进入审批和写操作。
        if (!detail.canRefund()) {
            throw new BusinessRuleRejection("AFTER_SALE_NOT_ELIGIBLE",
                    "很抱歉，这笔订单当前不支持退货或换货。原因是：" + detail.policy()
                            + "。您仍可以联系人工客服申请保修检测。");
        }
        return Map.of("refundAllowed", true, "signedDate", detail.signedDate().toString(),
                "logisticsStatus", detail.logisticsStatus(), "policy", detail.policy(),
                "policyEvidenceIds", evidence.documents().stream().map(d -> d.id()).toList());
    }
}
