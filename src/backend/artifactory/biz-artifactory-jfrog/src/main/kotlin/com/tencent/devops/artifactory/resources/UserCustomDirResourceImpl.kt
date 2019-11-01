package com.tencent.devops.artifactory.resources

import com.tencent.devops.artifactory.api.UserCustomDirResource
import com.tencent.devops.artifactory.pojo.CombinationPath
import com.tencent.devops.artifactory.pojo.PathList
import com.tencent.devops.artifactory.pojo.PathPair
import com.tencent.devops.artifactory.service.CustomDirService
import com.tencent.devops.common.api.exception.ParamBlankException
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.RestResource
import org.glassfish.jersey.media.multipart.FormDataContentDisposition
import org.springframework.beans.factory.annotation.Autowired
import java.io.InputStream

@RestResource
class UserCustomDirResourceImpl @Autowired constructor(val customDirService: CustomDirService) : UserCustomDirResource {
    override fun deploy(
        userId: String,
        projectId: String,
        path: String,
        inputStream: InputStream,
        disposition: FormDataContentDisposition
    ): Result<Boolean> {
        checkParam(userId, projectId, path)
        customDirService.deploy(userId, projectId, path, inputStream, disposition)
        return Result(true)
    }

    override fun mkdir(userId: String, projectId: String, path: String): Result<Boolean> {
        checkParam(userId, projectId, path)
        customDirService.mkdir(userId, projectId, path)
        return Result(true)
    }

    override fun rename(userId: String, projectId: String, pathPair: PathPair): Result<Boolean> {
        checkParam(userId, projectId)
        customDirService.rename(userId, projectId, pathPair.srcPath, pathPair.destPath)
        return Result(true)
    }

    override fun copy(userId: String, projectId: String, combinationPath: CombinationPath): Result<Boolean> {
        checkParam(userId, projectId)
        customDirService.copy(userId, projectId, combinationPath)
        return Result(true)
    }

    override fun move(userId: String, projectId: String, combinationPath: CombinationPath): Result<Boolean> {
        checkParam(userId, projectId)
        customDirService.move(userId, projectId, combinationPath)
        return Result(true)
    }

    override fun delete(userId: String, projectId: String, pathList: PathList): Result<Boolean> {
        checkParam(userId, projectId)
        customDirService.delete(userId, projectId, pathList)
        return Result(true)
    }

    private fun checkParam(userId: String, projectId: String) {
        if (userId.isBlank()) {
            throw ParamBlankException("Invalid userId")
        }
        if (projectId.isBlank()) {
            throw ParamBlankException("Invalid projectId")
        }
    }

    private fun checkParam(userId: String, projectId: String, path: String) {
        if (userId.isBlank()) {
            throw ParamBlankException("Invalid userId")
        }
        if (projectId.isBlank()) {
            throw ParamBlankException("Invalid projectId")
        }
        if (path.isBlank()) {
            throw ParamBlankException("Invalid path")
        }
    }
}