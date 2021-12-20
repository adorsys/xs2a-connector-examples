/*
 * Copyright 2018-2021 adorsys GmbH & Co KG
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

package de.adorsys.aspsp.xs2a.connector.spi.file.util;

import de.adorsys.aspsp.xs2a.connector.spi.file.exception.FileManagementException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Slf4j
public class FileManagementServiceSimple implements FileManagementService {
    @Value("${xs2a.download.files.cleanup.delay_s:30}")
    public int deleteFileDelay;

    @Value("${xs2a.download.files.dir:/tmp/XS2A}")
    private String configurationPath;

    @Override
    public String saveFileAndBuildDownloadLink(Resource resource, String filename) throws FileManagementException {

        try {
            byte[] bytes = resource.getInputStream().readAllBytes();
            Path dirPath = Path.of(configurationPath);
            Files.createDirectories(dirPath);
            Path dir = Files.createTempDirectory(dirPath, StringUtils.EMPTY);
            Path fileToCreatePath = dir.resolve(filename);
            Path newFilePath = Files.createFile(fileToCreatePath);
            File file = newFilePath.toFile();

            WriteFileRunnable writeFileRunnable = new WriteFileRunnable(file, bytes);
            Thread asyncFileWrite = new Thread(writeFileRunnable);
            asyncFileWrite.start();

            log.info("Bytes read: [{}]", bytes.length);
            return file.getAbsolutePath();
        } catch (IOException e) {
            log.error("Save file and build Download Link failed (IOException): message {}, exception {}", e.getMessage(), e);
            throw new FileManagementException(e.getMessage());
        }
    }

    @Override
    public Resource getFileByDownloadLink(String downloadLink) throws FileManagementException {
        Path path = Path.of(downloadLink);
        if (path.toFile().exists()) {
            return new FileSystemResource(path);
        }
        log.error("File does not exist: [{}]", downloadLink);
        throw new FileManagementException("Requested file does not exist");
    }

    @Override
    public void deleteFileByDownloadLink(String downloadLink) {
        DeleteFileRunnable deleteFileRunnable = new DeleteFileRunnable(downloadLink, deleteFileDelay);
        Thread asyncFileDelete = new Thread(deleteFileRunnable);
        asyncFileDelete.start();
    }
}
