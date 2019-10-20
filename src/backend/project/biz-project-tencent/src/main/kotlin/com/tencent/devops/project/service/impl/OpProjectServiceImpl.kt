package com.tencent.devops.project.service.impl

import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.common.service.gray.Gray
import com.tencent.devops.common.web.mq.EXCHANGE_PAASCC_PROJECT_UPDATE
import com.tencent.devops.common.web.mq.ROUTE_PAASCC_PROJECT_UPDATE
import com.tencent.devops.project.dao.ProjectDao
import com.tencent.devops.project.dao.ProjectLabelRelDao
import com.tencent.devops.project.pojo.*
import org.jooq.DSLContext
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class OpProjectServiceImpl @Autowired constructor(
    private val dslContext: DSLContext,
    private val projectDao: ProjectDao,
    private val projectLabelRelDao: ProjectLabelRelDao,
    private val rabbitTemplate: RabbitTemplate,
    private val redisOperation: RedisOperation,
    private val gray: Gray
): AbsOpProjectServiceImpl(dslContext,projectDao, projectLabelRelDao, redisOperation, gray) {
    override fun listGrayProject(): Result<OpGrayProject> {
        return super.listGrayProject()
    }

    override fun setGrayProject(projectCodeList: List<String>, operateFlag: Int): Boolean {
        return super.setGrayProject(projectCodeList, operateFlag)
    }

    override fun updateProjectFromOp(userId: String, accessToken: String, projectInfoRequest: OpProjectUpdateInfoRequest): Int {
        val count = super.updateProjectFromOp(userId, accessToken, projectInfoRequest)
        val dbProjectRecord = projectDao.get(dslContext, projectInfoRequest.projectId)
        rabbitTemplate.convertAndSend(
                EXCHANGE_PAASCC_PROJECT_UPDATE,
                ROUTE_PAASCC_PROJECT_UPDATE, PaasCCUpdateProject(
                userId = userId,
                accessToken = accessToken,
                projectId = projectInfoRequest.projectId,
                retryCount = 0,
                projectUpdateInfo = ProjectUpdateInfo(
                        projectName = projectInfoRequest.projectName,
                        projectType = projectInfoRequest.projectType,
                        bgId = projectInfoRequest.bgId,
                        bgName = projectInfoRequest.bgName,
                        centerId = projectInfoRequest.centerId,
                        centerName = projectInfoRequest.centerName,
                        deptId = projectInfoRequest.deptId,
                        deptName = projectInfoRequest.deptName,
                        description = dbProjectRecord!!.description ?: "",
                        englishName = dbProjectRecord!!.englishName,
                        ccAppId = projectInfoRequest.ccAppId,
                        ccAppName = projectInfoRequest.cc_app_name,
                        kind = projectInfoRequest.kind
//                        secrecy = projectInfoRequest.secrecyFlag
                )
            )
        )
        return count
    }

    override fun getProjectList(projectName: String?, englishName: String?, projectType: Int?, isSecrecy: Boolean?, creator: String?, approver: String?, approvalStatus: Int?, offset: Int, limit: Int, grayFlag: Boolean): Result<Map<String, Any?>?> {
        return super.getProjectList(projectName, englishName, projectType, isSecrecy, creator, approver, approvalStatus, offset, limit, grayFlag)
    }

    override fun getProjectCount(projectName: String?, englishName: String?, projectType: Int?, isSecrecy: Boolean?, creator: String?, approver: String?, approvalStatus: Int?, grayFlag: Boolean): Result<Int> {
        return super.getProjectCount(projectName, englishName, projectType, isSecrecy, creator, approver, approvalStatus, grayFlag)
    }
}