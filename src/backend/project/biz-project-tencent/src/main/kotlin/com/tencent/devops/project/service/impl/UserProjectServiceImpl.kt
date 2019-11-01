package com.tencent.devops.project.service.impl

import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.common.service.gray.Gray
import com.tencent.devops.project.dao.FavoriteDao
import com.tencent.devops.project.dao.GrayTestDao
import com.tencent.devops.project.dao.ServiceDao
import com.tencent.devops.project.dao.ServiceTypeDao
import com.tencent.devops.project.pojo.Result
import com.tencent.devops.project.pojo.ServiceUpdateUrls
import com.tencent.devops.project.pojo.service.*
import com.tencent.devops.project.service.tof.TOFService
import com.tencent.devops.project.utils.BG_IEG_ID
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class UserProjectServiceImpl @Autowired constructor(
    private val dslContext: DSLContext,
    private val serviceTypeDao: ServiceTypeDao,
    private val serviceDao: ServiceDao,
    private val grayTestDao: GrayTestDao,
    private val favoriteDao: FavoriteDao,
    private val gray: Gray,
    private val redisOperation: RedisOperation,
    private val tofService: TOFService
) : AbsUserProjectServiceServiceImpl(dslContext,serviceTypeDao, serviceDao, favoriteDao, gray, redisOperation) {

    override fun getService(userId: String, serviceId: Long): Result<ServiceVO> {
        val tServiceRecord = serviceDao.select(dslContext, serviceId)
        if (tServiceRecord != null) {
            val isIEGMember = tofService.getUserDeptDetail(userId).bgId == BG_IEG_ID

            return Result(
                    ServiceVO(
                            id = tServiceRecord.id ?: 0,
                            name = tServiceRecord.name,
                            link = tServiceRecord.link,
                            linkNew = tServiceRecord.linkNew,
                            status = tServiceRecord.status,
                            injectType = tServiceRecord.injectType,
                            iframeUrl = tServiceRecord.iframeUrl,
                            cssUrl = tServiceRecord.cssUrl,
                            jsUrl = tServiceRecord.jsUrl,
                            grayCssUrl = tServiceRecord.grayCssUrl,
                            grayJsUrl = tServiceRecord.grayJsUrl,
                            showProjectList = tServiceRecord.showProjectList,
                            showNav = tServiceRecord.showNav,
                            projectIdType = tServiceRecord.projectIdType,
                            collected = favoriteDao.countFavorite(dslContext, userId, tServiceRecord.id) > 0,
                            logoUrl = tServiceRecord.logoUrl,
                            webSocket = tServiceRecord.webSocket,
                            hidden = isServiceHidden(tServiceRecord.name, isIEGMember),
                            weigHt = tServiceRecord.weight ?:0
                    )
            )
        } else {
            return Result(405, "无限ID,获取服务信息失败")
        }
    }

    override fun updateService(userId: String, serviceId: Long, serviceCreateInfo: ServiceCreateInfo): Result<Boolean> {
        return super.updateService(userId, serviceId, serviceCreateInfo)
    }

    override fun updateServiceUrlByBatch(userId: String, serviceUrlUpdateInfoList: List<ServiceUrlUpdateInfo>?): Result<Boolean> {
        return super.updateServiceUrlByBatch(userId, serviceUrlUpdateInfoList)
    }

    override fun deleteService(userId: String, serviceId: Long): Result<Boolean> {
        return super.deleteService(userId, serviceId)
    }

    override fun listOPService(userId: String): Result<List<OPPServiceVO>> {
        return super.listOPService(userId)
    }

    override fun createService(userId: String, serviceCreateInfo: ServiceCreateInfo): Result<OPPServiceVO> {
        return super.createService(userId, serviceCreateInfo)
    }

    override fun updateCollected(userId: String, serviceId: Long, collector: Boolean): Result<Boolean> {
        return super.updateCollected(userId, serviceId, collector)
    }

    override fun listService(userId: String, projectId: String?): Result<ArrayList<ServiceListVO>> {
        return super.listService(userId, projectId)
    }

    override fun syncService(userId: String, services: List<ServiceListVO>) {
            dslContext.transaction { configuration ->
                val context = DSL.using(configuration)
                services.forEach {
                    val type = serviceTypeDao.create(context, userId, it.title, it.weigHt)
                    it.children.forEach { s ->
                        serviceDao.create(context, userId, type.id, s)
                    }
                }
            }
    }

    override fun updateServiceUrls(userId: String, name: String, serviceUpdateUrls: ServiceUpdateUrls): Result<Boolean> {
        return super.updateServiceUrls(userId, name, serviceUpdateUrls)
    }

    private fun isServiceHidden(serviceName: String, isIEGMember: Boolean): Boolean {
        return !(if (serviceName.contains("(Monitor)", false) || serviceName.contains("(BCS)", false)) {
            isIEGMember
        } else {
            true
        })
    }
}



