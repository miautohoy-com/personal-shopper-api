package com.techmo.personalshopper.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techmo.personalshopper.dto.ResponseDto;
import com.techmo.personalshopper.dto.infoAuto.*;
import com.techmo.personalshopper.mapper.CarAttributeMapper;
import com.techmo.personalshopper.util.MiscMethods;
import com.techmo.personalshopper.util.ControllerConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.util.*;

import com.auth0.jwt.JWT;

@Service
public class InfoAutoService {

    @Value("${info-auto.username}")
    String username;
    @Value("${info-auto.password}")
    String password;

    ObjectMapper objectMapper;
    CarAttributeMapper carAttributeMapper;
    HttpClient client;
    TokenDto tokens;


    public InfoAutoService(CarAttributeMapper carAttributeMapper) {
        this.carAttributeMapper = carAttributeMapper;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.client = HttpClient.newHttpClient();
        this.tokens = null;

    }

    @Autowired
    public void GetProperties(@Value("${info-auto.username}") String username, @Value("${info-auto.password}") String password) {
        this.username = username;
        this.password = password;
    }

    // Set auth header
    private String getBasicAuthenticationHeader() {
        String valueToEncode = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(valueToEncode.getBytes());
    }

    private TokenDto getNewTokens() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ControllerConstants.INFOAUTO_AUTH_URI + "/login"))
                .header("Authorization", getBasicAuthenticationHeader())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readValue(response.body(), TokenDto.class);
    }

    private String getNewAccessToken() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ControllerConstants.INFOAUTO_AUTH_URI + "/refresh"))
                .header("Authorization", "Bearer " + this.tokens.refreshToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        TokenDto tokensFromDto = objectMapper.readValue(response.body(), TokenDto.class);
        return tokensFromDto.accessToken;
    }

    // This is going to be used
    public void setInfoAutoTokens() throws RuntimeException, IOException, InterruptedException {
        try {
            if (this.tokens != null) {
                if (this.tokens.accessToken != null && this.tokens.refreshToken != null) { // check for null before decoding tokens
                    // validate that access token is expired and refresh token is not expired
                    if (
                            MiscMethods.checkIfJwtIsExpired(JWT.decode(this.tokens.accessToken)) &&
                                    !MiscMethods.checkIfJwtIsExpired(JWT.decode(this.tokens.refreshToken))
                    ) {
                        // get new access token w/ refresh token
                        this.tokens.accessToken = getNewAccessToken();
                        return;
                    }
                }
            }
            // Get new tokens and store them in the service attributes
            this.tokens = getNewTokens();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private <T> Optional<T> sendRequest(HttpRequest request, Class<T> clazz) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404 || response.statusCode() == 500) {
            return Optional.empty();
        } else {
            return Optional.of(objectMapper.readValue(response.body(), clazz));
        }
    }

    public ResponseDto<CarPriceDto[]> getModelBasePrices(Boolean usado, Integer codia) {
        try {
            // Try to get prices with the current access token
            if (this.tokens != null && this.tokens.accessToken != null) {
                String endpoint = ControllerConstants.INFOAUTO_BASE_URI + "/models/" + codia;
                if (usado) {
                    endpoint += "/prices/";
                } else {
                    endpoint += "/list_price";
                }
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Authorization", "Bearer " + this.tokens.accessToken)
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {

                    if (usado) {
                        CarPriceDto[] prices = objectMapper.readValue(response.body(), CarPriceDto[].class);
                        return new ResponseDto<>(HttpStatus.OK, "", prices);
                    } else {
                        CarPriceDto price = objectMapper.readValue(response.body(), CarPriceDto.class);
                        return new ResponseDto<>(HttpStatus.OK, "", new CarPriceDto[] { price });
                    }

                } else {
                    return new ResponseDto<>(HttpStatus.NOT_FOUND, "", null);
                }
            }

            // Try to refresh tokens if the access token has expired
            if (this.tokens != null && MiscMethods.checkIfJwtIsExpired(JWT.decode(this.tokens.accessToken))) {
                this.tokens.accessToken = getNewAccessToken();
            }

            // Try to get new tokens and then get prices with the new access token
            if (this.tokens == null || this.tokens.accessToken == null) {
                setInfoAutoTokens();
            }
            if (this.tokens != null && this.tokens.accessToken != null) {
                return getModelBasePrices(usado, codia);
            } else {
                return new ResponseDto<>(HttpStatus.NOT_FOUND, "", null);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseDto<>(HttpStatus.INTERNAL_SERVER_ERROR, e.toString(), null);
        }
    }


    public ResponseDto<CarDetailsDto> getCarDetails(Integer codia) throws RuntimeException, IOException, InterruptedException {

        try {
            this.setInfoAutoTokens();

            // Create request object
            HttpRequest featuresRequests = HttpRequest.newBuilder().uri(URI.create(ControllerConstants.INFOAUTO_BASE_URI + "/models/" + codia + "/features/")).header("Authorization", "Bearer " + this.tokens.accessToken).GET().build();

            // Make request
            HttpResponse<String> featuresResponse = client.send(featuresRequests, HttpResponse.BodyHandlers.ofString());

            if (featuresResponse.statusCode() == 404 || featuresResponse.statusCode() == 500) {
                return new ResponseDto<>(HttpStatus.NOT_FOUND, "", null);
            } else if (featuresResponse.statusCode() != 200) {
                this.tokens = getNewTokens();
                getCarDetails(codia);
            }
            CarAttributeDto[] detailedCarInfo = objectMapper.readValue(featuresResponse.body(), CarAttributeDto[].class);

            List<SimplifiedCarAttributeDto> comfort = new ArrayList<>();
            List<SimplifiedCarAttributeDto> technicalInfo = new ArrayList<>();
            List<SimplifiedCarAttributeDto> engineAndTransmission = new ArrayList<>();
            List<SimplifiedCarAttributeDto> security = new ArrayList<>();


            for (CarAttributeDto feature : detailedCarInfo) {
                SimplifiedCarAttributeDto carAttr = carAttributeMapper.simpleToAttr(feature);

                if (feature.category.equals("Confort")) {
                    comfort.add(carAttr);
                }
                if (feature.category.equals("Datos técnicos")) {
                    technicalInfo.add(carAttr);
                }
                if (feature.category.equals("Motor y transmisión")) {
                    engineAndTransmission.add(carAttr);
                }
                if (feature.category.equals("Seguridad")) {
                    security.add(carAttr);
                }
            }

            // Create request object
            HttpRequest detailsRequest = HttpRequest.newBuilder().uri(URI.create(ControllerConstants.INFOAUTO_BASE_URI + "/models/" + codia)).header("Authorization", "Bearer " + this.tokens.accessToken).GET().build();

            // Make request
            HttpResponse<String> response = client.send(detailsRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new Exception(response.body());
            }
            ModelDto modelInfo = objectMapper.readValue(response.body(), ModelDto.class);

            CarDetailsDto detailsDto = new CarDetailsDto(modelInfo.brand.name, modelInfo.description, modelInfo.url, comfort, technicalInfo, engineAndTransmission, security);
            return new ResponseDto<>(HttpStatus.ACCEPTED, "", detailsDto);

        } catch (Exception e) {
            return new ResponseDto<>(HttpStatus.INTERNAL_SERVER_ERROR, e.toString(), null);
        }

    }

}
