package com.tencent.devops.image.resources

import com.tencent.devops.common.api.exception.OperationException
import com.tencent.devops.common.api.exception.ParamBlankException
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.image.api.UserImageResource
import com.tencent.devops.image.pojo.DockerRepo
import com.tencent.devops.image.pojo.DockerTag
import com.tencent.devops.image.pojo.ImagePageData
import com.tencent.devops.image.pojo.UploadImageTask
import com.tencent.devops.image.service.ImageArtifactoryService
import com.tencent.devops.image.service.ImportImageService
import com.tencent.devops.image.utils.FileStoreUtils
import com.tencent.devops.image.utils.ImageFileUtils
import org.glassfish.jersey.media.multipart.FormDataContentDisposition
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.io.InputStream

@RestResource
class UserImageResourceImpl @Autowired constructor(
    private val importImageService: ImportImageService,
    private val artifactoryService: ImageArtifactoryService
) : UserImageResource {
    companion object {
        private val logger = LoggerFactory.getLogger(UserImageResourceImpl::class.java)
    }

    override fun upload(userId: String, projectId: String, isBuildImage: Boolean?, inputStream: InputStream, disposition: FormDataContentDisposition): Result<UploadImageTask> {
        checkUserAndProject(userId, projectId)
        if (!importImageService.checkDeployPermission(projectId, userId)) {
            throw OperationException("用户($userId)没有上传镜像权限")
        }

        var taskId: String
        try {
            taskId = FileStoreUtils.storeFile(inputStream)
        } catch (e: Exception) {
            logger.error("save image file failed", e)
            throw RuntimeException("文件保存失败：${e.message}")
        }

        try {
            val imageFilePath = FileStoreUtils.getFullFileName(taskId)
            val images = ImageFileUtils.parseImageMeta(imageFilePath)
            if (images.isEmpty()) {
                throw OperationException("镜像文件有效镜像个数为零")
            }

            val task = importImageService.importImage(projectId, userId, taskId, isBuildImage == true)
            return Result(task)
        } catch (e: Exception) {
            logger.error("update load image failed", e)
            throw RuntimeException("update load image failed")
        }
    }

    override fun queryUploadTask(userId: String, projectId: String, taskId: String): Result<UploadImageTask?> {
        checkUserAndProject(userId, projectId)
        return Result(importImageService.queryTask(taskId, projectId))
    }

    override fun listPublicImages(userId: String, searchKey: String?, start: Int?, limit: Int?): Result<ImagePageData> {
        checkUser(userId)

        val vSearchKey = searchKey ?: ""
        val vStart = if (start == null || start == 0) 0 else start
        val vLimit = if (limit == null || limit == 0) 10000 else limit

        return try {
            Result(artifactoryService.listPublicImages(vSearchKey, vStart, vLimit))
        } catch (e: Exception) {
            logger.error("list public image failed", e)
            throw RuntimeException("list public image failed")
        }
    }

    override fun listProjectImages(userId: String, projectId: String, searchKey: String?, start: Int?, limit: Int?): Result<ImagePageData> {
        checkUserAndProject(userId, projectId)

        val vSearchKey = searchKey ?: ""
        val vStart = if (start == null || start == 0) 0 else start
        val vLimit = if (limit == null || limit == 0) 10000 else limit

        return try {
            Result(artifactoryService.listProjectImages(projectId, vSearchKey, vStart, vLimit))
        } catch (e: Exception) {
            logger.error("list project image failed", e)
            throw RuntimeException("list project image failed")
        }
    }

    override fun listProjectBuildImages(userId: String, projectId: String, searchKey: String?, start: Int?, limit: Int?): Result<ImagePageData> {
        val vSearchKey = searchKey ?: ""
        val vStart = if (start == null || start == 0) 0 else start
        val vLimit = if (limit == null || limit == 0) 10000 else limit

        try {
            return Result(artifactoryService.listProjectBuildImages(projectId, vSearchKey, vStart, vLimit))
        } catch (e: Exception) {
            logger.error("list project build image failed", e)
            throw RuntimeException("list project build image failed")
        }
    }

    override fun listDockerBuildImages(userId: String, projectId: String): Result<List<DockerTag>> {
        checkUserAndProject(userId, projectId)

        try {
            return Result(artifactoryService.listDockerBuildImages(projectId))
        } catch (e: Exception) {
            logger.error("list docker build image failed", e)
            throw RuntimeException("list docker build image failed")
        }
    }

    override fun listDevCloudImages(userId: String, projectId: String, public: Boolean): Result<List<DockerTag>> {
        checkUserAndProject(userId, projectId)

        try {
            return Result(artifactoryService.listDevCloudImages(projectId, public))
        } catch (e: Exception) {
            logger.error("list dev cloud image failed", e)
            throw RuntimeException("list dev cloud image failed")
        }
    }

    override fun getImageInfo(userId: String, imageRepo: String, tagStart: Int?, tagLimit: Int?): Result<DockerRepo?> {
        if (imageRepo.isBlank()) {
            throw OperationException("imageRepo required")
        }

        val vStart = if (tagStart == null || tagStart == 0) 0 else tagStart
        val vLimit = if (tagLimit == null || tagLimit == 0) 1000 else tagLimit

        return try {
            Result(artifactoryService.getImageInfo(imageRepo, true, vStart, vLimit))
        } catch (e: Exception) {
            logger.error("get image info failed", e)
            throw RuntimeException("get image info failed")
        }
    }

    override fun getTagInfo(userId: String, imageRepo: String, imageTag: String): Result<DockerTag?> {
        if (imageRepo.isBlank()) {
            throw OperationException("imageRepo required")
        }
        if (imageTag.isBlank()) {
            throw OperationException("imageTag required")
        }

        try {
            return Result(artifactoryService.getTagInfo(imageRepo, imageTag))
        } catch (e: Exception) {
            logger.error("get image tag failed", e)
            throw RuntimeException("get image tag failed")
        }
    }

    override fun setBuildImage(userId: String, projectId: String, imageRepo: String, imageTag: String): Result<Boolean> {
        if (imageRepo.isBlank()) {
            throw OperationException("imageRepo required")
        }
        if (imageTag.isBlank()) {
            throw OperationException("imageTag required")
        }

        return try {
            Result(artifactoryService.copyToBuildImage(projectId, imageRepo, imageTag))
        } catch (e: OperationException) {
            Result(1, e.message!!)
        }
    }

    private fun checkUser(userId: String) {
        if (userId.isBlank()) {
            throw ParamBlankException("userId required")
        }
    }

    private fun checkUserAndProject(userId: String, projectId: String) {
        if (projectId.isBlank()) {
            throw ParamBlankException("projectId required")
        }
        if (userId.isBlank()) {
            throw ParamBlankException("userId required")
        }
    }
}