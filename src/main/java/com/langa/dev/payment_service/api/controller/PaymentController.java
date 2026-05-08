package com.langa.dev.payment_service.api.controller;

import com.langa.dev.payment_service.api.dto.request.PaymentRequest;
import com.langa.dev.payment_service.api.dto.response.PaymentResponse;
import com.langa.dev.payment_service.domain.service.PaymentService;
import com.langa.dev.payment_service.domain.service.PaymentService.PaymentResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(
        summary = "Criar pagamento",
        description = "Cria um novo pagamento e enfileira para processamento assíncrono. " +
                      "Responde imediatamente com `201 PENDING`. " +
                      "Idempotente: reenviar o mesmo `idempotencyKey` retorna `200` com o pagamento existente.",
        requestBody = @RequestBody(
            required = true,
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = PaymentRequest.class),
                examples = {
                    @ExampleObject(
                        name = "Pagamento MPesa",
                        summary = "Pagamento padrão em meticais",
                        value = """
                            {
                              "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
                              "amount": 150.00,
                              "currency": "MZN",
                              "phoneNumber": "+258840000000",
                              "description": "Pagamento de serviço de água"
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "Pagamento em USD",
                        summary = "Pagamento internacional",
                        value = """
                            {
                              "idempotencyKey": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
                              "amount": 25.00,
                              "currency": "USD",
                              "phoneNumber": "+258870000001",
                              "description": "Subscrição mensal"
                            }
                            """
                    )
                }
            )
        ),
        responses = {
            @ApiResponse(responseCode = "201", description = "Pagamento criado com sucesso",
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = PaymentResponse.class)
                )),
            @ApiResponse(responseCode = "200", description = "idempotencyKey já utilizado — retorna pagamento existente",
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = PaymentResponse.class)
                )),
            @ApiResponse(responseCode = "422", description = "Dados inválidos (campo em falta ou formato errado)",
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = """
                        {
                          "type": "about:blank",
                          "title": "Unprocessable Entity",
                          "status": 422,
                          "detail": "idempotencyKey: must not be blank, currency: size must be between 3 and 3"
                        }
                        """)
                ))
        }
    )
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @org.springframework.web.bind.annotation.RequestBody PaymentRequest request) {
        PaymentResult result = paymentService.createPayment(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(PaymentResponse.from(result.payment()));
    }

    @Operation(
        summary = "Consultar pagamento",
        description = "Retorna os detalhes e o estado actual de um pagamento pelo seu ID.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Pagamento encontrado",
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = PaymentResponse.class)
                )),
            @ApiResponse(responseCode = "404", description = "Pagamento não encontrado",
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = """
                        {
                          "type": "about:blank",
                          "title": "Not Found",
                          "status": 404,
                          "detail": "Payment not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6"
                        }
                        """)
                ))
        }
    )
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(
            @Parameter(description = "UUID do pagamento", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID id) {
        return ResponseEntity.ok(PaymentResponse.from(paymentService.getPayment(id)));
    }
}
