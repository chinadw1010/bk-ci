package com.tencent.devops.scm.code.svn

import com.tencent.devops.common.api.enums.ScmType
import com.tencent.devops.common.api.exception.OperationException
import com.tencent.devops.common.service.utils.SpringContextUtil
import com.tencent.devops.scm.IScm
import com.tencent.devops.scm.code.svn.api.SVNApi
import com.tencent.devops.scm.config.SVNConfig
import com.tencent.devops.scm.exception.ScmException
import com.tencent.devops.scm.jmx.JMX
import com.tencent.devops.scm.pojo.RevisionInfo
import com.tencent.devops.scm.utils.code.svn.SvnUtils
import org.slf4j.LoggerFactory
import org.tmatesoft.svn.core.SVNAuthenticationException
import org.tmatesoft.svn.core.SVNDirEntry
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNLogEntry
import org.tmatesoft.svn.core.SVNNodeKind
import org.tmatesoft.svn.core.io.SVNRepository

class CodeSvnScmImpl constructor(
    override val projectName: String,
    override val branchName: String?,
    override val url: String,
    private val username: String,
    private val privateKey: String,
    private val passphrase: String?
) : IScm {
    private val svnConfig = SpringContextUtil.getBean(SVNConfig::class.java)

    override fun getLatestRevision(): RevisionInfo {
        val branch = branchName ?: "trunk"
        var success = false
        val svnBean = JMX.getSvnBean()
        try {
            svnBean.latestRevision()
            val repository = getRepository()
            val revision = if (!branchName.isNullOrBlank()) {
                val info: SVNDirEntry? = repository.info(branchName, -1)
                info?.revision ?: repository.latestRevision // 如果因为路径错误导致找不到的话，使用整个仓库的最新版本号
            } else {
                repository.latestRevision
            }
            val updatedMessage = getCommitMessage(repository, revision)
            success = true
            return RevisionInfo(
                revision.toString(),
                updatedMessage,
                branch
            )
        } catch (e: SVNAuthenticationException) {
            if ((!e.message.isNullOrBlank()) && e.message!!.contains("timeout")) {
                svnBean.latestRevisionTimeout()
            }
            throw e
        } catch (e: SVNException) {
            if ((!e.message.isNullOrBlank()) && e.message!!.contains("There was a problem while connecting")) {
                svnBean.latestRevisionTimeout()
            }
            throw e
        } finally {
            if (!success) {
                svnBean.latestRevisionFail()
            }
        }
    }

    override fun getBranches(): List<String> {
        val repository = getRepository()
        val branchNames = ArrayList<String>()
        branchNames.add("trunk")
        branchNames.addAll(getBranchNames(repository))
        return branchNames
    }

    override fun getTags(): List<String> {
        throw RuntimeException("SVN not support get tags")
    }

    override fun checkTokenAndPrivateKey() {
        try {
            getLatestRevision()
        } catch (t: Throwable) {
            logger.warn("Fail to check the svn latest revision", t)
            throw RuntimeException("SVN 私钥不正确 或者 SVN 路径没有权限")
        }
    }

    override fun checkTokenAndUsername() {
        try {
            getLatestRevision()
        } catch (t: Throwable) {
            logger.warn("Fail to check the svn latest revision", t)
            throw RuntimeException("SVN 私钥不正确 或者 SVN 路径没有权限")
        }
    }

    /**
     * curl -XGET --header "apiKey: 802c80e122304505be073e1f29b8bf2c" "http://code.oa.com/rest/webhooks?event=1&apiKey=802c80e122304505be073e1f29b8bf2c&svnUrl=http://sh-svn.tencent.com/sodash/rdeng_svn_single_external_proj"
     * curl -XPOST --header "apiKey: 802c80e122304505be073e1f29b8bf2c" "http://code.oa.com/rest/webhooks?event=1&apiKey=802c80e122304505be073e1f29b8bf2c&svnUrl=http://sh-svn.tencent.com/sodash/rdeng_svn_single_external_proj&url=http://test.gw.open.oa.com/external/scm/codesvn/commit,http://gw.open.oa.com/external/scm/codesvn/commit&userName=rdeng"
     */
    override fun addWebHook(hookUrl: String) {
        logger.info("Start to add the webhook for the repo $projectName")
        try {
            val hooks = SVNApi.getWebhooks(svnConfig, url)
            val addHooks = if (hooks.isEmpty()) {
                hookUrl
            } else {
                if (hooks.contains(hookUrl)) {
                    logger.info("The hook url is already exist, ignore")
                    return
                }
                logger.info("Get the exist hooks - ($hooks)")

                val result = StringBuilder()
                hooks.forEach {
                    result.append(it).append(",")
                }
                result.append(hookUrl)
                result.toString()
            }
            logger.info("Adding the svn webhooks($addHooks)")
            SVNApi.addWebhooks(svnConfig, username, url, addHooks)
        } catch (e: Exception) {
            logger.warn("Fail to add the webhook", e)
            throw OperationException("添加SVN WEB hook 失败")
        }
    }

    override fun addCommitCheck(commitId: String, state: String, targetUrl: String, context: String, description: String, block: Boolean) {
    }

    override fun addMRComment(mrId: Long, comment: String) {
    }

    // curl -XPOST -H "ApiKey: 802c80e122304505be073e1f29b8bf2c" -H "Content-type: application/json" -d '{"repname":"iedbk","applicant":"johuang","subpath":["bluekingCI_proj/trunk/project_example"]}' http://code.oa.com/rest/svn/lock
    override fun lock(repname: String, applicant: String, subpath: String) {
        logger.info("Start to lock the repo $repname")
        try {
            // TODO check if already locked

            SVNApi.lock(repname, applicant, subpath, svnConfig)
        } catch (e: Exception) {
            logger.warn("Fail to lock the repo:$repname", e)
            throw OperationException("lock失败")
        }
    }

    override fun unlock(repname: String, applicant: String, subpath: String) {
        logger.info("Start to unlock the repo $repname")
        try {
            SVNApi.unlock(repname, applicant, subpath, svnConfig)
        } catch (e: Exception) {
            logger.warn("Fail to unlock the repo:$repname", e)
            throw OperationException("unlock失败")
        }
    }

    private fun getBranchNames(repository: SVNRepository): Set<String> {
        try {
            val nodeKind = repository.checkPath("branches", repository.latestRevision)
            return if (nodeKind === SVNNodeKind.DIR) {
                val dirEntries = HashSet<SVNDirEntry>()
                repository.getDir("branches", repository.latestRevision, false, dirEntries)
                dirEntries.filter {
                    it.kind == SVNNodeKind.DIR
                }.map {
                    it.name
                }.toSet()
            } else {
                setOf()
            }
        } catch (e: SVNException) {
            if (e.errorMessage.errorCode.isAuthentication) {
                throw ScmException("代码仓库访问未授权", ScmType.CODE_SVN.name)
            } else {
                logger.error("工程($projectName)获取分支失败", e)
                throw ScmException("代码仓库访问异常", ScmType.CODE_SVN.name)
            }
        }
    }

    private fun getRepository(): SVNRepository {

        try {
            return SvnUtils.getRepository(url, username, privateKey, passphrase)
        } catch (e: SVNException) {
            logger.error("工程($projectName)本地仓库创建失败", e)
            throw ScmException("代码仓库访问异常", ScmType.CODE_SVN.name)
        }
    }

    private fun getCommitMessage(svnRepository: SVNRepository, revision: Long): String {
        try {
            val collection = svnRepository.log(arrayOf(""), null, revision, revision, true, true)
            val sb = StringBuilder()
            if (!collection.isEmpty()) {
                for (aCollection in collection) {
                    val logEntry = aCollection as SVNLogEntry
                    sb.append("Revision:")
                    sb.append(logEntry.revision)
                    sb.append("\r\n")
                    sb.append(logEntry.message)
                    sb.append("\r\n")
                }
            }
            return sb.toString()
        } catch (e: SVNException) {
            logger.error("获取工程($projectName})版本更新日志失败", e)
            throw ScmException("代码仓库访问异常", ScmType.CODE_SVN.name)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CodeSvnScmImpl::class.java)
    }
}