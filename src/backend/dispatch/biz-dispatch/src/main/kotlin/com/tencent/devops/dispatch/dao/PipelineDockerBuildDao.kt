package com.tencent.devops.dispatch.dao

import com.tencent.devops.common.api.util.SecurityUtil
import com.tencent.devops.dispatch.pojo.enums.PipelineTaskStatus
import com.tencent.devops.model.dispatch.tables.TDispatchPipelineDockerBuild
import com.tencent.devops.model.dispatch.tables.records.TDispatchPipelineDockerBuildRecord
import org.jooq.DSLContext
import org.jooq.Result
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class PipelineDockerBuildDao {

    fun startBuild(
        dslContext: DSLContext,
        projectId: String,
        pipelineId: String,
        buildId: String,
        vmSeqId: Int,
        secretKey: String,
        status: PipelineTaskStatus,
        zone: String?
    ): Long {
        with(TDispatchPipelineDockerBuild.T_DISPATCH_PIPELINE_DOCKER_BUILD) {
            val now = LocalDateTime.now()
            val preRecord =
                dslContext.selectFrom(this).where(BUILD_ID.eq(buildId)).and(VM_SEQ_ID.eq(vmSeqId)).fetchAny()
            if (preRecord != null) { // 支持更新，让用户进行步骤重试时继续能使用
                dslContext.update(this).set(SECRET_KEY, SecurityUtil.encrypt(secretKey))
                    .set(STATUS, status.status)
                    .set(CREATED_TIME, now)
                    .set(UPDATED_TIME, now)
                    .set(ZONE, zone)
                    .where(ID.eq(preRecord.id)).execute()
                return preRecord.id
            }
            return dslContext.insertInto(
                this,
                PROJECT_ID,
                PIPELINE_ID,
                BUILD_ID,
                VM_SEQ_ID,
                SECRET_KEY,
                STATUS,
                CREATED_TIME,
                UPDATED_TIME,
                ZONE
            )
                .values(
                    projectId,
                    pipelineId,
                    buildId,
                    vmSeqId,
                    SecurityUtil.encrypt(secretKey),
                    status.status,
                    now,
                    now,
                    zone
                )
                .returning(ID)
                .fetchOne().id
        }
    }

    fun updateStatus(
        dslContext: DSLContext,
        buildId: String,
        vmSeqId: Int,
        status: PipelineTaskStatus
    ): Boolean {
        with(TDispatchPipelineDockerBuild.T_DISPATCH_PIPELINE_DOCKER_BUILD) {
            return dslContext.update(this)
                .set(STATUS, status.status)
                .set(UPDATED_TIME, LocalDateTime.now())
                .where(BUILD_ID.eq(buildId))
                .and(VM_SEQ_ID.eq(vmSeqId))
                .execute() == 1
        }
    }

    fun listBuilds(
        dslContext: DSLContext,
        buildId: String
    ): Result<TDispatchPipelineDockerBuildRecord> {
        with(TDispatchPipelineDockerBuild.T_DISPATCH_PIPELINE_DOCKER_BUILD) {
            return dslContext.selectFrom(this)
                .where(BUILD_ID.eq(buildId))
                .fetch()
        }
    }

    fun getBuild(
        dslContext: DSLContext,
        buildId: String,
        vmSeqId: Int
    ): TDispatchPipelineDockerBuildRecord? {
        with(TDispatchPipelineDockerBuild.T_DISPATCH_PIPELINE_DOCKER_BUILD) {
            return dslContext.selectFrom(this)
                .where(BUILD_ID.eq(buildId))
                .and(VM_SEQ_ID.eq(vmSeqId))
                .fetchOne()
        }
    }
}