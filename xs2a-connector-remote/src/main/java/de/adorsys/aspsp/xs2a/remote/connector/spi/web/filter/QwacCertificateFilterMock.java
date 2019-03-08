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

package de.adorsys.aspsp.xs2a.remote.connector.spi.web.filter;

import javax.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.adorsys.psd2.xs2a.service.validator.tpp.TppInfoHolder;
import de.adorsys.psd2.xs2a.service.validator.tpp.TppRoleValidationService;
import de.adorsys.psd2.xs2a.web.filter.QwacCertificateFilter;

/**
 * The intend of this class is to return a mock certificate, when we don't want
 * to enter manually everytime the qwac certificate in case of test.
 * launch it with the "mockspi" profile.
 */
@Profile("mockspi")
@Component
public class QwacCertificateFilterMock extends QwacCertificateFilter {

    public QwacCertificateFilterMock(TppRoleValidationService tppRoleValidationService, TppInfoHolder tppInfoHolder) {
		super(tppRoleValidationService, tppInfoHolder);
	}

	@Override
    public String getEncodedTppQwacCert(HttpServletRequest httpRequest) {

        return "-----BEGIN CERTIFICATE-----MIIEBTCCAu2gAwIBAgIDXdgBMA0GCSqGSIb3DQEBCwUAMIGUMQswCQYDVQQGEwJERTEPMA0GA1UECAwGSGVzc2VuMRIwEAYDVQQHDAlGcmFua2Z1cnQxFTATBgNVBAoMDEF1dGhvcml0eSBDQTELMAkGA1UECwwCSVQxITAfBgNVBAMMGEF1dGhvcml0eSBDQSBEb21haW4gTmFtZTEZMBcGCSqGSIb3DQEJARYKY2FAdGVzdC5kZTAeFw0xOTAzMDgxNTM5MzdaFw0xOTAzMjUxNjIwMDZaMHoxEzARBgNVBAMMCmRvbWFpbk5hbWUxDDAKBgNVBAoMA29yZzELMAkGA1UECwwCb3UxEDAOBgNVBAYTB0dlcm1hbnkxDzANBgNVBAgMBkJheWVybjESMBAGA1UEBwwJTnVyZW1iZXJnMREwDwYDVQRhDAgxMjM0NTk4NzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAI534+24aittlkgyvOyyiddxezKCjNTbevXPhf5kwXuJTncBAvkbVEhlhYORybC2KAbDtnE7dyEEVZRlS/5O70DVTyEHHbyXlwwf+nr0uoRWN55t0ZPcMCc1GnQBeAQJocYz4tqCyHqNgF9N/WPRsL/bC0ddAn1tW4yUmQAWqGEyfRJYRhJoOmeJRmavJbfLrMAvDH3yeUzcfN9dFweK8oyMaGIqVZaD3p0yfEE9DlvtIk4QQTLbKkaaTANPKpbyLCSZsevWnqeEDIhAtUfeTu38oaDSNqt9xox5dOulAKOKzGJKAU5mKRmo/YWySBvQyQIHv8d8NtRr8DhgwKU7eRUCAwEAAaN5MHcwdQYIKwYBBQUHAQMEaTBnBgYEAIGYJwIwXTBMMBEGBwQAgZgnAQEMBlBTUF9BUzARBgcEAIGYJwECDAZQU1BfUEkwEQYHBACBmCcBAwwGUFNQX0FJMBEGBwQAgZgnAQQMBlBTUF9JQwwEQXV0aAwHR2VybWFueTANBgkqhkiG9w0BAQsFAAOCAQEAojezdT1bZiaL+8CD1SN1cI5UrqnmHSacdTq6Ot966c8Nym/G59iGOJo4Umfu1Uba2KsKgv0ehoenY9fugoH+BEH31CNHfV1CoIZEcZDnYY7u1DrktZDAF9NkrI1E25ZX1sVwuyO6tdVJpakWATs7CbCHgQ1i8jDt34Ad69iRDThVR71wZgeTdDqQ3AEiheDlQMufCRiW6EFXC9oddl5bP9q5PM1/BSa/DkW9GYeD91SVIOxkVBOwa+I3Gbz8GS45Q87GDAXRlP4Hs+wz+9Nsgat2pcdSQgHGfrvK+1UT/3r/6zjaDthrkAb25YDOrHlgA6q1hfUniMDDGrzqnQDRyA==-----END CERTIFICATE-----";
    }
}
