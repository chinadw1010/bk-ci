package com.tencent.devops.process.engine.atom.task

import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.event.JobWrapper
import com.tencent.devops.common.pipeline.element.SendWechatNotifyElement
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.service.utils.HomeHostUtil
import com.tencent.devops.log.utils.LogUtils
import com.tencent.devops.notify.api.service.ServiceNotifyResource
import com.tencent.devops.notify.pojo.WechatNotifyMessage
import com.tencent.devops.process.engine.atom.AtomResponse
import com.tencent.devops.process.engine.atom.IAtomTask
import com.tencent.devops.process.engine.atom.defaultFailAtomResponse
import com.tencent.devops.process.pojo.AtomErrorCode
import com.tencent.devops.process.engine.pojo.PipelineBuildTask
import com.tencent.devops.process.pojo.ErrorType
import com.tencent.devops.process.utils.PIPELINE_ID
import com.tencent.devops.process.utils.PROJECT_NAME
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
class WechatTaskAtom @Autowired constructor(
    private val client: Client,
    private val rabbitTemplate: RabbitTemplate
)
    : IAtomTask<SendWechatNotifyElement> {
    override fun getParamElement(task: PipelineBuildTask): SendWechatNotifyElement {
        return JsonUtil.mapTo(task.taskParams, SendWechatNotifyElement::class.java)
    }

    override fun execute(task: PipelineBuildTask, param: SendWechatNotifyElement, runVariables: Map<String, String>): AtomResponse {
        val taskId = task.taskId
        val buildId = task.buildId
        if (param.receivers.isEmpty()) {
            LogUtils.addRedLine(rabbitTemplate, buildId, "通知接收者不合法:[${param.receivers}]", taskId, task.executeCount ?: 1)
            return AtomResponse(
                buildStatus = BuildStatus.FAILED,
                errorType = ErrorType.USER,
                errorCode = AtomErrorCode.USER_INPUT_INVAILD,
                errorMsg = "通知接收者不合法:[${param.receivers}]"
            )
        }
        if (param.body.isBlank()) {
            LogUtils.addRedLine(rabbitTemplate, buildId, "企业微信通知内容:[${param.body}]", taskId, task.executeCount ?: 1)
            return AtomResponse(
                buildStatus = BuildStatus.FAILED,
                errorType = ErrorType.USER,
                errorCode = AtomErrorCode.USER_INPUT_INVAILD,
                errorMsg = "企业微信通知内容:[${param.body}]"
            )
        }
        val sendDetailFlag = param.detailFlag ?: false

        var bodyStr = parseVariable(param.body, runVariables)

        // 启动短信的查看详情
        if (sendDetailFlag) {
            val outerUrl = "${HomeHostUtil.outerServerHost()}/app/download/devops_app_forward.html?flag=buildArchive&" +
                    "projectId=${runVariables[PROJECT_NAME]}&" +
                    "pipelineId=${runVariables[PIPELINE_ID]}&" +
                    "buildId=$buildId"
            val innerUrl = "${HomeHostUtil.innerServerHost()}/console/pipeline/${runVariables[PROJECT_NAME]}/${runVariables[PIPELINE_ID]}/detail/$buildId"
            bodyStr = "$bodyStr\n\n 手机查看详情：$outerUrl \n 电脑查看详情：$innerUrl"
        }
        val message = WechatNotifyMessage().apply {
            body = bodyStr
        }
        val receiversStr = parseVariable(param.receivers.joinToString(","), runVariables)
        LogUtils.addLine(rabbitTemplate, buildId, "发送企业微信内容: (${message.body}) 到 $receiversStr", taskId, task.executeCount ?: 1)

        message.addAllReceivers(receiversStr.split(",").toSet())

        val success = (object : JobWrapper {
            override fun doIt(): Boolean {
                val resp = client.get(ServiceNotifyResource::class).sendWechatNotify(message)
                if (resp.isOk()) {
                    if (resp.data!!) {
                        LogUtils.addLine(rabbitTemplate, buildId, "发送企业微信内容: (${message.body}) 到 [$receiversStr]成功", taskId, task.executeCount ?: 1)
                        return true
                    }
                }
                LogUtils.addRedLine(rabbitTemplate, buildId, "发送企业微信内容: (${message.body}) 到 [$receiversStr]失败: ${resp.message}", taskId, task.executeCount ?: 1)
                return false
            }
        }).tryDoIt()

        return if (success) AtomResponse(BuildStatus.SUCCEED) else defaultFailAtomResponse
    }
}