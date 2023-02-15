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

package de.adorsys.aspsp.xs2a.connector.spi.file.util;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.file.Path;

@Slf4j
@AllArgsConstructor
@SuppressWarnings("PMD.ShortMethodName")
public class DeleteFileRunnable implements Runnable {
    private final String downloadLink;
    private final int deleteFileDelay;

    @Override
    public void run() {
        Path filePath = Path.of(downloadLink);
        File file = new File(filePath.toString());

        Path parentDirectoryPath = filePath.getParent();
        File parentDirectory = new File(parentDirectoryPath.toString());

        try {
            log.info("File {} is scheduled to be deleted in {} seconds", filePath, deleteFileDelay);
            Thread.sleep(deleteFileDelay * 1000L);
            FileUtils.deleteQuietly(file);
            log.info("File deleted. File list in directory {} before deleting: {}", parentDirectoryPath, parentDirectory.list());
            log.info("Directory {} is scheduled to be deleted in {} seconds", parentDirectoryPath, deleteFileDelay);
            Thread.sleep(deleteFileDelay * 1000L);
            FileUtils.deleteDirectory(parentDirectory);
            log.info("Directory deleted: {}", parentDirectoryPath);
        } catch (Exception e) {
            log.error("Delete file by Download Id failed (IOException): Decrypted Download id: [{}], message {}, exception: {}", downloadLink, e.getMessage(), e);
        }
    }
}
