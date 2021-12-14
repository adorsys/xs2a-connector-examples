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
import org.springframework.core.io.Resource;

public interface FileManagementService {
    /**
     * Stores the file and returns its identifier for further access
     * @param resource is a Resource representation of input data to be stored
     * @param filename is a name of file to be saved
     * @return download link being used for this file retrieving
     * @throws FileManagementException in case of errors during file creation and data writing
     */
    String saveFileAndBuildDownloadLink(Resource resource, String filename) throws FileManagementException;

    /**
     * Returns file by its downloadLink, returned after execution of `saveFileAndBuildDownloadLink(Resource resource, String filename)` method
     * @param downloadLink is an identifier of requested file
     * @return Resource as a representation of a requested file
     * @throws FileManagementException in case of error during file reading or retrieving
     */
    Resource getFileByDownloadLink(String downloadLink) throws FileManagementException;

    /**
     * Deletes file by downloadLink
     * @param downloadLink is an identifier of the file to be deleted.
     */
    void deleteFileByDownloadLink(String downloadLink);
}
