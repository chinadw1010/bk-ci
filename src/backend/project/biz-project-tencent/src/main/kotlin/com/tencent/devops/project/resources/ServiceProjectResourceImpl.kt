package com.tencent.devops.project.resources

import com.tencent.devops.common.web.RestResource
import com.tencent.devops.project.api.service.ServiceProjectResource
import com.tencent.devops.project.pojo.ProjectCreateInfo
import com.tencent.devops.project.pojo.ProjectVO
import com.tencent.devops.project.pojo.Result
import com.tencent.devops.project.service.ProjectPermissionService
import com.tencent.devops.project.service.ProjectService
import org.springframework.beans.factory.annotation.Autowired

@RestResource
class ServiceProjectResourceImpl @Autowired constructor(
        private val projectService: ProjectService,
        private val projectPermissionService: ProjectPermissionService
) : ServiceProjectResource {
    override fun getProjectEnNamesByOrganization(
        userId: String,
        bgId: Long,
        deptName: String?,
        centerName: String?
    ): Result<List<String>> {
        return Result(
            projectService.getProjectEnNamesByOrganization(
                userId = userId,
                bgId = bgId,
                deptName = deptName,
                centerName = centerName,
                interfaceName = "/service/projects/enNames/organization"
            )
        )
    }

    override fun getProjectByGroup(
        userId: String,
        bgName: String?,
        deptName: String?,
        centerName: String
    ): Result<List<ProjectVO>> {
        return Result(projectService.getProjectByGroup(userId, bgName, deptName, centerName))
    }

    override fun getProjectByUser(userName: String): Result<List<ProjectVO>> {
        return Result(projectService.getProjectByUser(userName))
    }

    override fun verifyUserProjectPermission(
        accessToken: String,
        projectCode: String,
        userId: String
    ): Result<Boolean> {
        return Result(projectPermissionService.verifyUserProjectPermission(accessToken, projectCode, userId))
    }

    override fun list(accessToken: String): Result<List<ProjectVO>> {
        return Result(projectService.list(accessToken))
    }

    override fun listByProjectCode(projectCodes: Set<String>): Result<List<ProjectVO>> {
        return Result(projectService.list(projectCodes))
    }

    override fun getNameByCode(projectCodes: String): Result<HashMap<String, String>> {
        return Result(projectService.getNameByCode(projectCodes))
    }

    override fun get(englishName: String): Result<ProjectVO?> {
        return Result(projectService.getByEnglishName(englishName))
    }

    override fun getPreUserProject(userId: String, accessToken: String): Result<ProjectVO?> {
        return Result(projectService.getOrCreatePreProject(userId, accessToken))
    }

    override fun verifyUserProjectPermissionV2(accessToken: String, projectCode: String, userId: String): Result<Boolean> {
        return Result(projectPermissionService.verifyUserProjectPermission(accessToken, projectCode, userId))
    }

    override fun getV2(englishName: String): Result<ProjectVO?> {
        return Result(projectService.getByEnglishName(englishName))
    }

    override fun getPreUserProjectV2(userId: String, accessToken: String): Result<ProjectVO?> {
        return Result(projectService.getOrCreatePreProject(userId, accessToken))
    }

    override fun create(userId: String, projectCreateInfo: ProjectCreateInfo): Result<String> {
        return Result(projectService.create(userId, projectCreateInfo))
    }
}