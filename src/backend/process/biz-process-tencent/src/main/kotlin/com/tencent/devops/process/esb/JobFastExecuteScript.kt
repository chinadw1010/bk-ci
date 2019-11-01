package com.tencent.devops.process.esb

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.devops.common.api.exception.OperationException
import com.tencent.devops.common.api.exception.ParamBlankException
import com.tencent.devops.common.api.util.OkhttpUtils
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.log.utils.LogUtils
import okhttp3.Request
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Base64

@Component
class JobFastExecuteScript @Autowired constructor(private val rabbitTemplate: RabbitTemplate) {
    @Value("\${esb.url}")
    private val esbUrl = "http://open.oa.com/component/compapi/job/"

    @Value("\${esb.code}")
    protected val appCode = ""

    @Value("\${esb.secret}")
    protected val appSecret = ""

    fun fastExecuteScript(
        buildId: String,
        operator: String,
        appId: Int,
        content: String,
        scriptParam: String,
        ipList: List<SourceIp>,
        elementId: String,
        executeCount: Int,
        type: Int = 1,
        account: String = "root"
    ): Long {
        checkParam(operator, appId, content, account)

        val taskInstanceId = sendTaskRequest(buildId, operator, appId, content, scriptParam, type, ipList, account, elementId, executeCount)
        if (taskInstanceId <= 0) {
            // 失败处理
            logger.error("Job start execute script failed.")
            throw OperationException("Job执行脚本失败")
        }
        return taskInstanceId
    }

    protected fun checkParam(operator: String, appId: Int, content: String, account: String) {
        if (operator.isBlank()) {
            throw ParamBlankException("Invalid operator")
        }
        if (appId <= 0) {
            throw ParamBlankException("Invalid appId")
        }
        if (content.isBlank()) {
            throw ParamBlankException("Invalid content, content is empty")
        }
        try {
            Base64.getDecoder().decode(content)
        } catch (e: IllegalArgumentException) {
            throw ParamBlankException("Invalid content, it's not in valid Base64 scheme")
        }
        if (account.isBlank()) {
            throw ParamBlankException("Invalid account")
        }
    }

    fun checkStatus(
        startTime: Long,
        timeoutSeconds: Int,
        targetAppId: Int,
        taskInstanceId: Long,
        buildId: String,
        taskId: String,
        executeCount: Int,
        operator: String
    ): BuildStatus {

        if (System.currentTimeMillis() - startTime > timeoutSeconds * 1000) {
            logger.warn("job timeout. timeout seconds:$timeoutSeconds")
            LogUtils.addRedLine(
                rabbitTemplate,
                buildId,
                "Job timeout:$timeoutSeconds seconds",
                taskId,
                executeCount
            )
            return BuildStatus.EXEC_TIMEOUT
        }

        val taskResult = getTaskResult(targetAppId, taskInstanceId, operator)

        return if (taskResult.isFinish) {
            if (taskResult.success) {
                logger.info("[$buildId]|SUCCEED|taskInstanceId=$taskId|${taskResult.msg}")
                LogUtils.addLine(rabbitTemplate, buildId, "Job success! jobId:$taskInstanceId", taskId, executeCount)
                BuildStatus.SUCCEED
            } else {
                logger.info("[$buildId]|FAIL|taskInstanceId=$taskId|${taskResult.msg}")
                LogUtils.addLine(rabbitTemplate, buildId, "Job fail! jobId:$taskInstanceId", taskId, executeCount)
                BuildStatus.FAILED
            }
        } else {
            logger.info("[$buildId]|Waiting for job! jobId:$taskInstanceId")
            BuildStatus.LOOP_WAITING
        }
    }

    fun getTaskResult(appId: Int, taskInstanceId: Long, operator: String): TaskResult {
        val url = esbUrl + "get_task_result"
        val requestData = emptyMap<String, Any>().toMutableMap()
        requestData["app_code"] = appCode
        requestData["app_secret"] = appSecret
        requestData["app_id"] = appId
        requestData["task_instance_id"] = taskInstanceId
        requestData["operator"] = operator
        return doGetTaskResult(url, requestData, taskInstanceId)
    }

