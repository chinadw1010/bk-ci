package com.tencent.devops.process.engine.atom.task.deploy

import com.fasterxml.jackson.databind.ObjectMapper
import com.tencent.devops.common.api.util.FileUtil
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.archive.client.JfrogClient
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.gcloud.DynamicGcloudClient
import com.tencent.devops.common.gcloud.api.pojo.CommonParam
import com.tencent.devops.common.gcloud.api.pojo.dyn.DynNewResourceParam
import com.tencent.devops.common.gcloud.api.pojo.PrePublishParam
import com.tencent.devops.common.gcloud.api.pojo.UploadResParam
import com.tencent.devops.common.pipeline.element.GcloudPufferElement
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.log.utils.LogUtils
import com.tencent.devops.plugin.api.ServiceGcloudConfResource
import com.tencent.devops.process.engine.atom.AtomResponse
import com.tencent.devops.process.engine.atom.IAtomTask
import com.tencent.devops.process.engine.pojo.PipelineBuildTask
import com.tencent.devops.process.util.gcloud.TicketUtil
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.nio.file.Files

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
class GcloudPufferTaskAtom @Autowired constructor(
    private val client: Client,
    private val objectMapper: ObjectMapper,
    private val rabbitTemplate: RabbitTemplate
) : IAtomTask<GcloudPufferElement> {

    @Value("\${gateway.url:#{null}}")
    private val gatewayUrl: String? = null

    override fun execute(task: PipelineBuildTask, param: GcloudPufferElement, runVariables: Map<String, String>): AtomResponse {
        parseParam(param, runVariables)
        LogUtils.addLine(rabbitTemplate, task.buildId, "gcloud element params:\n $param", task.taskId, task.executeCount ?: 1)

        val gcloudUtil = TicketUtil(client)
        val buildId = task.buildId
        val taskId = task.taskId

        with(param) {
            val host = client.get(ServiceGcloudConfResource::class).getByConfigId(configId.toInt()).data
            if (host == null) {
                LogUtils.addRedLine(rabbitTemplate, task.buildId, "unknown configId($configId)", task.taskId, task.executeCount ?: 1)
                return AtomResponse(BuildStatus.FAILED)
            }

            val jfrogClient = JfrogClient(gatewayUrl ?: "", task.projectId, task.pipelineId, buildId)
            val isCustom = fileSource.toUpperCase() == "CUSTOMIZE"
            val destPath = Files.createTempDirectory("gcloud").toAbsolutePath().toString()
            val downloadFileList = jfrogClient.downloadFile(filePath, isCustom, destPath)

            if (downloadFileList.isEmpty()) {
                LogUtils.addRedLine(rabbitTemplate, buildId, "匹配不到待分发的文件: $filePath", taskId, task.executeCount ?: 1)
                return AtomResponse(BuildStatus.FAILED)
            }

            // 获取accessId和accessKey
            val keyPair = gcloudUtil.getAccesIdAndToken(task.projectId, ticketId)
            val accessId = keyPair.first
            val accessKey = keyPair.second

            // 获取文件上传的accessId和accessKey
            var fileAccessId = accessId
            var fileAccessKey = accessKey
            if (!fileTicketId.isNullOrBlank()) {
                val fileKeyPair = gcloudUtil.getAccesIdAndToken(task.projectId, fileTicketId!!)
                fileAccessId = fileKeyPair.first
                fileAccessKey = fileKeyPair.second
            }
            val commonParam = CommonParam(gameId, accessId, accessKey)
            val fileCommonParam = CommonParam(gameId, fileAccessId, fileAccessKey)

            downloadFileList.forEach { downloadFile ->
                try {
                    // step 1
                    LogUtils.addLine(rabbitTemplate, buildId, "开始对文件（${downloadFile.path}）执行Gcloud相关操作，详情请去gcloud官方地址查看：<a target='_blank' href='http://console.gcloud.oa.com/dolphin/channel/$gameId'>查看详情</a>\n", taskId, task.executeCount ?: 1)
                    val gcloudClient = DynamicGcloudClient(objectMapper, host.address, host.fileAddress)
                    LogUtils.addLine(rabbitTemplate, buildId, "开始执行 \"上传动态资源版本\" 操作\n", taskId, task.executeCount ?: 1)
                    val uploadResParam = UploadResParam(productId.toInt(), resourceVersion, FileUtil.getMD5(downloadFile), null, null, https)
                    LogUtils.addLine(rabbitTemplate, buildId, "\"上传动态资源版本\" 操作参数：$uploadResParam\n", taskId, task.executeCount ?: 1)
                    val uploadResult = gcloudClient.uploadDynamicRes(downloadFile, uploadResParam, commonParam)

                    // step 2
                    LogUtils.addLine(rabbitTemplate, buildId, "开始执行 \"查询版本上传 CDN 任务状态\" 操作\n", taskId, task.executeCount ?: 1)
                    val gCloudTaskId = uploadResult.first
                    val versionInfo: String
                    loop@ while (true) {
                        val getTaskResult = gcloudClient.getUploadTask(gCloudTaskId, fileCommonParam)
                        val state = getTaskResult["state"]
                        val message = getTaskResult["message"] ?: ""
                        when (state) {
                            "waiting", "processing" -> {
                                LogUtils.addLine(rabbitTemplate, buildId, "\"等待查询版本上传 CDN 任务状态\" 操作执行完毕: \n", taskId, task.executeCount ?: 1)
                                LogUtils.addLine(rabbitTemplate, buildId, "\"$getTaskResult\n\n", taskId, task.executeCount ?: 1)
                                Thread.sleep(1000 * 6)
                            }
                            "finished" -> {
                                LogUtils.addLine(rabbitTemplate, buildId, "\"查询版本上传 CDN 任务状态\" 操作 成功执行完毕\n", taskId, task.executeCount ?: 1)
                                versionInfo = getTaskResult["versionInfo"]!!
                                break@loop
                            }
                            else -> {
                                LogUtils.addRedLine(rabbitTemplate, buildId, "上传文件失败: $message($state)", taskId, task.executeCount ?: 1)
                                return AtomResponse(BuildStatus.FAILED)
                            }
                        }
                    }

                    // step 3
                    LogUtils.addLine(rabbitTemplate, buildId, "开始执行 \"创建资源\" 操作\n", taskId, task.executeCount ?: 1)
                    val newResParam = DynNewResourceParam(task.starter, productId.toInt(), resourceVersion, resourceName, versionInfo, versionType.toInt(), versionDes, customStr)
                    LogUtils.addLine(rabbitTemplate, buildId, "\"创建资源\" 操作参数：$newResParam\n", taskId, task.executeCount ?: 1)
                    gcloudClient.newResource(newResParam, commonParam)
                    val prePublishParam = PrePublishParam(task.starter, productId.toInt())

                    // step 4
                    LogUtils.addLine(rabbitTemplate, buildId, "开始执行 \"预发布\" 操作\n", taskId, task.executeCount ?: 1)
                    val prePubResult = gcloudClient.prePublish(prePublishParam, commonParam)
                    LogUtils.addLine(rabbitTemplate, buildId, "预发布单个或多个渠道响应结果: $prePubResult\n", taskId, task.executeCount ?: 1)
                } finally {
                    downloadFile.delete()
                }
            }
        }
        return AtomResponse(BuildStatus.SUCCEED)
    }

    private fun parseParam(param: GcloudPufferElement, runVariables: Map<String, String>) {
        param.configId = parseVariable(param.configId, runVariables)
        param.gameId = parseVariable(param.gameId, runVariables)
        param.ticketId = parseVariable(param.ticketId, runVariables)
        param.fileTicketId = parseVariable(param.fileTicketId, runVariables)
        param.productId = parseVariable(param.productId, runVariables)
        param.resourceVersion = parseVariable(param.resourceVersion, runVariables)
        param.resourceName = parseVariable(param.resourceName, runVariables)
        param.https = parseVariable(param.https, runVariables)
        param.filePath = parseVariable(param.filePath, runVariables)
        param.fileSource = parseVariable(param.fileSource, runVariables)
        param.versionType = parseVariable(param.versionType, runVariables)
        param.versionDes = parseVariable(param.versionDes, runVariables)
        param.customStr = parseVariable(param.customStr, runVariables)
    }

    override fun getParamElement(task: PipelineBuildTask): GcloudPufferElement {
        return JsonUtil.mapTo(task.taskParams, GcloudPufferElement::class.java)
    }
}