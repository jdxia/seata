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
package org.apache.seata.learning.boot;

import org.apache.seata.learning.boot.service.StockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Requires the learning schema and a real Seata server; no mocked transaction outcome.
 */
@EnabledIfSystemProperty(named = "learning.integration", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BootTransactionTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void shouldCommitAndRollBackStock() {
        int before = http.getForObject("/demo/stock", Integer.class);

        ResponseEntity<StockService.StockResult> committed = http.postForEntity(
                "/demo/transactions", null, StockService.StockResult.class);
        assertThat(committed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(committed.getBody().xid()).isNotBlank();
        assertThat(http.getForObject("/demo/stock", Integer.class)).isEqualTo(before - 1);

        ResponseEntity<String> rolledBack = http.postForEntity("/demo/transactions?rollback=true", null, String.class);
        assertThat(rolledBack.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(http.getForObject("/demo/stock", Integer.class)).isEqualTo(before - 1);
    }

    @Test
    void shouldRejectDeductionWithoutGlobalTransaction() {
        int before = http.getForObject("/demo/stock", Integer.class);
        assertThat(http.postForEntity("/demo/stock/deduct", null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.getForObject("/demo/stock", Integer.class)).isEqualTo(before);
    }
}
