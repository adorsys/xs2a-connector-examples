package de.adorsys.ledgers.xs2a.api.client;

import de.adorsys.ledgers.xs2a.api.utils.RemoteURLs;
import de.adorsys.psd2.client.model.*;
import de.adorsys.psd2.model.*;
import de.adorsys.psd2.model.Authorisations;
import de.adorsys.psd2.model.CancellationList;
import de.adorsys.psd2.model.PaymentInitationRequestResponse201;
import de.adorsys.psd2.model.PaymentInitiationStatusResponse200Json;
import de.adorsys.psd2.model.ScaStatusResponse;
import de.adorsys.psd2.model.StartScaprocessResponse;
import de.adorsys.psd2.model.UpdatePsuAuthenticationResponse;
import io.swagger.annotations.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.UUID;

@FeignClient(value = "paymentApiClient", url = RemoteURLs.XS2A_URL, path = "/")
public interface PaymentApiClient {
    @ApiOperation(value = "Payment Cancellation Request", nickname = "cancelPayment", notes = "This method initiates the cancellation of a payment. Depending on the payment-service, the payment-product and the ASPSP's implementation, this TPP call might be sufficient to cancel a payment. If an authorisation of the payment cancellation is mandated by the ASPSP, a corresponding hyperlink will be contained in the response message.  Cancels the addressed payment with resource identification paymentId if applicable to the payment-service, payment-product and received in product related timelines (e.g. before end of business day for scheduled payments of the last business day before the scheduled execution day).  The response to this DELETE command will tell the TPP whether the   * access method was rejected   * access method was successful, or   * access method is generally applicable, but further authorisation processes are needed. ", response = PaymentInitiationCancelResponse200202.class, authorizations = {
            @Authorization(value = "BearerAuthOAuth")
    }, tags = {"Payment Initiation Service (PIS)",})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = PaymentInitiationCancelResponse200202.class),
            @ApiResponse(code = 202, message = "Received", response = PaymentInitiationCancelResponse200202.class),
            @ApiResponse(code = 400, message = "Bad Request", response = TppMessages400.class),
            @ApiResponse(code = 401, message = "Unauthorized", response = TppMessages401.class),
            @ApiResponse(code = 403, message = "Forbidden", response = TppMessages403.class),
            @ApiResponse(code = 404, message = "Not found", response = TppMessages404.class),
            @ApiResponse(code = 405, message = "Method Not Allowed", response = TppMessages405.class),
            @ApiResponse(code = 406, message = "Not Acceptable", response = TppMessages406.class),
            @ApiResponse(code = 408, message = "Request Timeout"),
            @ApiResponse(code = 415, message = "Unsupported Media Type"),
            @ApiResponse(code = 429, message = "Too Many Requests", response = TppMessages429.class),
            @ApiResponse(code = 500, message = "Internal Server Error"),
            @ApiResponse(code = 503, message = "Service Unavailable")})
    @RequestMapping(value = "/v1/{payment-service}/{paymentId}",
            produces = {"application/json"},
            method = RequestMethod.DELETE)
    ResponseEntity<PaymentInitiationCancelResponse200202> _cancelPayment(
            @ApiParam(value = "Payment service:  Possible values are: * payments * bulk-payments * periodic-payments ", required = true, allowableValues = "\"payments\", \"bulk-payments\", \"periodic-payments\"") @PathVariable("payment-service") String paymentService,
            @ApiParam(value = "Resource identification of the generated payment initiation resource.", required = true) @PathVariable("paymentId") String paymentId,
            @ApiParam(value = "ID of the request, unique to the call, as determined by the initiating party.", required = true) @RequestHeader(value = "X-Request-ID", required = true) UUID xRequestID,
            @ApiParam(value = "Is contained if and only if the \"Signature\" element is contained in the header of the request.") @RequestHeader(value = "Digest", required = false) String digest,
            @ApiParam(value = "A signature of the request by the TPP on application level. This might be mandated by ASPSP. ") @RequestHeader(value = "Signature", required = false) String signature,
            @ApiParam(value = "The certificate used for signing the request, in base64 encoding. Must be contained if a signature is contained. ") @RequestHeader(value = "TPP-Signature-Certificate", required = false) byte[] tpPSignatureCertificate,
            @ApiParam(value = "The forwarded IP Address header field consists of the corresponding http request IP Address field between PSU and TPP. ") @RequestHeader(value = "PSU-IP-Address", required = false) String psUIPAddress,
            @ApiParam(value = "The forwarded IP Port header field consists of the corresponding HTTP request IP Port field between PSU and TPP, if available. ") @RequestHeader(value = "PSU-IP-Port", required = false) Object psUIPPort,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept", required = false) String psUAccept,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Charset", required = false) String psUAcceptCharset,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Encoding", required = false) String psUAcceptEncoding,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Language", required = false) String psUAcceptLanguage,
            @ApiParam(value = "The forwarded Agent header field of the HTTP request between PSU and TPP, if available. ") @RequestHeader(value = "PSU-User-Agent", required = false) String psUUserAgent,
            @ApiParam(value = "HTTP method used at the PSU ? TPP interface, if available. Valid values are: * GET * POST * PUT * PATCH * DELETE ", allowableValues = "GET, POST, PUT, PATCH, DELETE") @RequestHeader(value = "PSU-Http-Method", required = false) String psUHttpMethod,
            @ApiParam(value = "UUID (Universally Unique Identifier) for a device, which is used by the PSU, if available. UUID identifies either a device or a device dependant application installation. In case of an installation identification this ID need to be unaltered until removal from device. ") @RequestHeader(value = "PSU-Device-ID", required = false) UUID psUDeviceID,
            @ApiParam(value = "The forwarded Geo Location of the corresponding http request between PSU and TPP if available. ") @RequestHeader(value = "PSU-Geo-Location", required = false) String psUGeoLocation);

    @ApiOperation(value = "Read the SCA status of the payment cancellation's authorisation.", nickname = "getPaymentCancellationScaStatus", notes = "This method returns the SCA status of a payment initiation's authorisation sub-resource. ", response = ScaStatusResponse.class, authorizations = {
            @Authorization(value = "BearerAuthOAuth")
    }, tags = {"Payment Initiation Service (PIS)", "Common AIS and PIS Services",})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = ScaStatusResponse.class),
            @ApiResponse(code = 400, message = "Bad Request", response = TppMessages400.class),
            @ApiResponse(code = 401, message = "Unauthorized", response = TppMessages401.class),
            @ApiResponse(code = 403, message = "Forbidden", response = TppMessages403.class),
            @ApiResponse(code = 404, message = "Not found", response = TppMessages404.class),
            @ApiResponse(code = 405, message = "Method Not Allowed", response = TppMessages405.class),
            @ApiResponse(code = 406, message = "Not Acceptable", response = TppMessages406.class),
            @ApiResponse(code = 408, message = "Request Timeout"),
            @ApiResponse(code = 415, message = "Unsupported Media Type"),
            @ApiResponse(code = 429, message = "Too Many Requests", response = TppMessages429.class),
            @ApiResponse(code = 500, message = "Internal Server Error"),
            @ApiResponse(code = 503, message = "Service Unavailable")})
    @RequestMapping(value = "/v1/{payment-service}/{paymentId}/cancellation-authorisations/{cancellationId}",
            produces = {"application/json"},
            method = RequestMethod.GET)
    ResponseEntity<ScaStatusResponse> _getPaymentCancellationScaStatus(
            @ApiParam(value = "Payment service:  Possible values are: * payments * bulk-payments * periodic-payments ", required = true, allowableValues = "\"payments\", \"bulk-payments\", \"periodic-payments\"") @PathVariable("payment-service") String paymentService,
            @ApiParam(value = "Resource identification of the generated payment initiation resource.", required = true) @PathVariable("paymentId") String paymentId,
            @ApiParam(value = "Identification for cancellation resource.", required = true) @PathVariable("cancellationId") String cancellationId,
            @ApiParam(value = "ID of the request, unique to the call, as determined by the initiating party.", required = true) @RequestHeader(value = "X-Request-ID", required = true) UUID xRequestID,
            @ApiParam(value = "Is contained if and only if the \"Signature\" element is contained in the header of the request.") @RequestHeader(value = "Digest", required = false) String digest,
            @ApiParam(value = "A signature of the request by the TPP on application level. This might be mandated by ASPSP. ") @RequestHeader(value = "Signature", required = false) String signature,
            @ApiParam(value = "The certificate used for signing the request, in base64 encoding. Must be contained if a signature is contained. ") @RequestHeader(value = "TPP-Signature-Certificate", required = false) byte[] tpPSignatureCertificate,
            @ApiParam(value = "The forwarded IP Address header field consists of the corresponding http request IP Address field between PSU and TPP. ") @RequestHeader(value = "PSU-IP-Address", required = false) String psUIPAddress,
            @ApiParam(value = "The forwarded IP Port header field consists of the corresponding HTTP request IP Port field between PSU and TPP, if available. ") @RequestHeader(value = "PSU-IP-Port", required = false) Object psUIPPort,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept", required = false) String psUAccept,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Charset", required = false) String psUAcceptCharset,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Encoding", required = false) String psUAcceptEncoding,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Language", required = false) String psUAcceptLanguage,
            @ApiParam(value = "The forwarded Agent header field of the HTTP request between PSU and TPP, if available. ") @RequestHeader(value = "PSU-User-Agent", required = false) String psUUserAgent,
            @ApiParam(value = "HTTP method used at the PSU ? TPP interface, if available. Valid values are: * GET * POST * PUT * PATCH * DELETE ", allowableValues = "GET, POST, PUT, PATCH, DELETE") @RequestHeader(value = "PSU-Http-Method", required = false) String psUHttpMethod,
            @ApiParam(value = "UUID (Universally Unique Identifier) for a device, which is used by the PSU, if available. UUID identifies either a device or a device dependant application installation. In case of an installation identification this ID need to be unaltered until removal from device. ") @RequestHeader(value = "PSU-Device-ID", required = false) UUID psUDeviceID,
            @ApiParam(value = "The forwarded Geo Location of the corresponding http request between PSU and TPP if available. ") @RequestHeader(value = "PSU-Geo-Location", required = false) String psUGeoLocation);

    @ApiOperation(value = "Get Payment Information", nickname = "getPaymentInformation", notes = "Returns the content of a payment object", response = Object.class, authorizations = {
            @Authorization(value = "BearerAuthOAuth")
    }, tags = {"Payment Initiation Service (PIS)",})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = Object.class),
            @ApiResponse(code = 400, message = "Bad Request", response = TppMessages400.class),
            @ApiResponse(code = 401, message = "Unauthorized", response = TppMessages401.class),
            @ApiResponse(code = 403, message = "Forbidden", response = TppMessages403.class),
            @ApiResponse(code = 404, message = "Not found", response = TppMessages404.class),
            @ApiResponse(code = 405, message = "Method Not Allowed", response = TppMessages405.class),
            @ApiResponse(code = 406, message = "Not Acceptable", response = TppMessages406.class),
            @ApiResponse(code = 408, message = "Request Timeout"),
            @ApiResponse(code = 415, message = "Unsupported Media Type"),
            @ApiResponse(code = 429, message = "Too Many Requests", response = TppMessages429.class),
            @ApiResponse(code = 500, message = "Internal Server Error"),
            @ApiResponse(code = 503, message = "Service Unavailable")})
    @RequestMapping(value = "/v1/{payment-service}/{paymentId}",
            produces = {"application/json", "application/xml", "multipart/form-data"},
            method = RequestMethod.GET)
    ResponseEntity<Object> _getPaymentInformation(
            @ApiParam(value = "Payment service:  Possible values are: * payments * bulk-payments * periodic-payments ", required = true, allowableValues = "\"payments\", \"bulk-payments\", \"periodic-payments\"") @PathVariable("payment-service") String paymentService,
            @ApiParam(value = "Resource identification of the generated payment initiation resource.", required = true) @PathVariable("paymentId") String paymentId,
            @ApiParam(value = "ID of the request, unique to the call, as determined by the initiating party.", required = true) @RequestHeader(value = "X-Request-ID", required = true) UUID xRequestID,
            @ApiParam(value = "Is contained if and only if the \"Signature\" element is contained in the header of the request.") @RequestHeader(value = "Digest", required = false) String digest,
            @ApiParam(value = "A signature of the request by the TPP on application level. This might be mandated by ASPSP. ") @RequestHeader(value = "Signature", required = false) String signature,
            @ApiParam(value = "The certificate used for signing the request, in base64 encoding. Must be contained if a signature is contained. ") @RequestHeader(value = "TPP-Signature-Certificate", required = false) byte[] tpPSignatureCertificate,
            @ApiParam(value = "The forwarded IP Address header field consists of the corresponding http request IP Address field between PSU and TPP. ") @RequestHeader(value = "PSU-IP-Address", required = false) String psUIPAddress,
            @ApiParam(value = "The forwarded IP Port header field consists of the corresponding HTTP request IP Port field between PSU and TPP, if available. ") @RequestHeader(value = "PSU-IP-Port", required = false) Object psUIPPort,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept", required = false) String psUAccept,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Charset", required = false) String psUAcceptCharset,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Encoding", required = false) String psUAcceptEncoding,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Language", required = false) String psUAcceptLanguage,
            @ApiParam(value = "The forwarded Agent header field of the HTTP request between PSU and TPP, if available. ") @RequestHeader(value = "PSU-User-Agent", required = false) String psUUserAgent,
            @ApiParam(value = "HTTP method used at the PSU ? TPP interface, if available. Valid values are: * GET * POST * PUT * PATCH * DELETE ", allowableValues = "GET, POST, PUT, PATCH, DELETE") @RequestHeader(value = "PSU-Http-Method", required = false) String psUHttpMethod,
            @ApiParam(value = "UUID (Universally Unique Identifier) for a device, which is used by the PSU, if available. UUID identifies either a device or a device dependant application installation. In case of an installation identification this ID need to be unaltered until removal from device. ") @RequestHeader(value = "PSU-Device-ID", required = false) UUID psUDeviceID,
            @ApiParam(value = "The forwarded Geo Location of the corresponding http request between PSU and TPP if available. ") @RequestHeader(value = "PSU-Geo-Location", required = false) String psUGeoLocation);

    @ApiOperation(value = "Get Payment Initiation Authorisation Sub-Resources Request", nickname = "getPaymentInitiationAuthorisation", notes = "Read a list of all authorisation subresources IDs which have been created.  This function returns an array of hyperlinks to all generated authorisation sub-resources. ", response = Authorisations.class, authorizations = {
            @Authorization(value = "BearerAuthOAuth")
    }, tags = {"Payment Initiation Service (PIS)", "Common AIS and PIS Services",})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = Authorisations.class),
            @ApiResponse(code = 400, message = "Bad Request", response = TppMessages400.class),
            @ApiResponse(code = 401, message = "Unauthorized", response = TppMessages401.class),
            @ApiResponse(code = 403, message = "Forbidden", response = TppMessages403.class),
            @ApiResponse(code = 404, message = "Not found", response = TppMessages404.class),
            @ApiResponse(code = 405, message = "Method Not Allowed", response = TppMessages405.class),
            @ApiResponse(code = 406, message = "Not Acceptable", response = TppMessages406.class),
            @ApiResponse(code = 408, message = "Request Timeout"),
            @ApiResponse(code = 415, message = "Unsupported Media Type"),
            @ApiResponse(code = 429, message = "Too Many Requests", response = TppMessages429.class),
            @ApiResponse(code = 500, message = "Internal Server Error"),
            @ApiResponse(code = 503, message = "Service Unavailable")})
    @RequestMapping(value = "/v1/{payment-service}/{paymentId}/authorisations",
            produces = {"application/json"},
            method = RequestMethod.GET)
    ResponseEntity<Authorisations> _getPaymentInitiationAuthorisation(
            @ApiParam(value = "Payment service:  Possible values are: * payments * bulk-payments * periodic-payments ", required = true, allowableValues = "\"payments\", \"bulk-payments\", \"periodic-payments\"") @PathVariable("payment-service") String paymentService,
            @ApiParam(value = "Resource identification of the generated payment initiation resource.", required = true) @PathVariable("paymentId") String paymentId,
            @ApiParam(value = "ID of the request, unique to the call, as determined by the initiating party.", required = true) @RequestHeader(value = "X-Request-ID", required = true) UUID xRequestID,
            @ApiParam(value = "Is contained if and only if the \"Signature\" element is contained in the header of the request.") @RequestHeader(value = "Digest", required = false) String digest,
            @ApiParam(value = "A signature of the request by the TPP on application level. This might be mandated by ASPSP. ") @RequestHeader(value = "Signature", required = false) String signature,
            @ApiParam(value = "The certificate used for signing the request, in base64 encoding. Must be contained if a signature is contained. ") @RequestHeader(value = "TPP-Signature-Certificate", required = false) byte[] tpPSignatureCertificate,
            @ApiParam(value = "The forwarded IP Address header field consists of the corresponding http request IP Address field between PSU and TPP. ") @RequestHeader(value = "PSU-IP-Address", required = false) String psUIPAddress,
            @ApiParam(value = "The forwarded IP Port header field consists of the corresponding HTTP request IP Port field between PSU and TPP, if available. ") @RequestHeader(value = "PSU-IP-Port", required = false) Object psUIPPort,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept", required = false) String psUAccept,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Charset", required = false) String psUAcceptCharset,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Encoding", required = false) String psUAcceptEncoding,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Language", required = false) String psUAcceptLanguage,
            @ApiParam(value = "The forwarded Agent header field of the HTTP request between PSU and TPP, if available. ") @RequestHeader(value = "PSU-User-Agent", required = false) String psUUserAgent,
            @ApiParam(value = "HTTP method used at the PSU ? TPP interface, if available. Valid values are: * GET * POST * PUT * PATCH * DELETE ", allowableValues = "GET, POST, PUT, PATCH, DELETE") @RequestHeader(value = "PSU-Http-Method", required = false) String psUHttpMethod,
            @ApiParam(value = "UUID (Universally Unique Identifier) for a device, which is used by the PSU, if available. UUID identifies either a device or a device dependant application installation. In case of an installation identification this ID need to be unaltered until removal from device. ") @RequestHeader(value = "PSU-Device-ID", required = false) UUID psUDeviceID,
            @ApiParam(value = "The forwarded Geo Location of the corresponding http request between PSU and TPP if available. ") @RequestHeader(value = "PSU-Geo-Location", required = false) String psUGeoLocation);

    @ApiOperation(value = "Will deliver an array of resource identifications to all generated cancellation authorisation sub-resources.", nickname = "getPaymentInitiationCancellationAuthorisationInformation", notes = "Retrieve a list of all created cancellation authorisation sub-resources. ", response = CancellationList.class, authorizations = {
            @Authorization(value = "BearerAuthOAuth")
    }, tags = {"Payment Initiation Service (PIS)",})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = CancellationList.class),
            @ApiResponse(code = 400, message = "Bad Request", response = TppMessages400.class),
            @ApiResponse(code = 401, message = "Unauthorized", response = TppMessages401.class),
            @ApiResponse(code = 403, message = "Forbidden", response = TppMessages403.class),
            @ApiResponse(code = 404, message = "Not found", response = TppMessages404.class),
            @ApiResponse(code = 405, message = "Method Not Allowed", response = TppMessages405.class),
            @ApiResponse(code = 406, message = "Not Acceptable", response = TppMessages406.class),
            @ApiResponse(code = 408, message = "Request Timeout"),
            @ApiResponse(code = 415, message = "Unsupported Media Type"),
            @ApiResponse(code = 429, message = "Too Many Requests", response = TppMessages429.class),
            @ApiResponse(code = 500, message = "Internal Server Error"),
            @ApiResponse(code = 503, message = "Service Unavailable")})
    @RequestMapping(value = "/v1/{payment-service}/{paymentId}/cancellation-authorisations",
            produces = {"application/json"},
            method = RequestMethod.GET)
    ResponseEntity<CancellationList> _getPaymentInitiationCancellationAuthorisationInformation(
            @ApiParam(value = "Payment service:  Possible values are: * payments * bulk-payments * periodic-payments ", required = true, allowableValues = "\"payments\", \"bulk-payments\", \"periodic-payments\"") @PathVariable("payment-service") String paymentService,
            @ApiParam(value = "Resource identification of the generated payment initiation resource.", required = true) @PathVariable("paymentId") String paymentId,
            @ApiParam(value = "ID of the request, unique to the call, as determined by the initiating party.", required = true) @RequestHeader(value = "X-Request-ID", required = true) UUID xRequestID,
            @ApiParam(value = "Is contained if and only if the \"Signature\" element is contained in the header of the request.") @RequestHeader(value = "Digest", required = false) String digest,
            @ApiParam(value = "A signature of the request by the TPP on application level. This might be mandated by ASPSP. ") @RequestHeader(value = "Signature", required = false) String signature,
            @ApiParam(value = "The certificate used for signing the request, in base64 encoding. Must be contained if a signature is contained. ") @RequestHeader(value = "TPP-Signature-Certificate", required = false) byte[] tpPSignatureCertificate,
            @ApiParam(value = "The forwarded IP Address header field consists of the corresponding http request IP Address field between PSU and TPP. ") @RequestHeader(value = "PSU-IP-Address", required = false) String psUIPAddress,
            @ApiParam(value = "The forwarded IP Port header field consists of the corresponding HTTP request IP Port field between PSU and TPP, if available. ") @RequestHeader(value = "PSU-IP-Port", required = false) Object psUIPPort,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept", required = false) String psUAccept,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Charset", required = false) String psUAcceptCharset,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Encoding", required = false) String psUAcceptEncoding,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Language", required = false) String psUAcceptLanguage,
            @ApiParam(value = "The forwarded Agent header field of the HTTP request between PSU and TPP, if available. ") @RequestHeader(value = "PSU-User-Agent", required = false) String psUUserAgent,
            @ApiParam(value = "HTTP method used at the PSU ? TPP interface, if available. Valid values are: * GET * POST * PUT * PATCH * DELETE ", allowableValues = "GET, POST, PUT, PATCH, DELETE") @RequestHeader(value = "PSU-Http-Method", required = false) String psUHttpMethod,
            @ApiParam(value = "UUID (Universally Unique Identifier) for a device, which is used by the PSU, if available. UUID identifies either a device or a device dependant application installation. In case of an installation identification this ID need to be unaltered until removal from device. ") @RequestHeader(value = "PSU-Device-ID", required = false) UUID psUDeviceID,
            @ApiParam(value = "The forwarded Geo Location of the corresponding http request between PSU and TPP if available. ") @RequestHeader(value = "PSU-Geo-Location", required = false) String psUGeoLocation);

    @ApiOperation(value = "Read the SCA Status of the payment authorisation", nickname = "getPaymentInitiationScaStatus", notes = "This method returns the SCA status of a payment initiation's authorisation sub-resource. ", response = ScaStatusResponse.class, authorizations = {
            @Authorization(value = "BearerAuthOAuth")
    }, tags = {"Payment Initiation Service (PIS)", "Common AIS and PIS Services",})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = ScaStatusResponse.class),
            @ApiResponse(code = 400, message = "Bad Request", response = TppMessages400.class),
            @ApiResponse(code = 401, message = "Unauthorized", response = TppMessages401.class),
            @ApiResponse(code = 403, message = "Forbidden", response = TppMessages403.class),
            @ApiResponse(code = 404, message = "Not found", response = TppMessages404.class),
            @ApiResponse(code = 405, message = "Method Not Allowed", response = TppMessages405.class),
            @ApiResponse(code = 406, message = "Not Acceptable", response = TppMessages406.class),
            @ApiResponse(code = 408, message = "Request Timeout"),
            @ApiResponse(code = 415, message = "Unsupported Media Type"),
            @ApiResponse(code = 429, message = "Too Many Requests", response = TppMessages429.class),
            @ApiResponse(code = 500, message = "Internal Server Error"),
            @ApiResponse(code = 503, message = "Service Unavailable")})
    @RequestMapping(value = "/v1/{payment-service}/{paymentId}/authorisations/{authorisationId}",
            produces = {"application/json"},
            method = RequestMethod.GET)
    ResponseEntity<ScaStatusResponse> _getPaymentInitiationScaStatus(
            @ApiParam(value = "Payment service:  Possible values are: * payments * bulk-payments * periodic-payments ", required = true, allowableValues = "\"payments\", \"bulk-payments\", \"periodic-payments\"") @PathVariable("payment-service") String paymentService,
            @ApiParam(value = "Resource identification of the generated payment initiation resource.", required = true) @PathVariable("paymentId") String paymentId,
            @ApiParam(value = "Resource identification of the related SCA.", required = true) @PathVariable("authorisationId") String authorisationId,
            @ApiParam(value = "ID of the request, unique to the call, as determined by the initiating party.", required = true) @RequestHeader(value = "X-Request-ID", required = true) UUID xRequestID,
            @ApiParam(value = "Is contained if and only if the \"Signature\" element is contained in the header of the request.") @RequestHeader(value = "Digest", required = false) String digest,
            @ApiParam(value = "A signature of the request by the TPP on application level. This might be mandated by ASPSP. ") @RequestHeader(value = "Signature", required = false) String signature,
            @ApiParam(value = "The certificate used for signing the request, in base64 encoding. Must be contained if a signature is contained. ") @RequestHeader(value = "TPP-Signature-Certificate", required = false) byte[] tpPSignatureCertificate,
            @ApiParam(value = "The forwarded IP Address header field consists of the corresponding http request IP Address field between PSU and TPP. ") @RequestHeader(value = "PSU-IP-Address", required = false) String psUIPAddress,
            @ApiParam(value = "The forwarded IP Port header field consists of the corresponding HTTP request IP Port field between PSU and TPP, if available. ") @RequestHeader(value = "PSU-IP-Port", required = false) Object psUIPPort,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept", required = false) String psUAccept,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Charset", required = false) String psUAcceptCharset,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Encoding", required = false) String psUAcceptEncoding,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Language", required = false) String psUAcceptLanguage,
            @ApiParam(value = "The forwarded Agent header field of the HTTP request between PSU and TPP, if available. ") @RequestHeader(value = "PSU-User-Agent", required = false) String psUUserAgent,
            @ApiParam(value = "HTTP method used at the PSU ? TPP interface, if available. Valid values are: * GET * POST * PUT * PATCH * DELETE ", allowableValues = "GET, POST, PUT, PATCH, DELETE") @RequestHeader(value = "PSU-Http-Method", required = false) String psUHttpMethod,
            @ApiParam(value = "UUID (Universally Unique Identifier) for a device, which is used by the PSU, if available. UUID identifies either a device or a device dependant application installation. In case of an installation identification this ID need to be unaltered until removal from device. ") @RequestHeader(value = "PSU-Device-ID", required = false) UUID psUDeviceID,
            @ApiParam(value = "The forwarded Geo Location of the corresponding http request between PSU and TPP if available. ") @RequestHeader(value = "PSU-Geo-Location", required = false) String psUGeoLocation);

    @ApiOperation(value = "Payment initiation status request", nickname = "getPaymentInitiationStatus", notes = "Check the transaction status of a payment initiation.", response = PaymentInitiationStatusResponse200Json.class, authorizations = {
            @Authorization(value = "BearerAuthOAuth")
    }, tags = {"Payment Initiation Service (PIS)",})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = PaymentInitiationStatusResponse200Json.class),
            @ApiResponse(code = 400, message = "Bad Request", response = TppMessages400.class),
            @ApiResponse(code = 401, message = "Unauthorized", response = TppMessages401.class),
            @ApiResponse(code = 403, message = "Forbidden", response = TppMessages403.class),
            @ApiResponse(code = 404, message = "Not found", response = TppMessages404.class),
            @ApiResponse(code = 405, message = "Method Not Allowed", response = TppMessages405.class),
            @ApiResponse(code = 406, message = "Not Acceptable", response = TppMessages406.class),
            @ApiResponse(code = 408, message = "Request Timeout"),
            @ApiResponse(code = 415, message = "Unsupported Media Type"),
            @ApiResponse(code = 429, message = "Too Many Requests", response = TppMessages429.class),
            @ApiResponse(code = 500, message = "Internal Server Error"),
            @ApiResponse(code = 503, message = "Service Unavailable")})
    @RequestMapping(value = "/v1/{payment-service}/{paymentId}/status",
            produces = {"application/json", "application/xml"},
            method = RequestMethod.GET)
    ResponseEntity<PaymentInitiationStatusResponse200Json> _getPaymentInitiationStatus(
            @ApiParam(value = "Payment service:  Possible values are: * payments * bulk-payments * periodic-payments ", required = true, allowableValues = "\"payments\", \"bulk-payments\", \"periodic-payments\"") @PathVariable("payment-service") String paymentService,
            @ApiParam(value = "Resource identification of the generated payment initiation resource.", required = true) @PathVariable("paymentId") String paymentId,
            @ApiParam(value = "ID of the request, unique to the call, as determined by the initiating party.", required = true) @RequestHeader(value = "X-Request-ID", required = true) UUID xRequestID,
            @ApiParam(value = "Is contained if and only if the \"Signature\" element is contained in the header of the request.") @RequestHeader(value = "Digest", required = false) String digest,
            @ApiParam(value = "A signature of the request by the TPP on application level. This might be mandated by ASPSP. ") @RequestHeader(value = "Signature", required = false) String signature,
            @ApiParam(value = "The certificate used for signing the request, in base64 encoding. Must be contained if a signature is contained. ") @RequestHeader(value = "TPP-Signature-Certificate", required = false) byte[] tpPSignatureCertificate,
            @ApiParam(value = "The forwarded IP Address header field consists of the corresponding http request IP Address field between PSU and TPP. ") @RequestHeader(value = "PSU-IP-Address", required = false) String psUIPAddress,
            @ApiParam(value = "The forwarded IP Port header field consists of the corresponding HTTP request IP Port field between PSU and TPP, if available. ") @RequestHeader(value = "PSU-IP-Port", required = false) Object psUIPPort,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept", required = false) String psUAccept,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Charset", required = false) String psUAcceptCharset,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Encoding", required = false) String psUAcceptEncoding,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Language", required = false) String psUAcceptLanguage,
            @ApiParam(value = "The forwarded Agent header field of the HTTP request between PSU and TPP, if available. ") @RequestHeader(value = "PSU-User-Agent", required = false) String psUUserAgent,
            @ApiParam(value = "HTTP method used at the PSU ? TPP interface, if available. Valid values are: * GET * POST * PUT * PATCH * DELETE ", allowableValues = "GET, POST, PUT, PATCH, DELETE") @RequestHeader(value = "PSU-Http-Method", required = false) String psUHttpMethod,
            @ApiParam(value = "UUID (Universally Unique Identifier) for a device, which is used by the PSU, if available. UUID identifies either a device or a device dependant application installation. In case of an installation identification this ID need to be unaltered until removal from device. ") @RequestHeader(value = "PSU-Device-ID", required = false) UUID psUDeviceID,
            @ApiParam(value = "The forwarded Geo Location of the corresponding http request between PSU and TPP if available. ") @RequestHeader(value = "PSU-Geo-Location", required = false) String psUGeoLocation);

    @ApiOperation(value = "Payment initiation request", nickname = "initiatePayment", notes = "This method is used to initiate a payment at the ASPSP.  ## Variants of Payment Initiation Requests  This method to initiate a payment initiation at the ASPSP can be sent with either a JSON body or an pain.001 body depending on the payment product in the path.  There are the following **payment products**:    - Payment products with payment information in *JSON* format:     - ***sepa-credit-transfers***     - ***instant-sepa-credit-transfers***     - ***target-2-payments***     - ***cross-border-credit-transfers***   - Payment products with payment information in *pain.001* XML format:     - ***pain.001-sepa-credit-transfers***     - ***pain.001-instant-sepa-credit-transfers***     - ***pain.001-target-2-payments***     - ***pain.001-cross-border-credit-transfers***  Furthermore the request body depends on the **payment-service**   * ***payments***: A single payment initiation request.   * ***bulk-payments***: A collection of several payment iniatiation requests.      In case of a *pain.001* message there are more than one payments contained in the *pain.001 message.      In case of a *JSON* there are several JSON payment blocks contained in a joining list.   * ***periodic-payments***:     Create a standing order initiation resource for recurrent i.e. periodic payments addressable under {paymentId}      with all data relevant for the corresponding payment product and the execution of the standing order contained in a JSON body.  This is the first step in the API to initiate the related recurring/periodic payment.  ## Single and mulitilevel SCA Processes  The Payment Initiation Requests are independent from the need of one ore multilevel SCA processing, i.e. independent from the number of authorisations needed for the execution of payments.  But the response messages are specific to either one SCA processing or multilevel SCA processing.  For payment initiation with multilevel SCA, this specification requires an explicit start of the authorisation, i.e. links directly associated with SCA processing like 'scaRedirect' or 'scaOAuth' cannot be contained in the response message of a Payment Initation Request for a payment, where multiple authorisations are needed. Also if any data is needed for the next action, like selecting an SCA method is not supported in the response, since all starts of the multiple authorisations are fully equal. In these cases, first an authorisation sub-resource has to be generated following the 'startAuthorisation' link. ", response = Object.class, authorizations = {
            @Authorization(value = "BearerAuthOAuth")
    }, tags = {"Payment Initiation Service (PIS)",})
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "CREATED", response = Object.class),
            @ApiResponse(code = 400, message = "Bad Request", response = TppMessages400.class),
            @ApiResponse(code = 401, message = "Unauthorized", response = TppMessages401.class),
            @ApiResponse(code = 403, message = "Forbidden", response = TppMessages403.class),
            @ApiResponse(code = 404, message = "Not found", response = TppMessages404.class),
            @ApiResponse(code = 405, message = "Method Not Allowed", response = TppMessages405.class),
            @ApiResponse(code = 406, message = "Not Acceptable", response = TppMessages406.class),
            @ApiResponse(code = 408, message = "Request Timeout"),
            @ApiResponse(code = 415, message = "Unsupported Media Type"),
            @ApiResponse(code = 429, message = "Too Many Requests", response = TppMessages429.class),
            @ApiResponse(code = 500, message = "Internal Server Error"),
            @ApiResponse(code = 503, message = "Service Unavailable")})
    @RequestMapping(value = "/v1/{payment-service}/{payment-product}",
            produces = {"application/json"},
            method = RequestMethod.POST)
    ResponseEntity<PaymentInitationRequestResponse201> _initiatePayment(
            @ApiParam(value = "JSON request body for a payment inition request message  There are the following payment-products supported:   * \"sepa-credit-transfers\" with JSON-Body   * \"instant-sepa-credit-transfers\" with JSON-Body   * \"target-2-payments\" with JSON-Body   * \"cross-border-credit-transfers\" with JSON-Body   * \"pain.001-sepa-credit-transfers\" with XML pain.001.001.03 body for SCT scheme   * \"pain.001-instant-sepa-credit-transfers\" with XML pain.001.001.03 body for SCT INST scheme   * \"pain.001-target-2-payments\" with pain.001 body.     Only country specific schemes are currently available   * \"pain.001-cross-border-credit-transfers\" with pain.001 body.     Only country specific schemes are currently available  There are the following payment-services supported:   * \"payments\"   * \"periodic-payments\"   * \"bulk-paments\"  All optional, conditional and predefined but not yet used fields are defined. ", required = true) @Valid @RequestBody Object body,
            @ApiParam(value = "Payment service:  Possible values are: * payments * bulk-payments * periodic-payments ", required = true, allowableValues = "\"payments\", \"bulk-payments\", \"periodic-payments\"") @PathVariable("payment-service") String paymentService,
            @ApiParam(value = "The addressed payment product endpoint, e.g. for SEPA Credit Transfers (SCT). The ASPSP will publish which of the payment products/endpoints will be supported.  The following payment products are supported:   - sepa-credit-transfers   - instant-sepa-credit-transfers   - target-2-payments   - cross-border-credit-transfers   - pain.001-sepa-credit-transfers   - pain.001-instant-sepa-credit-transfers   - pain.001-target-2-payments   - pain.001-cross-border-credit-transfers  **Remark:** For all SEPA Credit Transfer based endpoints which accept XML encoding, the XML pain.001 schemes provided by EPC are supported by the ASPSP as a minimum for the body content. Further XML schemes might be supported by some communities.  **Remark:** For cross-border and TARGET-2 payments only community wide pain.001 schemes do exist. There are plenty of country specificic scheme variants. ", required = true, allowableValues = "\"sepa-credit-transfers\", \"instant-sepa-credit-transfers\", \"target-2-payments\", \"cross-border-credit-transfers\", \"pain.001-sepa-credit-transfers\", \"pain.001-instant-sepa-credit-transfers\", \"pain.001-target-2-payments\", \"pain.001-cross-border-credit-transfers\"") @PathVariable("payment-product") String paymentProduct,
            @ApiParam(value = "ID of the request, unique to the call, as determined by the initiating party.", required = true) @RequestHeader(value = "X-Request-ID", required = true) UUID xRequestID,
            @ApiParam(value = "The forwarded IP Address header field consists of the corresponding http request IP Address field between PSU and TPP. ", required = true) @RequestHeader(value = "PSU-IP-Address", required = true) String psUIPAddress,
            @ApiParam(value = "Is contained if and only if the \"Signature\" element is contained in the header of the request.") @RequestHeader(value = "Digest", required = false) String digest,
            @ApiParam(value = "A signature of the request by the TPP on application level. This might be mandated by ASPSP. ") @RequestHeader(value = "Signature", required = false) String signature,
            @ApiParam(value = "The certificate used for signing the request, in base64 encoding. Must be contained if a signature is contained. ") @RequestHeader(value = "TPP-Signature-Certificate", required = false) byte[] tpPSignatureCertificate,
            @ApiParam(value = "Client ID of the PSU in the ASPSP client interface. Might be mandated in the ASPSP's documentation. Is not contained if an OAuth2 based authentication was performed in a pre-step or an OAuth2 based SCA was performed in an preceeding AIS service in the same session. ") @RequestHeader(value = "PSU-ID", required = false) String PSU_ID,
            @ApiParam(value = "Type of the PSU-ID, needed in scenarios where PSUs have several PSU-IDs as access possibility. ") @RequestHeader(value = "PSU-ID-Type", required = false) String psUIDType,
            @ApiParam(value = "Might be mandated in the ASPSP's documentation. Only used in a corporate context. ") @RequestHeader(value = "PSU-Corporate-ID", required = false) String psUCorporateID,
            @ApiParam(value = "Might be mandated in the ASPSP's documentation. Only used in a corporate context. ") @RequestHeader(value = "PSU-Corporate-ID-Type", required = false) String psUCorporateIDType,
            @ApiParam(value = "This data element may be contained, if the payment initiation transaction is part of a session, i.e. combined AIS/PIS service. This then contains the consentId of the related AIS consent, which was performed prior to this payment initiation. ") @RequestHeader(value = "Consent-ID", required = false) String consentID,
            @ApiParam(value = "If it equals \"true\", the TPP prefers a redirect over an embedded SCA approach. If it equals \"false\", the TPP prefers not to be redirected for SCA. The ASPSP will then choose between the Embedded or the Decoupled SCA approach, depending on the choice of the SCA procedure by the TPP/PSU. If the parameter is not used, the ASPSP will choose the SCA approach to be applied depending on the SCA method chosen by the TPP/PSU. ") @RequestHeader(value = "TPP-Redirect-Preferred", required = false) Boolean tpPRedirectPreferred,
            @ApiParam(value = "URI of the TPP, where the transaction flow shall be redirected to after a Redirect.  Mandated for the Redirect SCA Approach (including OAuth2 SCA approach), specifically when TPP-Redirect-Preferred equals \"true\". It is recommended to always use this header field.  **Remark for Future:** This field might be changed to mandatory in the next version of the specification. ") @RequestHeader(value = "TPP-Redirect-URI", required = false) String tpPRedirectURI,
            @ApiParam(value = "If this URI is contained, the TPP is asking to redirect the transaction flow to this address instead of the TPP-Redirect-URI in case of a negative result of the redirect SCA method. This might be ignored by the ASPSP. ") @RequestHeader(value = "TPP-Nok-Redirect-URI", required = false) String tpPNokRedirectURI,
            @ApiParam(value = "If it equals \"true\", the TPP prefers to start the authorisation process separately, e.g. because of the usage of a signing basket. This preference might be ignored by the ASPSP, if a signing basket is not supported as functionality.  If it equals \"false\" or if the parameter is not used, there is no preference of the TPP. This especially indicates that the TPP assumes a direct authorisation of the transaction in the next step, without using a signing basket. ") @RequestHeader(value = "TPP-Explicit-Authorisation-Preferred", required = false) Boolean tpPExplicitAuthorisationPreferred,
            @ApiParam(value = "The forwarded IP Port header field consists of the corresponding HTTP request IP Port field between PSU and TPP, if available. ") @RequestHeader(value = "PSU-IP-Port", required = false) Object psUIPPort,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept", required = false) String psUAccept,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Charset", required = false) String psUAcceptCharset,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Encoding", required = false) String psUAcceptEncoding,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Language", required = false) String psUAcceptLanguage,
            @ApiParam(value = "The forwarded Agent header field of the HTTP request between PSU and TPP, if available. ") @RequestHeader(value = "PSU-User-Agent", required = false) String psUUserAgent,
            @ApiParam(value = "HTTP method used at the PSU ? TPP interface, if available. Valid values are: * GET * POST * PUT * PATCH * DELETE ", allowableValues = "GET, POST, PUT, PATCH, DELETE") @RequestHeader(value = "PSU-Http-Method", required = false) String psUHttpMethod,
            @ApiParam(value = "UUID (Universally Unique Identifier) for a device, which is used by the PSU, if available. UUID identifies either a device or a device dependant application installation. In case of an installation identification this ID need to be unaltered until removal from device. ") @RequestHeader(value = "PSU-Device-ID", required = false) UUID psUDeviceID,
            @ApiParam(value = "The forwarded Geo Location of the corresponding http request between PSU and TPP if available. ") @RequestHeader(value = "PSU-Geo-Location", required = false) String psUGeoLocation);

    @ApiOperation(value = "Start the authorisation process for a payment initiation", nickname = "startPaymentAuthorisation", notes = "Create an authorisation sub-resource and start the authorisation process. The message might in addition transmit authentication and authorisation related data.  This method is iterated n times for a n times SCA authorisation in a corporate context, each creating an own authorisation sub-endpoint for the corresponding PSU authorising the transaction.  The ASPSP might make the usage of this access method unnecessary in case of only one SCA process needed, since the related authorisation resource might be automatically created by the ASPSP after the submission of the payment data with the first POST payments/{payment-product} call.  The start authorisation process is a process which is needed for creating a new authorisation or cancellation sub-resource.  This applies in the following scenarios:    * The ASPSP has indicated with an 'startAuthorisation' hyperlink in the preceeding Payment     Initiation Response that an explicit start of the authorisation process is needed by the TPP.     The 'startAuthorisation' hyperlink can transport more information about data which needs to be     uploaded by using the extended forms.     * 'startAuthorisationWithPsuIdentfication',     * 'startAuthorisationWithPsuAuthentication'     * 'startAuthorisationWithAuthentciationMethodSelection'   * The related payment initiation cannot yet be executed since a multilevel SCA is mandated.   * The ASPSP has indicated with an 'startAuthorisation' hyperlink in the preceeding     Payment Cancellation Response that an explicit start of the authorisation process is needed by the TPP.     The 'startAuthorisation' hyperlink can transport more information about data which needs to be uploaded     by using the extended forms as indicated above.   * The related payment cancellation request cannot be applied yet since a multilevel SCA is mandate for     executing the cancellation.   * The signing basket needs to be authorised yet. ", response = StartScaprocessResponse.class, authorizations = {
            @Authorization(value = "BearerAuthOAuth")
    }, tags = {"Payment Initiation Service (PIS)", "Common AIS and PIS Services",})
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "Created", response = StartScaprocessResponse.class),
            @ApiResponse(code = 400, message = "Bad Request", response = TppMessages400.class),
            @ApiResponse(code = 401, message = "Unauthorized", response = TppMessages401.class),
            @ApiResponse(code = 403, message = "Forbidden", response = TppMessages403.class),
            @ApiResponse(code = 404, message = "Not found", response = TppMessages404.class),
            @ApiResponse(code = 405, message = "Method Not Allowed", response = TppMessages405.class),
            @ApiResponse(code = 406, message = "Not Acceptable", response = TppMessages406.class),
            @ApiResponse(code = 408, message = "Request Timeout"),
            @ApiResponse(code = 415, message = "Unsupported Media Type"),
            @ApiResponse(code = 429, message = "Too Many Requests", response = TppMessages429.class),
            @ApiResponse(code = 500, message = "Internal Server Error"),
            @ApiResponse(code = 503, message = "Service Unavailable")})
    @RequestMapping(value = "/v1/{payment-service}/{paymentId}/authorisations",
            produces = {"application/json"},
            method = RequestMethod.POST)
    ResponseEntity<StartScaprocessResponse> _startPaymentAuthorisation(
            @ApiParam(value = "Payment service:  Possible values are: * payments * bulk-payments * periodic-payments ", required = true, allowableValues = "\"payments\", \"bulk-payments\", \"periodic-payments\"") @PathVariable("payment-service") String paymentService,
            @ApiParam(value = "Resource identification of the generated payment initiation resource.", required = true) @PathVariable("paymentId") String paymentId,
            @ApiParam(value = "ID of the request, unique to the call, as determined by the initiating party.", required = true) @RequestHeader(value = "X-Request-ID", required = true) UUID xRequestID,
            @ApiParam(value = "Client ID of the PSU in the ASPSP client interface. Might be mandated in the ASPSP's documentation. Is not contained if an OAuth2 based authentication was performed in a pre-step or an OAuth2 based SCA was performed in an preceeding AIS service in the same session. ") @RequestHeader(value = "PSU-ID", required = false) String PSU_ID,
            @ApiParam(value = "Type of the PSU-ID, needed in scenarios where PSUs have several PSU-IDs as access possibility. ") @RequestHeader(value = "PSU-ID-Type", required = false) String psUIDType,
            @ApiParam(value = "Might be mandated in the ASPSP's documentation. Only used in a corporate context. ") @RequestHeader(value = "PSU-Corporate-ID", required = false) String psUCorporateID,
            @ApiParam(value = "Might be mandated in the ASPSP's documentation. Only used in a corporate context. ") @RequestHeader(value = "PSU-Corporate-ID-Type", required = false) String psUCorporateIDType,
            @ApiParam(value = "Is contained if and only if the \"Signature\" element is contained in the header of the request.") @RequestHeader(value = "Digest", required = false) String digest,
            @ApiParam(value = "A signature of the request by the TPP on application level. This might be mandated by ASPSP. ") @RequestHeader(value = "Signature", required = false) String signature,
            @ApiParam(value = "The certificate used for signing the request, in base64 encoding. Must be contained if a signature is contained. ") @RequestHeader(value = "TPP-Signature-Certificate", required = false) byte[] tpPSignatureCertificate,
            @ApiParam(value = "The forwarded IP Address header field consists of the corresponding http request IP Address field between PSU and TPP. ") @RequestHeader(value = "PSU-IP-Address", required = false) String psUIPAddress,
            @ApiParam(value = "The forwarded IP Port header field consists of the corresponding HTTP request IP Port field between PSU and TPP, if available. ") @RequestHeader(value = "PSU-IP-Port", required = false) Object psUIPPort,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept", required = false) String psUAccept,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Charset", required = false) String psUAcceptCharset,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Encoding", required = false) String psUAcceptEncoding,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Language", required = false) String psUAcceptLanguage,
            @ApiParam(value = "The forwarded Agent header field of the HTTP request between PSU and TPP, if available. ") @RequestHeader(value = "PSU-User-Agent", required = false) String psUUserAgent,
            @ApiParam(value = "HTTP method used at the PSU ? TPP interface, if available. Valid values are: * GET * POST * PUT * PATCH * DELETE ", allowableValues = "GET, POST, PUT, PATCH, DELETE") @RequestHeader(value = "PSU-Http-Method", required = false) String psUHttpMethod,
            @ApiParam(value = "UUID (Universally Unique Identifier) for a device, which is used by the PSU, if available. UUID identifies either a device or a device dependant application installation. In case of an installation identification this ID need to be unaltered until removal from device. ") @RequestHeader(value = "PSU-Device-ID", required = false) UUID psUDeviceID,
            @ApiParam(value = "The forwarded Geo Location of the corresponding http request between PSU and TPP if available. ") @RequestHeader(value = "PSU-Geo-Location", required = false) String psUGeoLocation);

    @ApiOperation(value = "Start the authorisation process for the cancellation of the addressed payment", nickname = "startPaymentInitiationCancellationAuthorisation", notes = "Creates an authorisation sub-resource and start the authorisation process of the cancellation of the addressed payment. The message might in addition transmit authentication and authorisation related data.  This method is iterated n times for a n times SCA authorisation in a corporate context, each creating an own authorisation sub-endpoint for the corresponding PSU authorising the cancellation-authorisation.  The ASPSP might make the usage of this access method unnecessary in case of only one SCA process needed, since the related authorisation resource might be automatically created by the ASPSP after the submission of the payment data with the first POST payments/{payment-product} call.  The start authorisation process is a process which is needed for creating a new authorisation or cancellation sub-resource.  This applies in the following scenarios:    * The ASPSP has indicated with an 'startAuthorisation' hyperlink in the preceeding Payment     Initiation Response that an explicit start of the authorisation process is needed by the TPP.     The 'startAuthorisation' hyperlink can transport more information about data which needs to be     uploaded by using the extended forms.     * 'startAuthorisationWithPsuIdentfication',     * 'startAuthorisationWithPsuAuthentication'     * 'startAuthorisationWithAuthentciationMethodSelection'   * The related payment initiation cannot yet be executed since a multilevel SCA is mandated.   * The ASPSP has indicated with an 'startAuthorisation' hyperlink in the preceeding     Payment Cancellation Response that an explicit start of the authorisation process is needed by the TPP.     The 'startAuthorisation' hyperlink can transport more information about data which needs to be uploaded     by using the extended forms as indicated above.   * The related payment cancellation request cannot be applied yet since a multilevel SCA is mandate for     executing the cancellation.   * The signing basket needs to be authorised yet. ", response = StartScaprocessResponse.class, authorizations = {
            @Authorization(value = "BearerAuthOAuth")
    }, tags = {"Payment Initiation Service (PIS)", "Common AIS and PIS Services",})
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "Created", response = StartScaprocessResponse.class),
            @ApiResponse(code = 400, message = "Bad Request", response = TppMessages400.class),
            @ApiResponse(code = 401, message = "Unauthorized", response = TppMessages401.class),
            @ApiResponse(code = 403, message = "Forbidden", response = TppMessages403.class),
            @ApiResponse(code = 404, message = "Not found", response = TppMessages404.class),
            @ApiResponse(code = 405, message = "Method Not Allowed", response = TppMessages405.class),
            @ApiResponse(code = 406, message = "Not Acceptable", response = TppMessages406.class),
            @ApiResponse(code = 408, message = "Request Timeout"),
            @ApiResponse(code = 415, message = "Unsupported Media Type"),
            @ApiResponse(code = 429, message = "Too Many Requests", response = TppMessages429.class),
            @ApiResponse(code = 500, message = "Internal Server Error"),
            @ApiResponse(code = 503, message = "Service Unavailable")})
    @RequestMapping(value = "/v1/{payment-service}/{paymentId}/cancellation-authorisations",
            produces = {"application/json"},
            method = RequestMethod.POST)
    ResponseEntity<StartScaprocessResponse> _startPaymentInitiationCancellationAuthorisation(
            @ApiParam(value = "Payment service:  Possible values are: * payments * bulk-payments * periodic-payments ", required = true, allowableValues = "\"payments\", \"bulk-payments\", \"periodic-payments\"") @PathVariable("payment-service") String paymentService,
            @ApiParam(value = "Resource identification of the generated payment initiation resource.", required = true) @PathVariable("paymentId") String paymentId,
            @ApiParam(value = "ID of the request, unique to the call, as determined by the initiating party.", required = true) @RequestHeader(value = "X-Request-ID", required = true) UUID xRequestID,
            @ApiParam(value = "Is contained if and only if the \"Signature\" element is contained in the header of the request.") @RequestHeader(value = "Digest", required = false) String digest,
            @ApiParam(value = "A signature of the request by the TPP on application level. This might be mandated by ASPSP. ") @RequestHeader(value = "Signature", required = false) String signature,
            @ApiParam(value = "The certificate used for signing the request, in base64 encoding. Must be contained if a signature is contained. ") @RequestHeader(value = "TPP-Signature-Certificate", required = false) byte[] tpPSignatureCertificate,
            @ApiParam(value = "Client ID of the PSU in the ASPSP client interface. Might be mandated in the ASPSP's documentation. Is not contained if an OAuth2 based authentication was performed in a pre-step or an OAuth2 based SCA was performed in an preceeding AIS service in the same session. ") @RequestHeader(value = "PSU-ID", required = false) String PSU_ID,
            @ApiParam(value = "Type of the PSU-ID, needed in scenarios where PSUs have several PSU-IDs as access possibility. ") @RequestHeader(value = "PSU-ID-Type", required = false) String psUIDType,
            @ApiParam(value = "Might be mandated in the ASPSP's documentation. Only used in a corporate context. ") @RequestHeader(value = "PSU-Corporate-ID", required = false) String psUCorporateID,
            @ApiParam(value = "Might be mandated in the ASPSP's documentation. Only used in a corporate context. ") @RequestHeader(value = "PSU-Corporate-ID-Type", required = false) String psUCorporateIDType,
            @ApiParam(value = "The forwarded IP Address header field consists of the corresponding http request IP Address field between PSU and TPP. ") @RequestHeader(value = "PSU-IP-Address", required = false) String psUIPAddress,
            @ApiParam(value = "The forwarded IP Port header field consists of the corresponding HTTP request IP Port field between PSU and TPP, if available. ") @RequestHeader(value = "PSU-IP-Port", required = false) Object psUIPPort,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept", required = false) String psUAccept,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Charset", required = false) String psUAcceptCharset,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Encoding", required = false) String psUAcceptEncoding,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Language", required = false) String psUAcceptLanguage,
            @ApiParam(value = "The forwarded Agent header field of the HTTP request between PSU and TPP, if available. ") @RequestHeader(value = "PSU-User-Agent", required = false) String psUUserAgent,
            @ApiParam(value = "HTTP method used at the PSU ? TPP interface, if available. Valid values are: * GET * POST * PUT * PATCH * DELETE ", allowableValues = "GET, POST, PUT, PATCH, DELETE") @RequestHeader(value = "PSU-Http-Method", required = false) String psUHttpMethod,
            @ApiParam(value = "UUID (Universally Unique Identifier) for a device, which is used by the PSU, if available. UUID identifies either a device or a device dependant application installation. In case of an installation identification this ID need to be unaltered until removal from device. ") @RequestHeader(value = "PSU-Device-ID", required = false) UUID psUDeviceID,
            @ApiParam(value = "The forwarded Geo Location of the corresponding http request between PSU and TPP if available. ") @RequestHeader(value = "PSU-Geo-Location", required = false) String psUGeoLocation);

    @ApiOperation(value = "Update PSU Data for payment initiation cancellation", nickname = "updatePaymentCancellationPsuData", notes = "This method updates PSU data on the cancellation authorisation resource if needed. It may authorise a cancellation of the payment within the Embedded SCA Approach where needed.  Independently from the SCA Approach it supports e.g. the selection of the authentication method and a non-SCA PSU authentication.  This methods updates PSU data on the cancellation authorisation resource if needed.  There are several possible Update PSU Data requests in the context of a cancellation authorisation within the payment initiation services needed, which depends on the SCA approach:  * Redirect SCA Approach:   A specific Update PSU Data Request is applicable for     * the selection of authentication methods, before choosing the actual SCA approach. * Decoupled SCA Approach:   A specific Update PSU Data Request is only applicable for   * adding the PSU Identification, if not provided yet in the Payment Initiation Request or the Account Information Consent Request, or if no OAuth2 access token is used, or   * the selection of authentication methods. * Embedded SCA Approach:   The Update PSU Data Request might be used   * to add credentials as a first factor authentication data of the PSU and   * to select the authentication method and   * transaction authorisation.  The SCA Approach might depend on the chosen SCA method. For that reason, the following possible Update PSU Data request can apply to all SCA approaches:  * Select an SCA method in case of several SCA methods are available for the customer.  There are the following request types on this access path:   * Update PSU Identification   * Update PSU Authentication   * Select PSU Autorization Method     WARNING: This method need a reduced header,     therefore many optional elements are not present.     Maybe in a later version the access path will change.   * Transaction Authorisation     WARNING: This method need a reduced header,     therefore many optional elements are not present.     Maybe in a later version the access path will change. ", response = Object.class, authorizations = {
            @Authorization(value = "BearerAuthOAuth")
    }, tags = {"Payment Initiation Service (PIS)", "Common AIS and PIS Services",})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = Object.class),
            @ApiResponse(code = 400, message = "Bad Request", response = TppMessages400.class),
            @ApiResponse(code = 401, message = "Unauthorized", response = TppMessages401.class),
            @ApiResponse(code = 403, message = "Forbidden", response = TppMessages403.class),
            @ApiResponse(code = 404, message = "Not found", response = TppMessages404.class),
            @ApiResponse(code = 405, message = "Method Not Allowed", response = TppMessages405.class),
            @ApiResponse(code = 406, message = "Not Acceptable", response = TppMessages406.class),
            @ApiResponse(code = 408, message = "Request Timeout"),
            @ApiResponse(code = 415, message = "Unsupported Media Type"),
            @ApiResponse(code = 429, message = "Too Many Requests", response = TppMessages429.class),
            @ApiResponse(code = 500, message = "Internal Server Error"),
            @ApiResponse(code = 503, message = "Service Unavailable")})
    @RequestMapping(value = "/v1/{payment-service}/{paymentId}/cancellation-authorisations/{cancellationId}",
            produces = {"application/json"},
            consumes = {"application/json"},
            method = RequestMethod.PUT)
    ResponseEntity<Object> _updatePaymentCancellationPsuData(
            @ApiParam(value = "Payment service:  Possible values are: * payments * bulk-payments * periodic-payments ", required = true, allowableValues = "\"payments\", \"bulk-payments\", \"periodic-payments\"") @PathVariable("payment-service") String paymentService,
            @ApiParam(value = "Resource identification of the generated payment initiation resource.", required = true) @PathVariable("paymentId") String paymentId,
            @ApiParam(value = "Identification for cancellation resource.", required = true) @PathVariable("cancellationId") String cancellationId,
            @ApiParam(value = "ID of the request, unique to the call, as determined by the initiating party.", required = true) @RequestHeader(value = "X-Request-ID", required = true) UUID xRequestID,
            @ApiParam(value = "") @Valid @RequestBody Object body,
            @ApiParam(value = "Is contained if and only if the \"Signature\" element is contained in the header of the request.") @RequestHeader(value = "Digest", required = false) String digest,
            @ApiParam(value = "A signature of the request by the TPP on application level. This might be mandated by ASPSP. ") @RequestHeader(value = "Signature", required = false) String signature,
            @ApiParam(value = "The certificate used for signing the request, in base64 encoding. Must be contained if a signature is contained. ") @RequestHeader(value = "TPP-Signature-Certificate", required = false) byte[] tpPSignatureCertificate,
            @ApiParam(value = "Client ID of the PSU in the ASPSP client interface. Might be mandated in the ASPSP's documentation. Is not contained if an OAuth2 based authentication was performed in a pre-step or an OAuth2 based SCA was performed in an preceeding AIS service in the same session. ") @RequestHeader(value = "PSU-ID", required = false) String PSU_ID,
            @ApiParam(value = "Type of the PSU-ID, needed in scenarios where PSUs have several PSU-IDs as access possibility. ") @RequestHeader(value = "PSU-ID-Type", required = false) String psUIDType,
            @ApiParam(value = "Might be mandated in the ASPSP's documentation. Only used in a corporate context. ") @RequestHeader(value = "PSU-Corporate-ID", required = false) String psUCorporateID,
            @ApiParam(value = "Might be mandated in the ASPSP's documentation. Only used in a corporate context. ") @RequestHeader(value = "PSU-Corporate-ID-Type", required = false) String psUCorporateIDType,
            @ApiParam(value = "The forwarded IP Address header field consists of the corresponding http request IP Address field between PSU and TPP. ") @RequestHeader(value = "PSU-IP-Address", required = false) String psUIPAddress,
            @ApiParam(value = "The forwarded IP Port header field consists of the corresponding HTTP request IP Port field between PSU and TPP, if available. ") @RequestHeader(value = "PSU-IP-Port", required = false) Object psUIPPort,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept", required = false) String psUAccept,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Charset", required = false) String psUAcceptCharset,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Encoding", required = false) String psUAcceptEncoding,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Language", required = false) String psUAcceptLanguage,
            @ApiParam(value = "The forwarded Agent header field of the HTTP request between PSU and TPP, if available. ") @RequestHeader(value = "PSU-User-Agent", required = false) String psUUserAgent,
            @ApiParam(value = "HTTP method used at the PSU ? TPP interface, if available. Valid values are: * GET * POST * PUT * PATCH * DELETE ", allowableValues = "GET, POST, PUT, PATCH, DELETE") @RequestHeader(value = "PSU-Http-Method", required = false) String psUHttpMethod,
            @ApiParam(value = "UUID (Universally Unique Identifier) for a device, which is used by the PSU, if available. UUID identifies either a device or a device dependant application installation. In case of an installation identification this ID need to be unaltered until removal from device. ") @RequestHeader(value = "PSU-Device-ID", required = false) UUID psUDeviceID,
            @ApiParam(value = "The forwarded Geo Location of the corresponding http request between PSU and TPP if available. ") @RequestHeader(value = "PSU-Geo-Location", required = false) String psUGeoLocation);

    @ApiOperation(value = "Update PSU data for payment initiation", nickname = "updatePaymentPsuData", notes = "This methods updates PSU data on the authorisation resource if needed. It may authorise a payment within the Embedded SCA Approach where needed.  Independently from the SCA Approach it supports e.g. the selection of the authentication method and a non-SCA PSU authentication.  There are several possible Update PSU Data requests in the context of payment initiation services needed, which depends on the SCA approach:  * Redirect SCA Approach:   A specific Update PSU Data Request is applicable for     * the selection of authentication methods, before choosing the actual SCA approach. * Decoupled SCA Approach:   A specific Update PSU Data Request is only applicable for   * adding the PSU Identification, if not provided yet in the Payment Initiation Request or the Account Information Consent Request, or if no OAuth2 access token is used, or   * the selection of authentication methods. * Embedded SCA Approach:   The Update PSU Data Request might be used   * to add credentials as a first factor authentication data of the PSU and   * to select the authentication method and   * transaction authorisation.  The SCA Approach might depend on the chosen SCA method. For that reason, the following possible Update PSU Data request can apply to all SCA approaches:  * Select an SCA method in case of several SCA methods are available for the customer.  There are the following request types on this access path:   * Update PSU Identification   * Update PSU Authentication   * Select PSU Autorization Method     WARNING: This method need a reduced header,     therefore many optional elements are not present.     Maybe in a later version the access path will change.   * Transaction Authorisation     WARNING: This method need a reduced header,     therefore many optional elements are not present.     Maybe in a later version the access path will change. ", response = Object.class, authorizations = {
            @Authorization(value = "BearerAuthOAuth")
    }, tags = {"Payment Initiation Service (PIS)", "Common AIS and PIS Services",})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = Object.class),
            @ApiResponse(code = 400, message = "Bad Request", response = TppMessages400.class),
            @ApiResponse(code = 401, message = "Unauthorized", response = TppMessages401.class),
            @ApiResponse(code = 403, message = "Forbidden", response = TppMessages403.class),
            @ApiResponse(code = 404, message = "Not found", response = TppMessages404.class),
            @ApiResponse(code = 405, message = "Method Not Allowed", response = TppMessages405.class),
            @ApiResponse(code = 406, message = "Not Acceptable", response = TppMessages406.class),
            @ApiResponse(code = 408, message = "Request Timeout"),
            @ApiResponse(code = 415, message = "Unsupported Media Type"),
            @ApiResponse(code = 429, message = "Too Many Requests", response = TppMessages429.class),
            @ApiResponse(code = 500, message = "Internal Server Error"),
            @ApiResponse(code = 503, message = "Service Unavailable")})
    @RequestMapping(value = "/v1/{payment-service}/{paymentId}/authorisations/{authorisationId}",
            produces = {"application/json"},
            consumes = {"application/json"},
            method = RequestMethod.PUT)
    ResponseEntity<UpdatePsuAuthenticationResponse> _updatePaymentPsuData(
            @ApiParam(value = "Payment service:  Possible values are: * payments * bulk-payments * periodic-payments ", required = true, allowableValues = "\"payments\", \"bulk-payments\", \"periodic-payments\"") @PathVariable("payment-service") String paymentService,
            @ApiParam(value = "Resource identification of the generated payment initiation resource.", required = true) @PathVariable("paymentId") String paymentId,
            @ApiParam(value = "Resource identification of the related SCA.", required = true) @PathVariable("authorisationId") String authorisationId,
            @ApiParam(value = "ID of the request, unique to the call, as determined by the initiating party.", required = true) @RequestHeader(value = "X-Request-ID", required = true) UUID xRequestID,
            @ApiParam(value = "") @Valid @RequestBody Object body,
            @ApiParam(value = "Is contained if and only if the \"Signature\" element is contained in the header of the request.") @RequestHeader(value = "Digest", required = false) String digest,
            @ApiParam(value = "A signature of the request by the TPP on application level. This might be mandated by ASPSP. ") @RequestHeader(value = "Signature", required = false) String signature,
            @ApiParam(value = "The certificate used for signing the request, in base64 encoding. Must be contained if a signature is contained. ") @RequestHeader(value = "TPP-Signature-Certificate", required = false) byte[] tpPSignatureCertificate,
            @ApiParam(value = "Client ID of the PSU in the ASPSP client interface. Might be mandated in the ASPSP's documentation. Is not contained if an OAuth2 based authentication was performed in a pre-step or an OAuth2 based SCA was performed in an preceeding AIS service in the same session. ") @RequestHeader(value = "PSU-ID", required = false) String PSU_ID,
            @ApiParam(value = "Type of the PSU-ID, needed in scenarios where PSUs have several PSU-IDs as access possibility. ") @RequestHeader(value = "PSU-ID-Type", required = false) String psUIDType,
            @ApiParam(value = "Might be mandated in the ASPSP's documentation. Only used in a corporate context. ") @RequestHeader(value = "PSU-Corporate-ID", required = false) String psUCorporateID,
            @ApiParam(value = "Might be mandated in the ASPSP's documentation. Only used in a corporate context. ") @RequestHeader(value = "PSU-Corporate-ID-Type", required = false) String psUCorporateIDType,
            @ApiParam(value = "The forwarded IP Address header field consists of the corresponding http request IP Address field between PSU and TPP. ") @RequestHeader(value = "PSU-IP-Address", required = false) String psUIPAddress,
            @ApiParam(value = "The forwarded IP Port header field consists of the corresponding HTTP request IP Port field between PSU and TPP, if available. ") @RequestHeader(value = "PSU-IP-Port", required = false) Object psUIPPort,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept", required = false) String psUAccept,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Charset", required = false) String psUAcceptCharset,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Encoding", required = false) String psUAcceptEncoding,
            @ApiParam(value = "The forwarded IP Accept header fields consist of the corresponding HTTP request Accept header fields between PSU and TPP, if available. ") @RequestHeader(value = "PSU-Accept-Language", required = false) String psUAcceptLanguage,
            @ApiParam(value = "The forwarded Agent header field of the HTTP request between PSU and TPP, if available. ") @RequestHeader(value = "PSU-User-Agent", required = false) String psUUserAgent,
            @ApiParam(value = "HTTP method used at the PSU ? TPP interface, if available. Valid values are: * GET * POST * PUT * PATCH * DELETE ", allowableValues = "GET, POST, PUT, PATCH, DELETE") @RequestHeader(value = "PSU-Http-Method", required = false) String psUHttpMethod,
            @ApiParam(value = "UUID (Universally Unique Identifier) for a device, which is used by the PSU, if available. UUID identifies either a device or a device dependant application installation. In case of an installation identification this ID need to be unaltered until removal from device. ") @RequestHeader(value = "PSU-Device-ID", required = false) UUID psUDeviceID,
            @ApiParam(value = "The forwarded Geo Location of the corresponding http request between PSU and TPP if available. ") @RequestHeader(value = "PSU-Geo-Location", required = false) String psUGeoLocation);
}
