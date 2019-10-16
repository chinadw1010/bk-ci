USE devops_ci_teamwork_plugin;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for T_ISSUE_DEVELOPMENT_INFORMATION
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_ISSUE_DEVELOPMENT_INFORMATION` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `ISSUE_ID` bigint(20) DEFAULT NULL,
  `REPOSITORY_HASH_ID` varchar(256) NOT NULL,
  `REPOSITORY_ALIAS_NAME` varchar(32) NOT NULL,
  `BRANCH` varchar(256) NOT NULL,
  `pipelines` text,
  `CREATED_USER` varchar(64) NOT NULL,
  `CREATED_TIME` datetime NOT NULL,
  `IS_DELETE` bit(1) DEFAULT b'0',
  `PROJECT_ID` varchar(256) DEFAULT NULL,
  PRIMARY KEY (`ID`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

-- ----------------------------
-- Table structure for T_TEAMWORK_PLUGIN
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_TEAMWORK_PLUGIN` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `PLUGIN_ID` varchar(256) NOT NULL,
  `VERSION` varchar(32) NOT NULL,
  `PLUGIN_CLASS` varchar(256) NOT NULL,
  `PROVIDER` varchar(64) NOT NULL,
  `PLUGIN_PATH` varchar(256) NOT NULL,
  `CREATED_USER` varchar(64) NOT NULL,
  `CREATED_TIME` datetime NOT NULL,
  `IS_DELETE` bit(1) DEFAULT b'0',
  PRIMARY KEY (`ID`) USING BTREE,
  UNIQUE KEY `PLUGIN_ID` (`PLUGIN_ID`,`VERSION`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

-- ----------------------------
-- Table structure for T_TEAMWORK_PLUGIN_ENABLE
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_TEAMWORK_PLUGIN_ENABLE` (
  `PLUGIN_DB_ID` bigint(20) NOT NULL,
  `PLUGIN_ID` varchar(256) NOT NULL,
  `LATEST_VERSION` varchar(32) NOT NULL,
  `ENABLE_PROJECTS` mediumtext,
  PRIMARY KEY (`PLUGIN_ID`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

-- ----------------------------
-- Table structure for T_TEAMWORK_PLUGIN_PROJECT
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_TEAMWORK_PLUGIN_PROJECT` (
  `PLUGIN_DB_ID` bigint(20) NOT NULL,
  `PROJECT_ID` varchar(256) NOT NULL,
  `CREATED_USER` varchar(64) NOT NULL,
  `CREATED_TIME` datetime NOT NULL,
  PRIMARY KEY (`PLUGIN_DB_ID`,`PROJECT_ID`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;