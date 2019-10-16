package com.tencent.devops.quality.service.v2

import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.util.HashUtil
import com.tencent.devops.quality.api.v2.pojo.ControlPointPosition
import com.tencent.devops.quality.api.v2.pojo.RuleIndicatorSet
import com.tencent.devops.quality.api.v2.pojo.RuleTemplate
import com.tencent.devops.quality.dao.v2.QualityIndicatorDao
import com.tencent.devops.quality.dao.v2.QualityRuleTemplateDao
import com.tencent.devops.quality.dao.v2.QualityTemplateIndicatorMapDao
import com.tencent.devops.quality.api.v2.pojo.op.TemplateData
import com.tencent.devops.quality.api.v2.pojo.op.TemplateIndicatorMap
import com.tencent.devops.quality.api.v2.pojo.op.TemplateUpdateData
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class QualityTemplateService @Autowired constructor(
    private val dslContext: DSLContext,
    private val ruleTemplateDao: QualityRuleTemplateDao,
    private val indicatorService: QualityIndicatorService,
    private val controlPointService: QualityControlPointService,
    private val ruleTemplateIndicatorDao: QualityTemplateIndicatorMapDao,
    private val indicatorDao: QualityIndicatorDao
) {

    fun userListIndicatorSet(): List<RuleIndicatorSet> {
        return ruleTemplateDao.listIndicatorSetEnable(dslContext)?.map {
            val indicatorIds = ruleTemplateIndicatorDao.queryTemplateMap(it.id, dslContext)?.map { it.indicatorId }
                    ?: listOf()
            val indicators = indicatorService.serviceList(indicatorIds)
            RuleIndicatorSet(
                    HashUtil.encodeLongId(it.id),
                    it.name,
                    it.desc,
                    indicators
            )
        } ?: listOf()
    }

    fun userList(): List<RuleTemplate> {
        val templateList = ruleTemplateDao.listTemplateEnable(dslContext)
        return templateList?.map {
            val indicatorIds = ruleTemplateIndicatorDao.queryTemplateMap(it.id, dslContext)?.map { it.indicatorId }
                    ?: listOf()
            val controlPoint = controlPointService.serviceGet(it.controlPoint)
            val indicators = indicatorService.serviceList(indicatorIds)
            RuleTemplate(
                    HashUtil.encodeLongId(it.id),
                    it.name,
                    it.desc,
                    indicators,
                    it.stage,
                    it.controlPoint,
                    controlPoint?.name ?: "",
                    ControlPointPosition(it.controlPointPosition),
                    listOf(ControlPointPosition("BEFORE"), ControlPointPosition("AFTER"))
            )
        } ?: listOf()
    }

    fun opList(userId: String, page: Int?, pageSize: Int?): Page<TemplateData> {
        val data = ruleTemplateDao.list(userId, page!!, pageSize!!, dslContext).map {
            val templateIndicatorMap = ruleTemplateIndicatorDao.listByTemplateId(it.id, dslContext)
            val indicatorIds = templateIndicatorMap.map { it.indicatorId }.toHashSet()
            val indicatorList = indicatorDao.listByIds(dslContext, indicatorIds)

            val templateIndicatorMaps = templateIndicatorMap.map { it1 ->
                val indicatorInst = indicatorList?.filter { it2 -> it2.id == it1.indicatorId }?.getOrNull(0)
                val indicatorName = if (indicatorInst != null) {
                    "${indicatorInst.elementName}-${indicatorInst.elementDetail}-${indicatorInst.cnName}"
                } else null
                TemplateIndicatorMap(
                        it1.id,
                        it1.templateId,
                        it1.indicatorId,
                        indicatorName,
                        it1.operation,
                        it1.threshold
                )
            }
            val controlPoint = controlPointService.serviceGetByType(it.controlPoint)
            TemplateData(
                    it.id, it.name, it.type, it.desc, it.stage, it.controlPoint,
                    if (controlPoint == null) null else controlPoint.name,
                    it.controlPointPosition, it.enable, templateIndicatorMap.size,
                    templateIndicatorMaps
            )
        }
        val count = ruleTemplateDao.count(dslContext)
        return Page(page, pageSize, count, data)
    }

    fun opCreate(userId: String, templateUpdateData: TemplateUpdateData): Boolean {
        dslContext.transaction { configuration ->
            val tDSLContext = DSL.using(configuration)
            val templateId = ruleTemplateDao.create(userId, templateUpdateData.templateUpdate, tDSLContext)

            if (templateUpdateData.indicatorDetail != null) {
                templateUpdateData.indicatorDetail!!.forEach { it.templateId = templateId }
            }
            ruleTemplateIndicatorDao.batchCreate(tDSLContext, templateUpdateData.indicatorDetail)
        }
        return true
    }

    fun opDelete(userId: String, id: Long): Boolean {
        logger.info("user($userId) is deleting the rule($id)")
        dslContext.transaction { configuration ->
            val tDSLContext = DSL.using(configuration)
            ruleTemplateDao.delete(userId, id, tDSLContext)
            ruleTemplateIndicatorDao.deleteRealByTemplateId(tDSLContext, id)
        }
        return true
    }

    fun opUpdate(userId: String, id: Long, templateUpdateData: TemplateUpdateData): Boolean {
        logger.info("user($userId) is updating the rule($id)")
        dslContext.transaction { configuration ->
            val tDSLContext = DSL.using(configuration)
            ruleTemplateDao.update(userId, id, templateUpdateData.templateUpdate, tDSLContext)
            ruleTemplateIndicatorDao.deleteRealByTemplateId(tDSLContext, id)
            ruleTemplateIndicatorDao.batchCreate(tDSLContext, templateUpdateData.indicatorDetail)
        }
        return true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(QualityTemplateService::class.java)
    }
}