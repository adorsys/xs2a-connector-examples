/*
 * Copyright 2018-2023 adorsys GmbH & Co KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.adorsys.aspsp.xs2a.connector.spi.file;

import de.adorsys.aspsp.xs2a.connector.spi.file.util.FileManagementService;
import de.adorsys.aspsp.xs2a.connector.spi.file.util.FileManagementServiceSimple;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TransactionsFileManagementServiceSimpleTest {
    private final FileManagementService fileManagementService = new FileManagementServiceSimple();

    @Test
    void allMethodsFlow() throws IOException, InterruptedException {
        ReflectionTestUtils.setField(fileManagementService, "configurationPath", "/tmp/XS2A");
        String inputFileName = "json/spi/impl/spi-transactions.json";
        String outputFileName = "transactions.json";
        String path = getClass().getClassLoader().getResource(inputFileName).getPath();
        Resource resource = new FileSystemResource(path);
        byte[] bytes = resource.getInputStream().readAllBytes();

        //Save file and build download link
        String downloadLink = fileManagementService.saveFileAndBuildDownloadLink(resource, outputFileName);
        assertNotNull(downloadLink);
        //Delay to ensure the file is written
        Thread.sleep(100);

        //Get file by download link
        Resource fileByDownloadLink = fileManagementService.getFileByDownloadLink(downloadLink);
        assertEquals(outputFileName, fileByDownloadLink.getFilename());
        assertArrayEquals(bytes, fileByDownloadLink.getInputStream().readAllBytes());

        //Delete file success (file exist)
        Assertions.assertDoesNotThrow(() -> fileManagementService.deleteFileByDownloadLink(downloadLink));
    }
}
