package com.tencent.devops.quality.service.v2

import com.fasterxml.jackson.databind.ObjectMapper
import com.tencent.devops.common.client.Client
import com.tencent.devops.process.api.ServicePipelineResource
import com.tencent.devops.quality.api.v2.pojo.QualityHisMetadata
import com.tencent.devops.quality.api.v2.pojo.enums.QualityDataType
import com.tencent.devops.quality.api.v2.pojo.request.MetadataCallback
import com.tencent.devops.quality.dao.v2.QualityHisMetadataDao
import org.apache.commons.lang3.math.NumberUtils
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class QualityHisMetadataService @Autowired constructor(
    private val objectMapper: ObjectMapper,
    private val client: Client,
    private val dslContext: DSLContext,
    private val hisMetadataDao: QualityHisMetadataDao,
    private val metadataService: QualityMetadataService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(QualityHisMetadataService::class.java)
    }

    fun saveHisMetadata(projectId: String, pipelineId: String, buildId: String, callback: MetadataCallback): String {
        logger.info("save history metadata for build: $buildId")
        logger.info("save history metadata data:\n$callback")

        val buildNo = client.get(ServicePipelineResource::class).getBuildNoByBuildIds(projectId, pipelineId, setOf(buildId)).data?.get(buildId) ?: 0
        hisMetadataDao.saveHisOriginMetadata(dslContext,
                projectId,
                pipelineId,
                buildId,
                buildNo.toString(),
                callbackStr = objectMapper.writeValueAsString(callback))
        hisMetadataDao.batchSaveHisDetailMetadata(dslContext,
                projectId,
                pipelineId,
                buildId,
                buildNo.toString(),
                callback.elementType,
                callback.data.map { QualityHisMetadata(
                        it.enName,
                        it.cnName,
                        it.detail,
                        it.type,
                        callback.elementType,
                        it.msg,
                        it.value,
                        it.extra
                ) })
        return "success save metadata for $buildId"
    }

    fun saveHisMetadata(projectId: String, pipelineId: String, buildId: String, elementType: String, data: Map<String, String>): Boolean {
        logger.info("save history metadata for build($elementType): $buildId")
        logger.info("save history metadata data:\n$data")

        val buildNo = client.get(ServicePipelineResource::class).getBuildNoByBuildIds(projectId, pipelineId, setOf(buildId)).data?.get(buildId) ?: 0
        val metadataMap = metadataService.serviceListByDataId(elementType, data.keys).map { it.dataId to it }.toMap()
        val qualityMetadataList = data.map {
            val key = it.key
            val value = it.value
            val isNumber = NumberUtils.isNumber(value)
            val isDigits = NumberUtils.isDigits(value)
            val metadata = metadataMap[key]
            // int
            if (isNumber && isDigits) {
                return@map QualityHisMetadata(
                        key,
                        metadata?.dataName ?: "",
                        metadata?.elementDetail ?: "",
                        QualityDataType.INT,
                        metadata?.elementType ?: "",
                        "from script element",
                        value,
                        null

                )
            }

            // float
            if (isNumber && !isDigits) {
                return@map QualityHisMetadata(
                        key,
                        metadata?.dataName ?: "",
                        metadata?.elementDetail ?: "",
                        QualityDataType.FLOAT,
                        metadata?.elementType ?: "",
                        "from script element",
                        value,
                        null

                )
            }

            // boolean
            return@map QualityHisMetadata(
                    key,
                    metadata?.dataName ?: "",
                    metadata?.elementDetail ?: "",
                    QualityDataType.BOOLEAN,
                    metadata?.elementType ?: "",
                    "from script element",
                    value,
                    null

            )
        }
        hisMetadataDao.batchSaveHisDetailMetadata(dslContext, projectId, pipelineId, buildId, buildNo.toString(), elementType, qualityMetadataList)
        return true
    }

    fun serviceGetHisMetadata(buildId: String): List<QualityHisMetadata> {
        return hisMetadataDao.getHisMetadata(dslContext, buildId)?.map {
            QualityHisMetadata(
                    it.dataId,
                    it.dataName,
                    it.elementDetail,
                    QualityDataType.valueOf(it.dataType),
                    it.elementType ?: "",
                    it.dataDesc,
                    it.dataValue,
                    it.extra
            )
        } ?: listOf()
    }
}