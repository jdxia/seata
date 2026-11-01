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
package org.apache.seata.learning.cloud.service;

import java.util.UUID;

import org.apache.seata.core.context.RootContext;
import org.apache.seata.learning.cloud.controller.StockClient;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Keeps the local insert and the remote stock deduction in one global transaction.
 */
@Service
public class OrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderService.class);

    private final JdbcTemplate jdbcTemplate;
    private final StockClient stockClient;

    public OrderService(JdbcTemplate jdbcTemplate, StockClient stockClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.stockClient = stockClient;
    }

    @GlobalTransactional(name = "cloud-order-demo", timeoutMills = 300000, rollbackFor = Exception.class)
    public OrderResult create(boolean rollback) {
        String xid = RootContext.getXID();
        String orderId = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO learning_order (id, product_id, quantity) VALUES (?, 1, 1)", orderId);
        StockClient.StockResult stock = stockClient.deduct();
        if (xid == null || !xid.equals(stock.xid())) {
            throw new IllegalStateException("The stock provider did not join the same global transaction");
        }
        LOGGER.info("Create order={}, xid={}, stockXid={}", orderId, xid, stock.xid());
        if (rollback) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Requested demo rollback");
        }
        return new OrderResult(orderId, xid, stock.xid(), stock.available());
    }

    public int count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM learning_order", Integer.class);
    }

    public record OrderResult(String orderId, String xid, String stockXid, int available) {
    }
}
