package com.tencent.devops.dispatch.dao

import com.tencent.devops.dispatch.pojo.enums.PipelineTaskStatus
import com.tencent.devops.model.dispatch.tables.TDispatchTstackBuild
import com.tencent.devops.model.dispatch.tables.records.TDispatchTstackBuildRecord
import org.jooq.DSLContext
import org.jooq.Result
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class TstackBuildDao {
    fun insertBuild(
        dslContext: DSLContext,
        projectId: String,
        pipelineId: String,
        buildId: String,
        vmSeqId: String,
        agentId: String,
        vmId: String,
        vmIp: String,
        volumeId: String,
        status: PipelineTaskStatus
    ): Long {
        with(TDispatchTstackBuild.T_DISPATCH_TSTACK_BUILD) {
            val now = LocalDateTime.now()
            val preRecord = dslContext.selectFrom(this).where(BUILD_ID.eq(buildId)).and(VM_SEQ_ID.eq(vmSeqId)).fetchAny()
            if (preRecord != null) { // 支持更新，让用户进行步骤重试时继续能使用
                dslContext.update(this)
                        .set(AGENT_ID, agentId)
                        .set(VM_ID, vmId)
                        .set(VM_IP, vmIp)
                        .set(VOLUME_ID, volumeId)
                        .set(STATUS, status.status)
                        .set(CREATED_TIME, now)
                        .set(UPDATED_TIME, now)
                        .where(ID.eq(preRecord.id)).execute()
                return preRecord.id
            }
            return dslContext.insertInto(this,
                    PROJECT_ID,
                    PIPELINE_ID,
                    BUILD_ID,
                    VM_SEQ_ID,
                    AGENT_ID,
                    VM_ID,
                    VM_IP,
                    VOLUME_ID,
                    STATUS,
                    CREATED_TIME,
                    UPDATED_TIME
            )
                    .values(
                            projectId,
                            pipelineId,
                            buildId,
                            vmSeqId,
                            agentId,
                            vmId,
                            vmIp,
                            volumeId,
                            status.status,
                            now,
                            now
                    )
                    .returning(ID)
                    .fetchOne().id.toLong()
        }
    }

    fun updateStatus(
        dslContext: DSLContext,
        buildId: String,
        vmSeqId: String,
        status: PipelineTaskStatus
    ) {
        with(TDispatchTstackBuild.T_DISPATCH_TSTACK_BUILD) {
            dslContext.update(this)
                    .set(STATUS, status.status)
                    .set(UPDATED_TIME, LocalDateTime.now())
                    .where(BUILD_ID.eq(buildId))
                    .and(VM_SEQ_ID.eq(vmSeqId))
                    .execute()
        }
    }

    fun listBuilds(
        dslContext: DSLContext,
        buildId: String
    ): Result<TDispatchTstackBuildRecord> {
        with(TDispatchTstackBuild.T_DISPATCH_TSTACK_BUILD) {
            return dslContext.selectFrom(this)
                    .where(BUILD_ID.eq(buildId))
                    .fetch()
        }
    }

    fun listRunningBuilds(dslContext: DSLContext): Result<TDispatchTstackBuildRecord> {
        with(TDispatchTstackBuild.T_DISPATCH_TSTACK_BUILD) {
            return dslContext.selectFrom(this)
                    .where(STATUS.eq(PipelineTaskStatus.RUNNING.status))
                    .fetch()
        }
    }

    fun getBuild(
        dslContext: DSLContext,
        buildId: String,
        vmSeqId: String
    ): TDispatchTstackBuildRecord? {
        with(TDispatchTstackBuild.T_DISPATCH_TSTACK_BUILD) {
            return dslContext.selectFrom(this)
                    .where(BUILD_ID.eq(buildId))
                    .and(VM_SEQ_ID.eq(vmSeqId))
                    .fetchOne()
        }
    }
}