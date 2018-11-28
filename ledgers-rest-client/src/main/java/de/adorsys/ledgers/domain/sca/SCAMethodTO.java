/*
 * Copyright 2018-2018 adorsys GmbH & Co KG
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

package de.adorsys.ledgers.domain.sca;

import java.util.Objects;

public class SCAMethodTO {
    private SCAMethodTypeTO scaMethod;
    private String methodValue;

    public SCAMethodTypeTO getScaMethod() {
        return scaMethod;
    }

    public void setScaMethod(SCAMethodTypeTO scaMethod) {
        this.scaMethod = scaMethod;
    }

    public String getMethodValue() {
        return methodValue;
    }

    public void setMethodValue(String methodValue) {
        this.methodValue = methodValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SCAMethodTO that = (SCAMethodTO) o;
        return scaMethod == that.scaMethod &&
                       Objects.equals(methodValue, that.methodValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scaMethod, methodValue);
    }

    @Override
    public String toString() {
        return "SCAMethodTO{" +
                       "scaMethod=" + scaMethod +
                       ", methodValue='" + methodValue + '\'' +
                       '}';
    }
}


