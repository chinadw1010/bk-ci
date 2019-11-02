package com.tencent.devops.process.engine.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.common.event.pojo.measure.MeasureRequest
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.pipeline.enums.StartType
import com.tencent.devops.process.engine.dao.PipelineBuildVarDao
import com.tencent.devops.process.engine.pojo.event.PipelineBuildCancelEvent
import com.tencent.devops.process.engine.service.template.TemplateService
import com.tencent.devops.process.pojo.measure.ElementMeasureData
import com.tencent.devops.process.pojo.measure.PipelineBuildData
import com.tencent.devops.process.service.measure.MeasureEventDispatcher
import com.tencent.devops.process.utils.PIPELINE_START_PARENT_BUILD_ID
import com.tencent.devops.process.utils.PIPELINE_START_PARENT_PIPELINE_ID
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class MeasureService @Autowired constructor(
    private val pipelineRuntimeService: PipelineRuntimeService,
    private val templateService: TemplateService,
    private val pipelineBuildVarDao: PipelineBuildVarDao,
    private val dslContext: DSLContext,
    private val objectMapper: ObjectMapper,
    private val measureEventDispatcher: MeasureEventDispatcher
) {

    @Value("\${gateway.service:#{null}}")
    private val serviceGateway: String? = null

    fun postPipelineData(
        projectId: String,
        pipelineId: String,
        buildId: String,
        startTime: Long,
        startType: String,
        username: String,
        buildStatus: BuildStatus,
        buildNum: Int,
        model: Model?,
        errorType: String? = null,
        errorCode: Int? = null,
        errorMsg: String? = null
    ) {
        try {
            if (model == null) {
                logger.warn("The pipeline.json is not exist of pipeline($pipelineId)")
                return
            }

            val json = JsonUtil.getObjectMapper().writeValueAsString(model)

            val variable = pipelineBuildVarDao.getVars(dslContext, buildId)
            val metaInfo = mapOf(
                "parentPipelineId" to (variable[PIPELINE_START_PARENT_PIPELINE_ID] ?: ""),
                "parentBuildId" to (variable[PIPELINE_START_PARENT_BUILD_ID] ?: "")
            )

            val templateId = getTemplateId(pipelineId)
            val data = PipelineBuildData(
                projectId = projectId,
                pipelineId = pipelineId,
                templateId = templateId,
                buildId = buildId,
                beginTime = startTime,
                endTime = System.currentTimeMillis(),
                startType = StartType.toStartType(startType),
                buildUser = username,
                isParallel = false,
                buildResult = buildStatus,
                pipeline = json,
                buildNum = buildNum,
                metaInfo = metaInfo,
                errorType = errorType,
                errorCode = errorCode,
                errorMsg = errorMsg
            )
            val url = "http://$serviceGateway/measure/api/service/pipelines/addData"

            val requestBody = objectMapper.writeValueAsString(data)
            measureEventDispatcher.dispatch(MeasureRequest(projectId, pipelineId, buildId, url, requestBody))
        } catch (t: Throwable) {
            logger.warn("Fail to post the pipeline measure data of build($buildId)", t)
        }
    }

    fun onCancelNew(event: PipelineBuildCancelEvent) {
        try {
            val tasks = pipelineRuntimeService.getAllBuildTask(event.buildId)
            if (tasks.isEmpty()) {
                return
            }
            tasks.forEach { task ->
                with(task) {
                    if (BuildStatus.isRunning(status)) {
                        val tStartTime = startTime?.timestampmilli() ?: 0
                        val atomCode = task.taskParams["atomCode"] as String? ?: ""
                        postElementDataNew(
                            projectId = event.projectId,
                            pipelineId = pipelineId,
                            taskId = taskId,
                            atomCode = atomCode,
                            name = taskName,
                            buildId = event.buildId,
                            startTime = tStartTime,
                            status = BuildStatus.CANCELED,
                            type = taskType,
                            executeCount = executeCount
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Fail to post the cancel event elements of ${event.buildId}", e)
        }
    }

    fun postElementDataNew(
        projectId: String,
        pipelineId: String,
        taskId: String,
        atomCode: String,
        name: String,
        buildId: String,
        startTime: Long,
        status: BuildStatus,
        type: String,
        executeCount: Int?,
        extraInfo: Map<String, Any>? = null,
        errorType: String? = null,
        errorCode: Int? = null,
        errorMsg: String? = null
    ) {
        try {
            val url = "http://$serviceGateway/measure/api/service/elements/addData"

            val elementMeasureData = ElementMeasureData(
                id = taskId,
                name = name,
                pipelineId = pipelineId,
                projectId = projectId,
                buildId = buildId,
                atomCode = atomCode,
                status = status,
                beginTime = startTime,
                endTime = System.currentTimeMillis(),
                type = type
            )
            if (extraInfo != null && extraInfo.isNotEmpty()) {
                val extraInfoStr = ObjectMapper().writeValueAsString(extraInfo)
                elementMeasureData.extraInfo = extraInfoStr
            }
            if (errorType != null) {
                elementMeasureData.errorType = errorType
                elementMeasureData.errorCode = errorCode
                elementMeasureData.errorMsg = errorMsg
            }
            val requestBody = ObjectMapper().writeValueAsString(elementMeasureData)
            logger.info("add the element data, request data: $elementMeasureData")
            measureEventDispatcher.dispatch(MeasureRequest(projectId, pipelineId, buildId, url, requestBody))
        } catch (e: Throwable) {
            logger.error("Fail to add the element data, $e")
        }
    }

    private fun getTemplateId(pipelineId: String): String {
        return templateService.listPipelineTemplate(setOf(pipelineId))?.firstOrNull()?.templateId ?: ""
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MeasureService::class.java)
    }
}