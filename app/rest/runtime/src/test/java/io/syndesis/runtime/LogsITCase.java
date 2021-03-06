/*
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.syndesis.runtime;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class LogsITCase extends BaseITCase {

    @Override
    @Before
    public void clearDB() {
        super.clearDB();
    }

    @Test
    @SuppressWarnings({"unchecked","rawtypes"})
    public void requestIntegrationLogs() throws IOException {
        jsondb.update("/", resource("logs-controller-db.json"));

        ResponseEntity<List> re = get("/api/v1/logs/my-integration", List.class);
        List<Object> response = re.getBody();
        assertThat(response.size()).isEqualTo(4);

    }

    private static String resource(String file) throws IOException {
        try (InputStream is = LogsITCase.class.getClassLoader().getResourceAsStream(file)) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            copy(is, os);
            return new String(os.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void copy(InputStream is, ByteArrayOutputStream os) throws IOException {
        int c;
        while( (c=is.read())>=0 ) {
            os.write(c);
        }
    }

}