    protected fun doGetTaskResult(url: String, requestData: Map<String, Any>, taskInstanceId: Long): TaskResult {
        val json = ObjectMapper().writeValueAsString(requestData)
        try {
            val httpReq = Request.Builder()
                .url(url)
                .post(RequestBody.create(OkhttpUtils.jsonMediaType, json))
                .build()
            OkhttpUtils.doHttp(httpReq).use { resp ->
                val responseStr = resp.body()!!.string()
                val response: Map<String, Any> = jacksonObjectMapper().readValue(responseStr)
                if (response["code"] == "00") {
                    val responseData = response["data"] as Map<String, *>
                    val isFinished = responseData["isFinished"] as Boolean
                    logger.info("request success. taskInstanceId: $taskInstanceId")
                    return if (isFinished) {
                        val taskInstanceObj = responseData["taskInstance"] as Map<String, *>
                        val status = taskInstanceObj["status"] as Int
                        if (status == SUCCESS) {
                            logger.info("Job execute task finished and success")
                            TaskResult(isFinish = true, success = true, msg = "Success")
                        } else {
                            logger.info("Job execute task finished but failed")
                            TaskResult(isFinish = true, success = false, msg = "Job failed")
                        }
                    } else {
                        TaskResult(isFinish = false, success = false, msg = "Job Running")
                    }
                } else {
                    val msg = response["message"] as String
                    logger.error("request failed, msg: $msg")
                    return TaskResult(isFinish = true, success = false, msg = msg)
                }
            }
        } catch (e: Exception) {
            logger.error("error occur", e)
            throw RuntimeException("error occur while execute job task.")
        }
    }

    private fun sendTaskRequest(
        buildId: String,
        operator: String,
        appId: Int,
        content: String,
        scriptParam: String,
        type: Int,
        ipList: List<SourceIp>,
        account: String,
        elementId: String,
        executeCount: Int
    ): Long {
        val requestData = emptyMap<String, Any>().toMutableMap()
        requestData["app_code"] = appCode
        requestData["app_secret"] = appSecret
        requestData["app_id"] = appId
        requestData["content"] = content
        requestData["script_param"] = scriptParam
        requestData["type"] = type
        requestData["account"] = account
        requestData["ip_list"] = ipList
        requestData["operator"] = operator

        val url = esbUrl + "fast_execute_script"
        return doSendTaskRequest(url, requestData, buildId, elementId, executeCount)
    }

    protected fun doSendTaskRequest(url: String, requestData: MutableMap<String, Any>, buildId: String, elementId: String, executeCount: Int): Long {
        val json = ObjectMapper().writeValueAsString(requestData)
        logger.info("send execute script task request: $json")
        try {

            val httpReq = Request.Builder()
                .url(url)
                .post(RequestBody.create(OkhttpUtils.jsonMediaType, json))
                .build()
            OkhttpUtils.doHttp(httpReq).use { resp ->
                val responseStr = resp.body()!!.string()
                val response: Map<String, Any> = jacksonObjectMapper().readValue(responseStr)
                logger.info("send execute script task response: $response")

                return if (response["code"] == "00") {
                    val responseData = response["data"] as Map<String, *>
                    val taskInstanceId = responseData["taskInstanceId"].toString().toLong()
                    logger.info("request success. taskInstanceId: $taskInstanceId")
                    LogUtils.addLine(
                        rabbitTemplate,
                        buildId,
                        "start execute job task success: taskInstanceId:  $taskInstanceId",
                        elementId,
                        executeCount
                    )
                    taskInstanceId
                } else {
                    val msg = response["message"] as String
                    logger.error("request failed, msg: $msg")
                    LogUtils.addLine(
                        rabbitTemplate,
                        buildId,
                        "start execute job task failed: $msg",
                        elementId,
                        executeCount
                    )
                    -1
                }
            }
        } catch (e: Exception) {
            logger.error("error occur", e)
            LogUtils.addLine(rabbitTemplate, buildId, "error occur while execute job task: ${e.message}", elementId, executeCount)
            throw RuntimeException("error occur while execute job task.")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(JobFastExecuteScript::class.java)
        private const val SUCCESS = 3
    }

    data class TaskResult(val isFinish: Boolean, val success: Boolean, val msg: String)
}