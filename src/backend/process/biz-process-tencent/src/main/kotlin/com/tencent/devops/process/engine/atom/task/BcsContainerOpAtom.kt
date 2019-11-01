@file:Suppress("UNCHECKED_CAST")

package com.tencent.devops.process.engine.atom.task

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.api.util.OkhttpUtils
import com.tencent.devops.common.auth.api.BSAuthTokenApi
import com.tencent.devops.common.auth.code.PipelineAuthServiceCode
import com.tencent.devops.process.pojo.ErrorType
import com.tencent.devops.log.utils.LogUtils
import com.tencent.devops.common.pipeline.element.BcsContainerOpElement
import com.tencent.devops.process.engine.atom.AtomResponse
import com.tencent.devops.process.engine.atom.IAtomTask
import com.tencent.devops.process.pojo.AtomErrorCode.USER_INPUT_INVAILD
import com.tencent.devops.process.pojo.AtomErrorCode.USER_TASK_OPERATE_FAIL
import com.tencent.devops.process.engine.pojo.PipelineBuildTask
import com.tencent.devops.process.pojo.third.enum.BcsCategory
import com.tencent.devops.process.pojo.third.enum.BcsOperation
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
@Scope(SCOPE_PROTOTYPE)
class BcsContainerOpAtom @Autowired constructor(
    private val rabbitTemplate: RabbitTemplate,
    private val bkAuthTokenApi: BSAuthTokenApi,
    private val serviceCode: PipelineAuthServiceCode
) : IAtomTask<BcsContainerOpElement> {

    override fun getParamElement(task: PipelineBuildTask): BcsContainerOpElement {
        return JsonUtil.mapTo(task.taskParams, BcsContainerOpElement::class.java)
    }

    @Value("\${project.url}")
    private val projectUrl = "http://api.apigw-biz.o.oa.com/api/paas-cc/test/projects/"

    @Value("\${bcsApp.url}")
    private val bcsAppUrl = "http://api.apigw-biz.o.oa.com/api/paas-cd/test/apps/"

    override fun execute(
        task: PipelineBuildTask,
        param: BcsContainerOpElement,
        runVariables: Map<String, String>
    ): AtomResponse {

        if (param.opType.isBlank()) {
            LogUtils.addRedLine(rabbitTemplate, task.buildId, "opType is not init", task.taskId, task.executeCount ?: 1)
            return AtomResponse(
                buildStatus = BuildStatus.FAILED,
                errorType = ErrorType.USER,
                errorCode = USER_INPUT_INVAILD,
                errorMsg = "opType is not init"
            )
        }

        val projectCode = task.projectId
        val projectId = getProjectOriginId(projectCode)
        val opType = parseVariable(param.opType, runVariables)
        val bcsOperation = BcsOperation.parse(opType)

        val result = if (bcsOperation == BcsOperation.CREATE) {
            createInstance(task, projectId, param, runVariables)
        } else {
            doBcsOperation(param, bcsOperation, projectId, task, runVariables)
        }

        return result
    }

    private fun doBcsOperation(
        param: BcsContainerOpElement,
        opType: BcsOperation,
        projectId: String,
        task: PipelineBuildTask,
        runVariables: Map<String, String>
    ): AtomResponse {

        // 公共的参数校验
        if (param.category.isNullOrBlank()) {
            logger.error("[${task.buildId}]|TASK_BcsContainerOpAtom| category is not init")
            LogUtils.addRedLine(rabbitTemplate, task.buildId, "category is not init", task.taskId, task.executeCount ?: 1)
//            throw BuildTaskException(ERROR_BUILD_TASK_BCS_PARAM_CATEGORY, "category is not init")
            return AtomResponse(
                buildStatus = BuildStatus.FAILED,
                errorType = ErrorType.USER,
                errorCode = USER_INPUT_INVAILD,
                errorMsg = "category is not init"
            )
        }

        if (param.bcsAppInstId.isNullOrBlank()) {
            logger.error("[${task.buildId}]|TASK_BcsContainerOpAtom| bcsAppInstId is not init")
            LogUtils.addRedLine(rabbitTemplate, task.buildId, "bcsAppInstId is not init", task.taskId, task.executeCount ?: 1)
//            throw BuildTaskException(ERROR_BUILD_TASK_BCS_PARAM_BCSAPPINSTID, "bcsAppInstId is not init")
            return AtomResponse(
                buildStatus = BuildStatus.FAILED,
                errorType = ErrorType.USER,
                errorCode = USER_INPUT_INVAILD,
                errorMsg = "bcsAppInstId is not init"
            )
        }
        val instVarList = param.instVar ?: listOf()
        val appIdStr = parseVariable(param.ccAppId, runVariables)
        val category = BcsCategory.parse(parseVariable(param.category, runVariables))
        val bcsAppInstId = parseVariable(param.bcsAppInstId, runVariables)
        val timeout = param.timeout

        val varMap = mutableMapOf<String, String>()
        instVarList.forEach {
            varMap[it.key] = parseVariable(it.value, runVariables)
        }
        val instVar = mutableMapOf<String, Map<String, String>>()
        instVar[bcsAppInstId] = varMap

        lateinit var result: Pair<Int, String>
        when (opType) {
            BcsOperation.RECREATE -> {
                LogUtils.addLine(
                    rabbitTemplate,
                    task.buildId,
                    "BCS opType is reCreate, instanceId : $bcsAppInstId",
                    task.taskId,
                    task.executeCount ?: 1
                )
                result = reCreateInstance(category, appIdStr, projectId, bcsAppInstId)
            }
            BcsOperation.SCALE -> {
                if (param.bcsInstNum == null) {
                    logger.error("[${task.buildId}]|TASK_BcsContainerOpAtom| bcsInstNum is not init")
                    LogUtils.addRedLine(rabbitTemplate, task.buildId, "bcsInstNum is not init", task.taskId, task.executeCount ?: 1)
//                    throw BuildTaskException(ERROR_BUILD_TASK_BCS_PARAM_BCSINSTNUM, "bcsInstNum is not init")
                    return AtomResponse(
                        buildStatus = BuildStatus.FAILED,
                        errorType = ErrorType.USER,
                        errorCode = USER_INPUT_INVAILD,
                        errorMsg = "bcsInstNum is not init"
                    )
                }
                val instNum = param.bcsInstNum!!
                LogUtils.addLine(
                    rabbitTemplate,
                    task.buildId,
                    "BCS opType is scale, instanceId : $bcsAppInstId and instanceNum: $instNum",
                    task.taskId,
                    task.executeCount ?: 1
                )
                result = scaleInstance(category, appIdStr, projectId, bcsAppInstId, instNum)
            }
            BcsOperation.ROLLINGUPDATE -> {
                if (param.instVersionId == null) {
                    logger.error("[${task.buildId}]|TASK_BcsContainerOpAtom| instVersionId is not init")
                    LogUtils.addRedLine(rabbitTemplate, task.buildId, "instVersionId is not init", task.taskId, task.executeCount ?: 1)
//                    throw BuildTaskException(ERROR_BUILD_TASK_BCS_PARAM_INSTVERSIONID, "instVersionId is not init")
                    return AtomResponse(
                        buildStatus = BuildStatus.FAILED,
                        errorType = ErrorType.USER,
                        errorCode = USER_INPUT_INVAILD,
                        errorMsg = "instVersionId is not init"
                    )
                }
                val versionId = parseVariable(param.instVersionId, runVariables).toInt()

                result = when (category) {
                    BcsCategory.APPLICATION -> {
                        // FIXME versionid
                        LogUtils.addLine(
                            rabbitTemplate,
                            task.buildId,
                            "BCS opType is application update, instanceId : $bcsAppInstId",
                            task.taskId,
                            task.executeCount ?: 1
                        )
                        applicationUpdate(appIdStr, projectId, versionId, bcsAppInstId, instVar)
                    }
                    else -> {
                        if (param.bcsInstNum == null) {
                            logger.error("[${task.buildId}]|TASK_BcsContainerOpAtom| bcsInstNum is not init")
                            LogUtils.addRedLine(rabbitTemplate, task.buildId, "bcsInstNum is not init", task.taskId, task.executeCount ?: 1)
//                            throw BuildTaskException(ERROR_BUILD_TASK_BCS_PARAM_BCSINSTNUM, "bcsInstNum is not init")
                            return AtomResponse(
                                buildStatus = BuildStatus.FAILED,
                                errorType = ErrorType.USER,
                                errorCode = USER_INPUT_INVAILD,
                                errorMsg = "bcsInstNum is not init"
                            )
                        }
                        val instNum = param.bcsInstNum!!

                        LogUtils.addLine(
                            rabbitTemplate,
                            task.buildId,
                            "BCS opType is batch update, instanceId : $bcsAppInstId",
                            task.taskId,
                            task.executeCount ?: 1
                        )
                        updateInstance(category, appIdStr, projectId, versionId, instNum, bcsAppInstId, instVar)
                    }
                }
            }
            BcsOperation.DELETE -> {
                LogUtils.addLine(rabbitTemplate, task.buildId, "BCS opType is delete", task.taskId, task.executeCount ?: 1)
                result = deleteInstance(category, appIdStr, projectId, bcsAppInstId)
            }
            else -> {
            }
        }
        if (result.first != 0) {
            logger.error("BCS operate failed msg: ${result.second}")
            LogUtils.addRedLine(rabbitTemplate, task.buildId, "BCS operate result:${result.second}", task.taskId, task.executeCount ?: 1)
//            throw BuildTaskException(ERROR_BUILD_TASK_BCS_OPERATE_FAIL, "BCS operate failed, msg: ${result.second}")
            return AtomResponse(
                buildStatus = BuildStatus.FAILED,
                errorType = ErrorType.USER,
                errorCode = USER_TASK_OPERATE_FAIL,
                errorMsg = "BCS operate failed"
            )
        }

        Thread.sleep(5 * 1000)

        if (opType != BcsOperation.DELETE) {
            val appResult = waitForRunning(appIdStr, projectId, bcsAppInstId, timeout.toLong())
            if (!appResult.first) {
                LogUtils.addRedLine(rabbitTemplate, task.buildId, "BCS operation failed", task.taskId, task.executeCount ?: 1)
                LogUtils.addRedLine(rabbitTemplate, task.buildId, appResult.second, task.taskId, task.executeCount ?: 1)
//                throw BuildTaskException(ERROR_BUILD_TASK_BCS_OPERATE_FAIL, "BCS operate failed, msg: ${appResult.second}")
                return AtomResponse(
                    buildStatus = BuildStatus.FAILED,
                    errorType = ErrorType.USER,
                    errorCode = USER_TASK_OPERATE_FAIL,
                    errorMsg = "BCS operate failed"
                )
            }
        }

        LogUtils.addLine(rabbitTemplate, task.buildId, "BCS operation success!", task.taskId, task.executeCount ?: 1)
        return AtomResponse(BuildStatus.SUCCEED)
    }

    private fun waitForRunning(
        appidStr: String,
        projectId: String,
        instanceId: String,
        timeout: Long
    ): Pair<Boolean, String> {
        logger.info("waiting for bcsApp running, timeout setting: ${timeout}min")
        val startTime = System.currentTimeMillis()
        loop@ while (true) {
            if (System.currentTimeMillis() - startTime > timeout * 60 * 1000) {
                logger.error("waiting for bcsApp running timeout")
                return Pair(false, "Waiting for bcs app running timeout")
            }

            val (isFinish, success, msg) = getInstanceStatus(appidStr, projectId, instanceId)
            return when {
                !isFinish -> {
                    Thread.sleep(5 * 1000)
                    continue@loop
                }
                !success -> {
                    logger.error("Waiting for bcs app running failed, msg: $msg")
                    Pair(false, "Waiting for bcs app running failed, msg: $msg")
                }
                else -> Pair(true, "Success!")
            }
        }
    }

    private fun getInstanceStatus(appidStr: String, projectId: String, instanceId: String): TaskResult {
        val token = bkAuthTokenApi.getAccessToken(serviceCode)
        val url =
            bcsAppUrl + "cc_app_ids/$appidStr/projects/$projectId/instances/$instanceId/status/?access_token=$token"
        logger.info("Get instance status, request url: $url")
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        OkhttpUtils.doHttp(request).use { response ->
            val data = response.body()!!.string()
            logger.info("Get instance status, response: $data")
            if (!response.isSuccessful) {
                throw RuntimeException(data)
            }

            val responseData: Map<String, Any> = jacksonObjectMapper().readValue(data)
            val code = responseData["code"] as Int
            if (0 != code) {
                val message = responseData["message"] as String
                return TaskResult(true, false, message)
            }
            val statusMap = responseData["data"] as Map<String, Any>
            if (null == statusMap["status"]) {
                return TaskResult(false, false, "")
            }
            val status = statusMap["status"] as String
            return when {
                "running".equals(status, false) -> TaskResult(true, true, "running")
                "unnormal".equals(status, false) -> TaskResult(true, false, "unnormal")
                else -> TaskResult(false, false, "")
            }
        }
    }

    private fun createInstance(
        task: PipelineBuildTask,
        projectId: String,
        param: BcsContainerOpElement,
        runVariables: Map<String, String>
    ): AtomResponse {

        val timeout = param.timeout
        if (param.clusterId.isNullOrBlank()) {
            LogUtils.addRedLine(rabbitTemplate, task.buildId, "clusterId is not init", task.taskId, task.executeCount ?: 1)
            return AtomResponse(
                buildStatus = BuildStatus.FAILED,
                errorType = ErrorType.USER,
                errorCode = USER_INPUT_INVAILD,
                errorMsg = "clusterId is not init"
            )
            // throw BuildTaskException(ERROR_BUILD_TASK_BCS_PARAM_NAMESPACE_VAR, "namespaceVar is not init")
        }
        if (param.namespaceVar == null) {
            logger.error("namespaceVar is not init of build(${task.buildId})")
            LogUtils.addRedLine(rabbitTemplate, task.buildId, "namespaceVar is not init", task.taskId, task.executeCount ?: 1)
            return AtomResponse(
                buildStatus = BuildStatus.FAILED,
                errorType = ErrorType.USER,
                errorCode = USER_INPUT_INVAILD,
                errorMsg = "namespaceVar is not init"
            )
            // throw BuildTaskException(ERROR_BUILD_TASK_BCS_PARAM_NAMESPACE_VAR, "namespaceVar is not init")
        }
        val variableInfo = param.namespaceVar!!
        if (param.versionId.isNullOrBlank()) {
            logger.error("versionId is not init of build(${task.buildId})")
            LogUtils.addRedLine(rabbitTemplate, task.buildId, "versionId is not init", task.taskId, task.executeCount ?: 1)
            return AtomResponse(
                buildStatus = BuildStatus.FAILED,
                errorType = ErrorType.USER,
                errorCode = USER_INPUT_INVAILD,
                errorMsg = "versionId is not init"
            )
        }
        if (param.showVersionId.isNullOrBlank()) {
            logger.error("showVersionId is not init of build(${task.buildId})")
            LogUtils.addRedLine(rabbitTemplate, task.buildId, "showVersionId is not init", task.taskId, task.executeCount ?: 1)
            return AtomResponse(
                buildStatus = BuildStatus.FAILED,
                errorType = ErrorType.USER,
                errorCode = USER_INPUT_INVAILD,
                errorMsg = "showVersionId is not init"
            )
        }
        if (param.instanceEntity.isNullOrBlank()) {
            logger.error("instanceEntity is not init of build(${task.buildId})")
            LogUtils.addRedLine(rabbitTemplate, task.buildId, "instanceEntity is not init", task.taskId, task.executeCount ?: 1)
            return AtomResponse(
                buildStatus = BuildStatus.FAILED,
                errorType = ErrorType.USER,
                errorCode = USER_INPUT_INVAILD,
                errorMsg = "instanceEntity is not init"
            )
        }

        val appIdStr = parseVariable(param.ccAppId, runVariables)
        val clusterId = parseVariable(param.clusterId, runVariables)
        val versionId = parseVariable(param.versionId, runVariables)
        val showVersionId = parseVariable(param.showVersionId, runVariables)
        val instanceEntity = parseVariable(param.instanceEntity, runVariables)
        val showVersionName = parseVariable(param.showVersionName, runVariables)
        val instanceEntityObj = JsonUtil.toMap(instanceEntity)

        LogUtils.addLine(rabbitTemplate, task.buildId, "BCS opType is create, instanceEntity : $instanceEntity", task.taskId, task.executeCount ?: 1)

        val token = bkAuthTokenApi.getAccessToken(serviceCode)
        val url = bcsAppUrl + "cc_app_ids/$appIdStr/projects/$projectId/instances/?access_token=$token"
        logger.info("Create instance, request url: $url")

        val clusterNsInfo = mutableMapOf<String, MutableMap<String, String>>()
        variableInfo.forEach { it ->
            val namespace = parseVariable(it.namespace, runVariables)
            val map: MutableMap<String, String> = clusterNsInfo.computeIfAbsent(namespace) { mutableMapOf() }
            val key = parseVariable(it.varKey, runVariables)
            val value = parseVariable(it.varValue, runVariables)
            map[key] = value
        }

        val requestData = mapOf(
            "cluster_ns_info" to mapOf(clusterId to clusterNsInfo),
            "version_id" to versionId,
            "show_version_id" to showVersionId,
            "show_version_name" to showVersionName,
            "instance_entity" to instanceEntityObj
        )

        val requestBody = ObjectMapper().writeValueAsString(requestData)
        logger.info("Create instance, request body: $requestBody")
        val request = Request.Builder()
            .url(url)
            .post(RequestBody.create(JSON, requestBody))
            .build()
        OkhttpUtils.doHttp(request).use { response ->
            val data = response.body()!!.string()
            logger.info("Create instance, response: $data")
            if (!response.isSuccessful) {
                logger.error("Create instance failed, msg: $data")
                LogUtils.addRedLine(rabbitTemplate, task.buildId, "Create instance failed, msg: $data", task.taskId, task.executeCount ?: 1)
//                throw RuntimeException(data)
                return AtomResponse(
                    buildStatus = BuildStatus.FAILED,
                    errorType = ErrorType.USER,
                    errorCode = USER_TASK_OPERATE_FAIL,
                    errorMsg = "Create instance failed"
                )
            }
            val responseData: Map<String, Any> = jacksonObjectMapper().readValue(data)
            val code = responseData["code"] as Int
            val message = responseData["message"] as String
            if (code != 0) {
                logger.error("Create instance failed, msg:$message")
                LogUtils.addRedLine(rabbitTemplate, task.buildId, "创建实例失败，详情：$message", task.taskId, task.executeCount ?: 1)
//                throw BuildTaskException(ERROR_BUILD_TASK_BCS_CREATE_INSTANCE_FAIL, "Create instance failed, msg:$message")
                return AtomResponse(
                    buildStatus = BuildStatus.FAILED,
                    errorType = ErrorType.USER,
                    errorCode = USER_TASK_OPERATE_FAIL,
                    errorMsg = "创建实例失败"
                )
            }
            val instData = responseData["data"] as Map<String, Any>
            val instIdList = instData["inst_id_list"] as List<Int>

            Thread.sleep(5 * 1000)

            instIdList.forEach {
                val appResult = waitForRunning(appIdStr, projectId, it.toString(), timeout.toLong())
                if (!appResult.first) {
                    LogUtils.addRedLine(rabbitTemplate, task.buildId, "BCS operation failed", task.taskId, task.executeCount ?: 1)
                    LogUtils.addRedLine(rabbitTemplate, task.buildId, appResult.second, task.taskId, task.executeCount ?: 1)
//                    throw BuildTaskException(ERROR_BUILD_TASK_BCS_OPERATE_FAIL, "BCS operate failed, msg: ${appResult.second}")
                    return AtomResponse(
                        buildStatus = BuildStatus.FAILED,
                        errorType = ErrorType.USER,
                        errorCode = USER_TASK_OPERATE_FAIL,
                        errorMsg = "BCS operation failed"
                    )
                }
            }
            LogUtils.addLine(rabbitTemplate, task.buildId, "BCS operation success!", task.taskId, task.executeCount ?: 1)
        }
        return AtomResponse(BuildStatus.SUCCEED)
    }

    private fun reCreateInstance(
        category: BcsCategory,
        appidStr: String,
        projectId: String,
        instIdList: String
    ): Pair<Int, String> {
        val token = bkAuthTokenApi.getAccessToken(serviceCode)
        val url =
            bcsAppUrl + "cc_app_ids/$appidStr/projects/$projectId/instances/batch_recreate/?access_token=$token&category=${category.getValue()}"
        logger.info("Recreate instance, request url: $url")
        val requestData = mapOf("inst_id_list" to listOf(instIdList))
        val requestBody = ObjectMapper().writeValueAsString(requestData)
        logger.info("Recreate instance, request body: $requestBody")
        val request = Request.Builder()
            .url(url)
            .post(RequestBody.create(JSON, requestBody))
            .build()
        OkhttpUtils.doHttp(request).use { response ->
            val data = response.body()!!.string()
            logger.info("Recreate instance, response: $data")
            if (!response.isSuccessful) {
                throw RuntimeException(data)
            }
            val responseData: Map<String, Any> = jacksonObjectMapper().readValue(data)
            val code = responseData["code"] as Int
            val message = responseData["message"] as String
            return Pair(code, message)
        }
    }

    private fun deleteInstance(
        category: BcsCategory,
        appidStr: String,
        projectId: String,
        instIdList: String
    ): Pair<Int, String> {
        val token = bkAuthTokenApi.getAccessToken(serviceCode)
        val url =
            bcsAppUrl + "cc_app_ids/$appidStr/projects/$projectId/instances/batch_delete/?access_token=$token&category=${category.getValue()}"
        logger.info("delete instance, request url: $url")
        val requestData = mapOf("inst_id_list" to listOf(instIdList))
        val requestBody = ObjectMapper().writeValueAsString(requestData)
        logger.info("delete instance, request body: $requestBody")
        val request = Request.Builder()
            .url(url)
            .delete(RequestBody.create(JSON, requestBody))
            .build()
        OkhttpUtils.doHttp(request).use { response ->
            val data = response.body()!!.string()
            logger.info("delete instance, response: $data")
            if (!response.isSuccessful) {
                throw RuntimeException(data)
            }
            val responseData: Map<String, Any> = jacksonObjectMapper().readValue(data)
            val code = responseData["code"] as Int
            val message = responseData["message"] as String
            return Pair(code, message)
        }
    }

    private fun scaleInstance(
        category: BcsCategory,
        appidStr: String,
        projectId: String,
        instIdList: String,
        instNum: Int
    ): Pair<Int, String> {
        val token = bkAuthTokenApi.getAccessToken(serviceCode)
        val url =
            bcsAppUrl + "cc_app_ids/$appidStr/projects/$projectId/instances/batch_scale/?access_token=$token&category=${category.getValue()}&instance_num=$instNum"
        logger.info("Scale instance, request url: $url")
        val requestData = mapOf("inst_id_list" to listOf(instIdList))
        val requestBody = ObjectMapper().writeValueAsString(requestData)
        logger.info("Scale instance, request body: $requestBody")
        val request = Request.Builder()
            .url(url)
            .put(RequestBody.create(JSON, requestBody))
            .build()
        OkhttpUtils.doHttp(request).use { response ->
            val data = response.body()!!.string()
            logger.info("Scale instance, response: $data")
            if (!response.isSuccessful) {
                throw RuntimeException(data)
            }
            val responseData: Map<String, Any> = jacksonObjectMapper().readValue(data)
            val code = responseData["code"] as Int
            val message = responseData["message"] as String
            return Pair(code, message)
        }
    }

    private fun applicationUpdate(
        appidStr: String,
        projectId: String,
        versionId: Int,
        instIdList: String,
        namespaceVar: Map<String, Map<String, String>>
    ): Pair<Int, String> {
        val token = bkAuthTokenApi.getAccessToken(serviceCode)
        val url =
            bcsAppUrl + "cc_app_ids/$appidStr/projects/$projectId/instances/batch_application_update/?access_token=$token&category=${BcsCategory.APPLICATION.getValue()}&version_id=$versionId"
        logger.info("Update application instance, request url: $url")
        val requestData = mapOf(
            "inst_id_list" to listOf(instIdList),
            "inst_variables" to namespaceVar
        )
        val requestBody = ObjectMapper().writeValueAsString(requestData)
        logger.info("Update application instance, request body: $requestBody")
        val request = Request.Builder()
            .url(url)
            .put(RequestBody.create(JSON, requestBody))
            .build()
        OkhttpUtils.doHttp(request).use { response ->
            val data = response.body()!!.string()
            logger.info("Update application instance, response: $data")
            if (!response.isSuccessful) {
                throw RuntimeException(data)
            }
            val responseData: Map<String, Any> = jacksonObjectMapper().readValue(data)
            val code = responseData["code"] as Int
            val message = responseData["message"] as String
            return Pair(code, message)
        }
    }

    private fun updateInstance(
        category: BcsCategory,
        appidStr: String,
        projectId: String,
        versionId: Int,
        instNum: Int,
        instIdList: String,
        instVar: Map<String, Map<String, String>>
    ): Pair<Int, String> {
        val token = bkAuthTokenApi.getAccessToken(serviceCode)
        val url =
            bcsAppUrl + "cc_app_ids/$appidStr/projects/$projectId/instances/batch_update/?access_token=$token&category=${category.getValue()}&version_id=$versionId&instance_num=$instNum"
        logger.info("Update instance, request url: $url")
        val requestData = mapOf(
            "inst_id_list" to listOf(instIdList),
            "inst_variables" to instVar
        )
        val requestBody = ObjectMapper().writeValueAsString(requestData)
        logger.info("Update instance, request body: $requestBody")
        val request = Request.Builder()
            .url(url)
            .put(RequestBody.create(JSON, requestBody))
            .build()
        OkhttpUtils.doHttp(request).use { response ->
            val data = response.body()!!.string()
            logger.info("Update instance, response: $data")
            if (!response.isSuccessful) {
                throw RuntimeException(data)
            }
            val responseData: Map<String, Any> = jacksonObjectMapper().readValue(data)
            val code = responseData["code"] as Int
            val message = responseData["message"] as String
            return Pair(code, message)
        }
    }

    private fun getProjectOriginId(projectCode: String): String {
        val token = bkAuthTokenApi.getAccessToken(serviceCode)
        val url = "$projectUrl$projectCode/?access_token=$token"
        logger.info("Get project info, request url: $url")
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        OkhttpUtils.doHttp(request).use { response ->
            val data = response.body()!!.string()
            logger.info("Get project info, response: $data")
            if (!response.isSuccessful) {
                throw RuntimeException(data)
            }
            val responseData: Map<String, Any> = jacksonObjectMapper().readValue(data)
            val code = responseData["code"] as Int
            if (0 == code) {
                val dataMap = responseData["data"] as Map<String, Any>
                return dataMap["project_id"] as String
            } else {
                throw RuntimeException(data)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BcsContainerOpAtom::class.java)
        private val JSON = MediaType.parse("application/json;charset=utf-8")
    }

    data class TaskResult(val isFinish: Boolean, val success: Boolean, val msg: String)
}