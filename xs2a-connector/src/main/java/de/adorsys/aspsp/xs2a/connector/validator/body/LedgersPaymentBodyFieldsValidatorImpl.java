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

package de.adorsys.aspsp.xs2a.connector.validator.body;

import de.adorsys.aspsp.xs2a.connector.validator.body.config.LedgersValidationConfigImpl;
import de.adorsys.psd2.xs2a.web.validator.body.payment.handler.DefaultPaymentBodyFieldsValidatorImpl;
import de.adorsys.psd2.xs2a.web.validator.body.payment.handler.type.PaymentTypeValidatorContext;
import de.adorsys.psd2.xs2a.web.validator.body.raw.FieldExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LedgersPaymentBodyFieldsValidatorImpl extends DefaultPaymentBodyFieldsValidatorImpl {

    @Autowired
    public LedgersPaymentBodyFieldsValidatorImpl(PaymentTypeValidatorContext paymentTypeValidatorContext,
                                                 FieldExtractor fieldExtractor) {
        super(paymentTypeValidatorContext, fieldExtractor);
    }

    @Override
    public LedgersValidationConfigImpl createPaymentValidationConfig() {
        return new LedgersValidationConfigImpl();
    }
}
