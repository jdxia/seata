/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.seata.learning.boot.service;

import org.apache.seata.core.context.RootContext;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Deducts one unit so commit and rollback have observable database effects.
 */
@Service
public class StockService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StockService.class);

    private final JdbcTemplate jdbcTemplate;

    public StockService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GlobalTransactional(name = "boot-stock-demo", timeoutMills = 300000, rollbackFor = Exception.class)
    public StockResult execute(boolean rollback) {
        StockResult result = deduct();
        if (rollback) {
            LOGGER.info("Request rollback, xid={}", result.xid());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Requested demo rollback");
        }
        return result;
    }

    public StockResult deduct() {
        String xid = RootContext.getXID();
        // Reject calls without propagation instead of silently committing a local transaction.
        if (xid == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A global transaction is required");
        }
        int updated = jdbcTemplate.update("UPDATE learning_stock SET available = available - 1 "
                + "WHERE id = 1 AND available > 0");
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Demo stock is exhausted");
        }
        LOGGER.info("Deduct stock, xid={}", xid);
        return new StockResult(xid, available());
    }

    public int available() {
        return jdbcTemplate.queryForObject("SELECT available FROM learning_stock WHERE id = 1", Integer.class);
    }

    public record StockResult(String xid, int available) {
    }
}
