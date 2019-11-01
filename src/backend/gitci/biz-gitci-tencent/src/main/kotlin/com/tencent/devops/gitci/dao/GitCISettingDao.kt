package com.tencent.devops.gitci.dao

import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.gitci.pojo.EnvironmentVariables
import com.tencent.devops.gitci.pojo.GitRepositoryConf
import com.tencent.devops.model.gitci.tables.TRepositoryConf
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class GitCISettingDao {

    fun saveSetting(
        dslContext: DSLContext,
        conf: GitRepositoryConf,
        projectCode: String
    ) {
        with(TRepositoryConf.T_REPOSITORY_CONF) {
            dslContext.transaction { configuration ->
                val context = DSL.using(configuration)
                val record = context.selectFrom(this)
                        .where(URL.eq(conf.url))
                        .fetchOne()
                val now = LocalDateTime.now()
                if (record == null) {
                    context.insertInto(this,
                        ID,
                        NAME,
                        URL,
                        HOME_PAGE,
                        GIT_HTTP_URL,
                        GIT_SSH_URL,
                        ENABLE_CI,
                        BUILD_PUSHED_BRANCHES,
                        LIMIT_CONCURRENT_JOBS,
                        BUILD_PUSHED_PULL_REQUEST,
                        AUTO_CANCEL_BRANCH_BUILDS,
                        AUTO_CANCEL_PULL_REQUEST_BUILDS,
                        ENV,
                        CREATE_TIME,
                        UPDATE_TIME,
                        PROJECT_CODE
                        )
                        .values(
                            conf.gitProjectId,
                            conf.name,
                            conf.url,
                            conf.homepage,
                            conf.gitHttpUrl,
                            conf.gitSshUrl,
                            conf.enableCi,
                            conf.buildPushedBranches,
                            conf.limitConcurrentJobs,
                            conf.buildPushedPullRequest,
                            conf.autoCancelBranchBuilds,
                            conf.autoCancelPullRequestBuilds,
                            if (conf.env == null) { "" } else { JsonUtil.toJson(conf.env!!) },
                            LocalDateTime.now(),
                            LocalDateTime.now(),
                            projectCode
                        ).execute()
                } else {
                    context.update(this)
                        .set(ENABLE_CI, conf.enableCi)
                            .set(BUILD_PUSHED_BRANCHES, conf.buildPushedBranches)
                            .set(LIMIT_CONCURRENT_JOBS, conf.limitConcurrentJobs)
                            .set(BUILD_PUSHED_PULL_REQUEST, conf.buildPushedPullRequest)
                            .set(AUTO_CANCEL_BRANCH_BUILDS, conf.autoCancelBranchBuilds)
                            .set(AUTO_CANCEL_PULL_REQUEST_BUILDS, conf.autoCancelPullRequestBuilds)
                            .set(ENV, if (conf.env == null) { "" } else { JsonUtil.toJson(conf.env!!) })
                            .set(UPDATE_TIME, now)
                            .set(PROJECT_CODE, projectCode)
                            .where(ID.eq(conf.gitProjectId))
                            .execute()
                }
            }
        }
    }

    fun getSetting(dslContext: DSLContext, gitProjectId: Long): GitRepositoryConf? {
        with(TRepositoryConf.T_REPOSITORY_CONF) {
            val conf = dslContext.selectFrom(this)
                .where(ID.eq(gitProjectId))
                .fetchOne()
            if (conf == null) {
                return null
            } else {
                return GitRepositoryConf(
                        conf.id,
                        conf.name,
                        conf.url,
                        conf.homePage,
                        conf.gitHttpUrl,
                        conf.gitSshUrl,
                        conf.enableCi,
                        conf.buildPushedBranches,
                        conf.limitConcurrentJobs,
                        conf.buildPushedPullRequest,
                        conf.autoCancelBranchBuilds,
                        conf.autoCancelPullRequestBuilds,
                        if (conf.env.isNullOrBlank()) {
                            null
                        } else {
                            JsonUtil.getObjectMapper().readValue(conf.env) as List<EnvironmentVariables>
                        },
                        conf.createTime.timestampmilli(),
                        conf.updateTime.timestampmilli(),
                        conf.projectCode
                        )
            }
        }
    }

    /**
     * 启用或关闭CI项目
     */
    fun enableGitCI(dslContext: DSLContext, gitId: Long, enable: Boolean) {
        with(TRepositoryConf.T_REPOSITORY_CONF) {
            dslContext.update(this)
                .set(ENABLE_CI, enable)
                .where(ID.eq(gitId))
                .execute()
        }
    }
}