package com.mah.personalshopper.controller;

import com.mah.personalshopper.dto.OwnerDto;
import com.mah.personalshopper.dto.ResponseDto;
import com.mah.personalshopper.dto.VehicleDto;
import com.mah.personalshopper.service.VehicleService;
import com.mah.personalshopper.util.ControllerConstants;

import io.swagger.annotations.ApiOperation;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ControllerConstants.VEHICLE)
public class VehicleController {
    private final VehicleService service;

    public VehicleController(VehicleService service) {
        this.service = service;
    }

    @ApiOperation(
            value = "Persists a new vehicle"
    )
    @PostMapping("")
    public ResponseEntity<ResponseDto<VehicleDto>> addNewVehicle(@RequestBody VehicleDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createVehicle(dto));
    }

    @PostMapping("/{vehicleId}/owner")
    public ResponseEntity<ResponseDto<VehicleDto>> addOwner(@PathVariable Long vehicleId,
                                                            @RequestBody OwnerDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addVehicleOwner(vehicleId, dto));
    }

}
