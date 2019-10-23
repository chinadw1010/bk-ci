package com.tencent.devops.environment.service

import com.tencent.devops.common.api.exception.OperationException
import com.tencent.devops.common.api.util.timestamp
import com.tencent.devops.common.auth.api.AuthPermission
import com.tencent.devops.common.misc.client.BcsClient
import com.tencent.devops.environment.dao.BcsClusterDao
import com.tencent.devops.environment.dao.NodeDao
import com.tencent.devops.environment.dao.ProjectConfigDao
import com.tencent.devops.environment.dao.StaticData
import com.tencent.devops.environment.permission.EnvironmentPermissionService
import com.tencent.devops.environment.pojo.BcsCluster
import com.tencent.devops.environment.pojo.BcsImageInfo
import com.tencent.devops.environment.pojo.BcsVmModel
import com.tencent.devops.environment.pojo.BcsVmParam
import com.tencent.devops.environment.pojo.ProjectConfig
import com.tencent.devops.environment.pojo.ProjectConfigParam
import com.tencent.devops.environment.pojo.ProjectInfo
import com.tencent.devops.environment.pojo.enums.NodeType
import com.tencent.devops.environment.utils.BcsVmParamCheckUtils
import com.tencent.devops.environment.utils.NodeStringIdUtils
import com.tencent.devops.model.environment.tables.records.TNodeRecord
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class BcsClusterService @Autowired constructor(
    private val bcsClient: BcsClient,
    private val dslContext: DSLContext,
    private val bcsClusterDao: BcsClusterDao,
    private val nodeDao: NodeDao,
    private val projectConfigDao: ProjectConfigDao,
    private val environmentPermissionService: EnvironmentPermissionService
) {

    fun addBcsVmNodes(userId: String, projectId: String, bcsVmParam: BcsVmParam) {
        if (!environmentPermissionService.checkNodePermission(userId, projectId, AuthPermission.CREATE)) {
            throw OperationException("没有创建节点的权限")
        }

        val existNodeList = nodeDao.listServerAndDevCloudNodes(dslContext, projectId)
        val existIpList = existNodeList.map {
            it.nodeIp
        }

        val vmCreateInfoPair =
            BcsVmParamCheckUtils.checkAndGetVmCreateParam(
                dslContext,
                projectConfigDao,
                nodeDao,
                projectId,
                userId,
                bcsVmParam
            )
        val bcsVmList = bcsClient.createVM(
            bcsVmParam.clusterId,
            projectId,
            bcsVmParam.instanceCount,
            vmCreateInfoPair.first,
            vmCreateInfoPair.second,
            vmCreateInfoPair.third
        )
        val now = LocalDateTime.now()
        val toAddNodeList = bcsVmList.filterNot { existIpList.contains(it.ip) }.map {
            TNodeRecord(
                null,
                "",
                projectId,
                it.ip,
                it.name,
                it.status,
                NodeType.BCSVM.name,
                it.clusterId,
                projectId,
                userId,
                now,
                now.plusDays(bcsVmParam.validity.toLong()),
                it.osName,
                null,
                null,
                false,
                "",
                "",
                null,
                now,
                userId
            )
        }

        dslContext.transaction { configuration ->
            val context = DSL.using(configuration)
            nodeDao.batchAddNode(context, toAddNodeList)
            val insertedNodeList = nodeDao.listServerNodesByIps(context, projectId, toAddNodeList.map { it.nodeIp })
            batchRegisterNodePermission(insertedNodeList = insertedNodeList, userId = userId, projectId = projectId)
        }
    }

    private fun batchRegisterNodePermission(
        insertedNodeList: List<TNodeRecord>,
        userId: String,
        projectId: String
    ) {
        insertedNodeList.forEach {
            environmentPermissionService.createNode(
                userId = userId,
                projectId = projectId,
                nodeId = it.nodeId,
                nodeName = "${NodeStringIdUtils.getNodeStringId(it)}(${it.nodeIp})"
            )
        }
    }

    fun listBcsCluster(): List<BcsCluster> {
        return bcsClusterDao.list().map { BcsCluster(it.clusterId, it.clusterName) }
    }

    fun listBcsVmModel(): List<BcsVmModel> {
        return StaticData.getBcsVmModelList()
    }

    fun listBcsImageList(): List<BcsImageInfo> {
        return StaticData.getBcsImageList()
    }

    fun getProjectInfo(userId: String, projectId: String): ProjectInfo {
        val projectConfig = projectConfigDao.get(dslContext, projectId, userId)
        val bcsVmEnabled = projectConfig.bcsvmEnalbed
        val bcsVmQuota = projectConfig.bcsvmQuota
        val bcsVmUsedCount = nodeDao.countBcsVm(dslContext, projectId)
        val bcsVmRestCount = bcsVmQuota - bcsVmUsedCount
        val importQuota = projectConfig.importQuota
        val devCloudEnable = projectConfig.devCloudEnalbed
        val devCloudQuota = projectConfig.devCloudQuota
        val devCloudUsedCount = nodeDao.countDevCloudVm(dslContext, projectId)

        return ProjectInfo(bcsVmEnabled, bcsVmQuota, bcsVmUsedCount, bcsVmRestCount, importQuota, devCloudEnable, devCloudQuota, devCloudUsedCount)
    }

    fun saveProjectConfig(projectConfigParam: ProjectConfigParam) {
        projectConfigDao.saveProjectConfig(dslContext,
                projectConfigParam.projectId,
                projectConfigParam.updatedUser,
                projectConfigParam.bcsVmEnabled,
                projectConfigParam.bcsVmQuota,
                projectConfigParam.importQuota,
                projectConfigParam.devCloudEnable,
                projectConfigParam.devCloudQuota
                )
    }

    fun listProjectConfig(): List<ProjectConfig> {
        return projectConfigDao.listProjectConfig(dslContext).map {
            ProjectConfig(
                    it.projectId,
                    it.updatedUser,
                    it.updatedTime.timestamp(),
                    it.bcsvmEnalbed,
                    it.bcsvmQuota,
                    it.importQuota,
                    it.devCloudEnalbed,
                    it.devCloudQuota
            )
        }
    }

    fun list(page: Int, pageSize: Int, projectId: String?): List<ProjectConfig> {
        return projectConfigDao.list(dslContext, page, pageSize, projectId).map {
            ProjectConfig(
                    it.projectId,
                    it.updatedUser,
                    it.updatedTime.timestamp(),
                    it.bcsvmEnalbed,
                    it.bcsvmQuota,
                    it.importQuota,
                    it.devCloudEnalbed,
                    it.devCloudQuota
            )
        }
    }

    fun countProjectConfig(projectId: String?): Int {
        return projectConfigDao.count(dslContext, projectId)
    }
}