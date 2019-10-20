package com.tencent.devops.experience.resources.app

import com.tencent.devops.common.api.exception.ParamBlankException
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.experience.api.app.AppExperienceResource
import com.tencent.devops.experience.pojo.AppExperience
import com.tencent.devops.experience.pojo.AppExperienceDetail
import com.tencent.devops.experience.pojo.AppExperienceSummary
import com.tencent.devops.experience.pojo.DownloadUrl
import com.tencent.devops.experience.pojo.ExperienceCreate
import com.tencent.devops.experience.pojo.ProjectGroupAndUsers
import com.tencent.devops.experience.service.ExperienceAppService
import com.tencent.devops.experience.service.ExperienceService
import com.tencent.devops.experience.service.GroupService
import org.springframework.beans.factory.annotation.Autowired

@RestResource
class AppExperienceResourceImpl @Autowired constructor(
    private val experienceAppService: ExperienceAppService,
    private val experienceService: ExperienceService,
    private val groupService: GroupService
) : AppExperienceResource {
    override fun list(userId: String, page: Int?, pageSize: Int?): Result<List<AppExperience>> {
        checkParam(userId)
        val pageNotNull = page ?: 0
        val pageSizeNotNull = pageSize ?: -1
        val offset = if (pageSizeNotNull == -1) 0 else (pageNotNull - 1) * pageSizeNotNull
        val result = experienceAppService.list(userId, offset, pageSizeNotNull)
        return Result(result)
    }

    override fun detail(userId: String, experienceHashId: String): Result<AppExperienceDetail> {
        checkParam(userId, experienceHashId)
        val result = experienceAppService.detail(userId, experienceHashId)
        return Result(result)
    }

    override fun downloadUrl(userId: String, experienceHashId: String): Result<DownloadUrl> {
        checkParam(userId, experienceHashId)
        val result = experienceAppService.downloadUrl(userId, experienceHashId)
        return Result(result)
    }

    override fun history(userId: String, projectId: String): Result<List<AppExperienceSummary>> {
        checkParam(userId)
        if (projectId.isBlank()) {
            throw ParamBlankException("Invalid projectId")
        }
        val result = experienceAppService.history(userId, projectId)
        return Result(result)
    }

    override fun projectGroupAndUsers(userId: String, projectId: String): Result<List<ProjectGroupAndUsers>> {
        checkParam(userId)
        if (projectId.isBlank()) {
            throw ParamBlankException("Invalid projectId")
        }
        val result = groupService.getProjectGroupAndUsers(userId, projectId)
        return Result(result)
    }

    override fun creat(userId: String, projectId: String, experience: ExperienceCreate): Result<Boolean> {
        checkParam(userId, projectId)
        experienceService.create(userId, projectId, experience)
        return Result(true)
    }

    fun checkParam(userId: String) {
        if (userId.isBlank()) {
            throw ParamBlankException("Invalid userId")
        }
    }

    fun checkParam(userId: String, experienceHashId: String) {
        if (userId.isBlank()) {
            throw ParamBlankException("Invalid userId")
        }
        if (experienceHashId.isBlank()) {
            throw ParamBlankException("Invalid experienceHashId")
        }
    }
}