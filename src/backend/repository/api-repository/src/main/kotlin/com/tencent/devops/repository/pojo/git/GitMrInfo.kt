package com.tencent.devops.repository.pojo.git

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.annotations.ApiModel

/*
* {
    "labels": [
        "label1"
    ],
    "id": 645665,
    "title": "dddd",
    "target_project_id": 64762,
    "target_branch": "master",
    "source_project_id": 64762,
    "source_branch": "branch2019090405",
    "state": "reopened",
    "merge_status": "can_be_merged",
    "iid": 30,
    "description": "dddd",
    "created_at": "2019-09-04T07:27:55+0000",
    "updated_at": "2019-09-04T08:31:21+0000",
    "assignee": {
        "id": 66212,
        "username": "v_kaizewu",
        "web_url": "http://git.code.oa.com/u/v_kaizewu",
        "name": "v_kaizewu",
        "state": "active",
        "avatar_url": "http://git.code.oa.com/assets/images/avatar/no_user_avatar.png"
    },
    "author": {
        "id": 16703,
        "username": "ddlin",
        "web_url": "http://git.code.oa.com/u/ddlin",
        "name": "ddlin",
        "state": "active",
        "avatar_url": "http://git.code.oa.com/assets/images/avatar/no_user_avatar.png"
    },
    "milestone": {
        "id": 3669,
        "project_id": 64762,
        "title": "Milestone1",
        "state": "active",
        "iid": 1,
        "due_date": "2019-09-02",
        "created_at": "2019-09-02T08:58:20+0000",
        "updated_at": "2019-09-02T08:58:20+0000",
        "description": ""
    },
    "necessary_reviewers": null,
    "suggestion_reviewers": null,
    "project_id": 64762,
    "work_in_progress": false,
    "downvotes": 0,
    "upvotes": 0
}
* */

@ApiModel("git mr信息")
data class GitMrInfo(
    val title: String = "",
    @JsonProperty("target_project_id")
    val targetProjectId: String = "",
    @JsonProperty("target_branch")
    val targetBranch: String? = "",
    @JsonProperty("source_project_id")
    val sourceProjectId: String? = "",
    @JsonProperty("source_branch")
    val sourceBranch: String? = "",
    @JsonProperty("created_at")
    val createTime: String? = "",
    @JsonProperty("updated_at")
    val updateTime: String? = "",
    @JsonProperty("iid")
    val mrNumber: String = "",
    @JsonProperty("id")
    val mrId: String = "",
    val labels: List<String>,
    val description: String? = "",
    val assignee: GitMrInfoAssignee? = null,
    val milestone: GitMrInfoMilestone? = null,
    val author: GitMrInfoAuthor = GitMrInfoAuthor()
) {
    data class GitMrInfoAssignee(
        @JsonProperty("id")
        val id: Int = 0,
        val username: String = "",
        @JsonProperty("web_url")
        val webUrl: String = "",
        @JsonProperty("avatar_url")
        val avatarUrl: String = ""
    )

    data class GitMrInfoMilestone(
        @JsonProperty("id")
        val id: Int = 0,
        @JsonProperty("title")
        val title: String = "",
        @JsonProperty("due_date")
        val dueDate: String = "",
        val description: String? = ""
    )

    data class GitMrInfoAuthor(
        @JsonProperty("id")
        val id: Int = 0,
        @JsonProperty("username")
        val username: String = "",
        @JsonProperty("web_url")
        val webUrl: String = "",
        @JsonProperty("title")
        val title: String = "",
        @JsonProperty("avatar_url")
        val avatarUrl: String = ""
    )
}