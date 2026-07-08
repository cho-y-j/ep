package com.example.verifyapi.controller;

import com.example.verifyapi.dto.InternalCargoRequest;
import com.example.verifyapi.dto.InternalCargoResponse;
import com.example.verifyapi.service.CargoVerifyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/verify")
public class CargoVerifyController {

    private final CargoVerifyService cargoVerifyService;

    public CargoVerifyController(CargoVerifyService cargoVerifyService) {
        this.cargoVerifyService = cargoVerifyService;
    }

    @PostMapping("/cargo")
    public ResponseEntity<InternalCargoResponse> verifyCargo(@RequestBody InternalCargoRequest request) {
        InternalCargoResponse response = cargoVerifyService.verify(request);
        return ResponseEntity.ok(response);
    }
}
