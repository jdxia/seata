-- Licensed to the Apache Software Foundation (ASF) under one or more
-- contributor license agreements.  See the NOTICE file distributed with
-- this work for additional information regarding copyright ownership.
-- The ASF licenses this file to You under the Apache License, Version 2.0
-- (the "License"); you may not use this file except in compliance with
-- the License.  You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

CREATE DATABASE IF NOT EXISTS seata_learning_boot CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS seata_learning_cloud CHARACTER SET utf8mb4;

USE seata_learning_boot;

CREATE TABLE IF NOT EXISTS learning_stock (
    id BIGINT NOT NULL PRIMARY KEY,
    available INT NOT NULL
) ENGINE = InnoDB;

-- Preserve progress when the script is run again.
INSERT INTO learning_stock (id, available)
SELECT 1, 100 WHERE NOT EXISTS (SELECT 1 FROM learning_stock WHERE id = 1);

CREATE TABLE IF NOT EXISTS undo_log (
    branch_id BIGINT NOT NULL,
    xid VARCHAR(128) NOT NULL,
    context VARCHAR(128) NOT NULL,
    rollback_info LONGBLOB NOT NULL,
    log_status INT NOT NULL,
    log_created DATETIME(6) NOT NULL,
    log_modified DATETIME(6) NOT NULL,
    UNIQUE KEY ux_undo_log (xid, branch_id),
    KEY ix_log_created (log_created)
) ENGINE = InnoDB;

USE seata_learning_cloud;

CREATE TABLE IF NOT EXISTS learning_order (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS undo_log LIKE seata_learning_boot.undo_log;
