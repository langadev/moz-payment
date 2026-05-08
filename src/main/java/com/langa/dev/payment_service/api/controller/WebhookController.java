package com.langa.dev.payment_service.api.controller;

import com.langa.dev.payment_service.api.dto.request.WebhookRequest;
import com.langa.dev.payment_service.domain.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks")
@Tag(name = "Webhooks")
public class WebhookController {

    private final PaymentService paymentService;

    public WebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(
        summary = "Callback do provedor",
        description = "Recebe notificações de confirmação ou rejeição do provedor externo (MPesa, e-Mola). " +
                      "Apenas os estados `COMPLETED` e `FAILED` são processados. " +
                      "Outros estados são ignorados com `200 OK`.",
        requestBody = @RequestBody(
            required = true,
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = WebhookRequest.class),
                examples = {
                    @ExampleObject(
                        name = "Pagamento confirmado",
                        summary = "Provedor confirmou o pagamento",
                        value = """
                            {
                              "providerRef": "MOCK-A1B2C3D4",
                              "status": "COMPLETED"
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "Pagamento recusado",
                        summary = "Provedor rejeitou o pagamento",
                        value = """
                            {
                              "providerRef": "MOCK-A1B2C3D4",
                              "status": "FAILED",
                              "failureReason": "Saldo insuficiente"
                            }
                            """
                    )
                }
            )
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Webhook processado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Nenhum pagamento encontrado com o providerRef indicado",
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = """
                        {
                          "type": "about:blank",
                          "title": "Not Found",
                          "status": 404,
                          "detail": "Payment not found: providerRef=MOCK-UNKNOWN"
                        }
                        """)
                )),
            @ApiResponse(responseCode = "422", description = "Dados inválidos",
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
                ))
        }
    )
    @PostMapping("/provider")
    public ResponseEntity<Void> handleProviderWebhook(
            @Valid @org.springframework.web.bind.annotation.RequestBody WebhookRequest request) {
        paymentService.handleWebhook(request);
        return ResponseEntity.ok().build();
    }
}
