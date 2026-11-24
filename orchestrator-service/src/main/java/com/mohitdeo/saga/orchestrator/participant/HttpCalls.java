package com.mohitdeo.saga.orchestrator.participant;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Sorts HTTP results into Success, Rejected or Transient. This is the participant contract in one place. */
final class HttpCalls {

    private HttpCalls() {
    }

    static StepOutcome post(RestClient client, String uri, Object body, Object... uriVariables) {
        try {
            RestClient.RequestBodySpec request = client.post().uri(uri, uriVariables)
                    .contentType(MediaType.APPLICATION_JSON);
            if (body != null) {
                request.body(body);
            }
            request.retrieve().toBodilessEntity();
            return StepOutcome.success();
        } catch (HttpClientErrorException e) {
            // 408 and 429 mean "try again later"; every other 4xx is a definite business "no"
            return isRetryable(e.getStatusCode())
                    ? StepOutcome.transientFailure(describe(e))
                    : StepOutcome.rejected(describe(e));
        } catch (HttpServerErrorException e) {
            return StepOutcome.transientFailure(describe(e));
        } catch (ResourceAccessException e) {
            // Connect/read timeout or connection refused: the request may or may not have been applied
            return StepOutcome.transientFailure("I/O error: " + e.getMessage());
        } catch (RestClientException e) {
            return StepOutcome.transientFailure("Unexpected response: " + e.getMessage());
        }
    }

    private static boolean isRetryable(HttpStatusCode status) {
        return status.value() == 408 || status.value() == 429;
    }

    private static String describe(RestClientResponseException e) {
        String status = String.valueOf(e.getStatusCode().value());
        try {
            ProblemDetail problem = e.getResponseBodyAs(ProblemDetail.class);
            if (problem != null && problem.getDetail() != null) {
                return status + " " + problem.getDetail();
            }
        } catch (RuntimeException ignored) {
            // response body was not a problem detail; fall through
        }
        String body = e.getResponseBodyAsString();
        return body.isBlank() ? status : status + " " + body.substring(0, Math.min(body.length(), 200));
    }
}
