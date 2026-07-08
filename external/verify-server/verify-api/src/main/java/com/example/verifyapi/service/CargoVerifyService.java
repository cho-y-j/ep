package com.example.verifyapi.service;

import com.example.verifyapi.dto.InternalCargoRequest;
import com.example.verifyapi.dto.InternalCargoResponse;
import com.example.verifyapi.provider.CargoLicenseProvider;
import org.springframework.stereotype.Service;

@Service
public class CargoVerifyService {

    private final CargoLicenseProvider cargoLicenseProvider;

    public CargoVerifyService(CargoLicenseProvider cargoLicenseProvider) {
        this.cargoLicenseProvider = cargoLicenseProvider;
    }

    public InternalCargoResponse verify(InternalCargoRequest request) {
        return cargoLicenseProvider.verify(request);
    }
}
