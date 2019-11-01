package com.tencent.devops.process.engine.atom.task

import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.element.SendRTXNotifyElement
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.service.utils.HomeHostUtil
import com.tencent.devops.common.wechatwork.WechatWorkService
import com.tencent.devops.common.wechatwork.model.enums.ReceiverType
import com.tencent.devops.common.wechatwork.model.sendmessage.Receiver
import com.tencent.devops.common.wechatwork.model.sendmessage.richtext.RichtextContent
import com.tencent.devops.common.wechatwork.model.sendmessage.richtext.RichtextMessage
import com.tencent.devops.common.wechatwork.model.sendmessage.richtext.RichtextText
import com.tencent.devops.common.wechatwork.model.sendmessage.richtext.RichtextTextText
import com.tencent.devops.common.wechatwork.model.sendmessage.richtext.RichtextView
import com.tencent.devops.common.wechatwork.model.sendmessage.richtext.RichtextViewLink
import com.tencent.devops.log.utils.LogUtils
import com.tencent.devops.notify.api.service.ServiceNotifyResource
import com.tencent.devops.notify.pojo.RtxNotifyMessage
import com.tencent.devops.process.engine.atom.AtomResponse
import com.tencent.devops.process.engine.atom.IAtomTask
import com.tencent.devops.process.pojo.AtomErrorCode
import com.tencent.devops.process.engine.pojo.PipelineBuildTask
import com.tencent.devops.process.pojo.ErrorType
import com.tencent.devops.process.util.ServiceHomeUrlUtils
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
class RtxTaskAtom @Autowired constructor(
    private val client: Client,
    private val wechatWorkService: WechatWorkService,
    private val rabbitTemplate: RabbitTemplate
)
    : IAtomTask<SendRTXNotifyElement> {
    override fun getParamElement(task: PipelineBuildTask): SendRTXNotifyElement {
        return JsonUtil.mapTo(task.taskParams, SendRTXNotifyElement::class.java)
    }

    private val logger = LoggerFactory.getLogger(RtxTaskAtom::class.java)

    override fun execute(task: PipelineBuildTask, param: SendRTXNotifyElement, runVariables: Map<String, String>): AtomResponse {
        with(task) {

            logger.info("Enter RtxTaskDelegate run...")
            if (param.receivers.isEmpty()) {
                LogUtils.addRedLine(rabbitTemplate, buildId, "Message Receivers is empty(接收人为空)", taskId, task.executeCount ?: 1)
                AtomResponse(
                    buildStatus = BuildStatus.FAILED,
                    errorType = ErrorType.USER,
                    errorCode = AtomErrorCode.USER_INPUT_INVAILD,
                    errorMsg = "Message Receivers is empty(接收人为空)"
                )
            }
            if (param.body.isBlank()) {
                LogUtils.addRedLine(rabbitTemplate, buildId, "Message Body is empty(消息内容为空)", taskId, task.executeCount ?: 1)
                AtomResponse(
                    buildStatus = BuildStatus.FAILED,
                    errorType = ErrorType.USER,
                    errorCode = AtomErrorCode.USER_INPUT_INVAILD,
                    errorMsg = "Message Body is empty(消息内容为空)"
                )
            }
            if (param.title.isEmpty()) {
                LogUtils.addRedLine(rabbitTemplate, buildId, "Message Title is empty(标题为空)", taskId, task.executeCount ?: 1)
                AtomResponse(
                    buildStatus = BuildStatus.FAILED,
                    errorType = ErrorType.USER,
                    errorCode = AtomErrorCode.USER_INPUT_INVAILD,
                    errorMsg = "Message Title is empty(标题为空)"
                )
            }

            val sendDetailFlag = param.detailFlag != null && param.detailFlag!!

            val detailUrl = detailUrl(projectId, pipelineId, buildId)

            val detailOuterUrl = detailOuterUrl(projectId, pipelineId, buildId)

            val bodyStrOrigin = parseVariable(param.body, runVariables)
            // 企业微信通知是否加上详情
            val bodyStr = if (sendDetailFlag) {
                "$bodyStrOrigin\n\n电脑查看详情：$detailUrl\n手机查看详情：$detailOuterUrl"
            } else {
                bodyStrOrigin
            }
            val titleStr = parseVariable(param.title, runVariables)

            val message = RtxNotifyMessage().apply {
                body = bodyStr
                title = titleStr
            }

            val receiversStr = parseVariable(param.receivers.joinToString(","), runVariables)
            LogUtils.addLine(rabbitTemplate, buildId, "send enterprise wechat message(发送企业微信消息):\n${message.body}\nto\n$receiversStr", taskId, task.executeCount ?: 1)

            message.addAllReceivers(getReceivers(receiversStr))
            client.get(ServiceNotifyResource::class).sendRtxNotify(message)

            // 发送企业微信群消息
            val sendWechatGroupFlag = param.wechatGroupFlag != null && param.wechatGroupFlag!!
            if (sendWechatGroupFlag) {
                val wechatGroups = mutableSetOf<String>()
                val wechatGroupsStr = parseVariable(param.wechatGroup, runVariables)
                wechatGroups.addAll(wechatGroupsStr.split(",|;".toRegex()))
                wechatGroups.forEach {
                    val receiver = Receiver(ReceiverType.group, it)
                    val richtextContentList = mutableListOf<RichtextContent>()
                    richtextContentList.add(
                        RichtextText(
                            RichtextTextText(
                            "$titleStr\n\n$bodyStrOrigin\n"
                    )
                        )
                    )
                    // 企业微信群是否加上查看详情
                    if (sendDetailFlag) {
                        richtextContentList.add(
                            RichtextView(
                                RichtextViewLink(
                                "查看详情",
                                detailUrl,
                                1
                        )
                            )
                        )
                    }
                    val richtextMessage = RichtextMessage(receiver, richtextContentList)
                    wechatWorkService.sendRichText(richtextMessage)
                }
            }
        }
        return AtomResponse(BuildStatus.SUCCEED)
    }

    private fun getReceivers(receiverStr: String): Set<String> {
        val set = mutableSetOf<String>()
        receiverStr.split(",").forEach {
            set.add(it.trim())
        }
        return set
    }

    private fun detailUrl(projectId: String, pipelineId: String, processInstanceId: String) =
            "${ServiceHomeUrlUtils.server()}/console/pipeline/$projectId/$pipelineId/detail/$processInstanceId"

    private fun detailOuterUrl(projectId: String, pipelineId: String, processInstanceId: String) =
            "${HomeHostUtil.outerServerHost()}/app/download/devops_app_forward.html?flag=buildArchive&" +
                    "projectId=$projectId&" +
                    "pipelineId=$pipelineId&" +
                    "buildId=$processInstanceId"
}