package com.tencent.devops.process.service

import com.tencent.devops.common.api.auth.AUTH_HEADER_DEVOPS_ORGANIZATION_TYPE_BG
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.enums.ChannelCode
import com.tencent.devops.process.engine.dao.PipelineInfoDao
import com.tencent.devops.process.engine.dao.template.TemplateDao
import com.tencent.devops.process.engine.dao.template.TemplatePipelineDao
import com.tencent.devops.process.pojo.statistic.PipelineAndTemplateStatistic
import com.tencent.devops.process.util.exception.OrganizationTypeNotSupported
import com.tencent.devops.project.api.service.service.ServiceTxProjectResource
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 统计流水线与模板信息
 */
@Service
class PipelineTemplateStatisticService @Autowired constructor(
    private val dslContext: DSLContext,
    private val templateDao: TemplateDao,
    private val pipelineInfoDao: PipelineInfoDao,
    private val templatePipelineDao: TemplatePipelineDao,
    private val client: Client
) {

    /**
     * 通过组织信息获取流水线总数
     */
    fun getPipelineAndTemplateStatistic(
        userId: String,
        organizationType: String,
        organizationId: Int,
        deptName: String?,
        centerName: String?,
        interfaceName: String? = "Anon interface"
    ): PipelineAndTemplateStatistic {
        logger.info("$interfaceName:getPipelineAndTemplateStatistic:Input:($userId,$organizationType,$organizationId,$deptName,$centerName)")
        // 1.参数校验与预处理
        // 当前接口只支持BG的ID传递
        if (organizationType != AUTH_HEADER_DEVOPS_ORGANIZATION_TYPE_BG) {
            throw OrganizationTypeNotSupported("organizationType:$organizationType, is not supported yet,supported types are [ $AUTH_HEADER_DEVOPS_ORGANIZATION_TYPE_BG ]")
        }
        // 2.根据组织信息查询所有项目id
        // 调用TOF接口根据ID获取部门名称
        val projectsResult = client.get(ServiceTxProjectResource::class).getProjectEnNamesByOrganization(
            userId = userId,
            bgId = organizationId.toLong(),
            deptName = deptName?.trim(),
            centerName = centerName?.trim()
        )
        val projectIds = projectsResult.data?.toSet() ?: emptySet()
        logger.info("$interfaceName:getPipelineAndTemplateStatistic:Inner:projectIds=$projectIds")
        // 3.1 根据项目id集合查询流水线数量
        // 流水线总数
        val pipelineNum = pipelineInfoDao.countByProjectIds(dslContext, projectIds, ChannelCode.BS)
        // 实例化流水线总数
        val instancedPipelineNum = templatePipelineDao.countPipelineInstancedByTemplate(dslContext, projectIds).value1()
        // 模板总数
        val templateNum = templateDao.countTemplateByProjectIds(
            dslContext = dslContext,
            projectIds = projectIds,
            includePublicFlag = null,
            templateType = null,
            templateName = null,
            storeFlag = null
        )
        // 实例化模板总数
        val instancedTemplateNum = templatePipelineDao.countTemplateInstanced(dslContext, projectIds).value1()
        // 原始模板总数
        var srcTemplateIds: Set<String> = mutableSetOf()
        templateDao.getCustomizedTemplate(dslContext, projectIds).forEach {
            srcTemplateIds = srcTemplateIds.plus(it.value1())
        }
        templateDao.getOriginalTemplate(dslContext, projectIds).forEach {
            srcTemplateIds = srcTemplateIds.plus(it.value1())
        }
        val srcTemplateNum = srcTemplateIds.size
        // 实例化原始模板总数
        val instancedSrcTemplateNum = templatePipelineDao.countSrcTemplateInstanced(dslContext, srcTemplateIds).value1()
        logger.info("$interfaceName:getPipelineAndTemplateStatistic:Output:($pipelineNum,$instancedPipelineNum,$templateNum,$instancedTemplateNum,$srcTemplateNum,$instancedSrcTemplateNum)")
        return PipelineAndTemplateStatistic(
            pipelineNum = pipelineNum,
            instancedPipelineNum = instancedPipelineNum,
            templateNum = templateNum,
            instancedTemplateNum = instancedTemplateNum,
            srcTemplateNum = srcTemplateNum,
            instancedSrcTemplateNum = instancedSrcTemplateNum
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PipelineTemplateStatisticService::class.java)
    }
}