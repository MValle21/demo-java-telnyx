package com.main;

import com.google.gson.Gson;
import com.main.model.Dlr;
import com.main.model.PhoneNumberOrderRequest;
import com.main.model.SearchNumbersResponse;
import com.main.model.MessageSendRequest;
import io.github.cdimascio.dotenv.Dotenv;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.Configuration;
import io.swagger.client.api.MessagesApi;
import io.swagger.client.api.NumberOrdersApi;
import io.swagger.client.api.NumberReservationsApi;
import io.swagger.client.api.NumberSearchApi;
import io.swagger.client.model.*;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static spark.Spark.*;


public class TelnyxExample {

    static Dotenv dotenv = Dotenv.load();

    private static final String TELNYX_API_KEY = dotenv.get("TELNYX_API_KEY");
//    private static final String TELNYX_PUBLIC_KEY = dotenv.get("TELNYX_PUBLIC_KEY");
    private static final String TELNYX_APP_PORT = dotenv.get("TELNYX_APP_PORT");
    private static final String WEBHOOK_URL = "http://d461e798f09e.ngrok.io/Callbacks/Messaging/Outbound";

    // Instantiate the client
    static ApiClient defaultClient = Configuration.getDefaultApiClient();

    private static String sendMessage (MessageSendRequest request) {

        MessagesApi apiInstance = new MessagesApi(defaultClient);

        //Create the payload
        NewMessage newMessage = new NewMessage()
                .from(request.from)
                .to(request.to)
                .text(request.text)
                .useProfileWebhooks(false)
                .webhookUrl(WEBHOOK_URL);
        // Send the message
        try {
            CreateMessageResponse result = apiInstance.createMessage(newMessage);
            UUID id = result.getData().getId();
            System.out.printf("Sent message with ID: %s\n", id);
            return new Gson().toJson(result);
        } catch (Exception e) {
            System.err.println("Exception when calling MessagesApi#createLongCodeMessage");
            e.printStackTrace();
            return "{ \"error\": \"Problem sending message\"}";
        }
    }

    private static SearchNumbersResponse searchNumbers(String countryCode, String state, String city){
        NumberSearchApi apiInstance = new NumberSearchApi();
        SearchNumbersResponse numbersResponse = new SearchNumbersResponse();

        try {
            ListAvailablePhoneNumbersResponse availablePhoneNumbers = apiInstance.listAvailablePhoneNumbers(
                    null,
                    null,
                    null,
                    city,
                    state,
                    countryCode,
                    null,
                    null,
                    null,
                    null,
                    2,
                    null,
                    null,
                    null
            );
            numbersResponse.setApiResponse(availablePhoneNumbers);
            numbersResponse.setJson(new Gson().toJson(availablePhoneNumbers));
            numbersResponse.setValid(true);
        } catch (Exception e) {
            System.err.println("Exception when calling NumberSearchApi#listAvailablePhoneNumbers");
            e.printStackTrace();
            numbersResponse.setJson("{ \"error\": \"Problem searching phone numbers\"}");
            numbersResponse.setValid(false);
        }
        return numbersResponse;
    }

    private static String orderNumber(String phoneNumber) {
        NumberOrdersApi apiInstance = new NumberOrdersApi();
        NumberOrder orderRequest = new NumberOrder()
                .addPhoneNumbersItem(new PhoneNumber().phoneNumber(phoneNumber));
        try {
            CreateNumberOrderResponse orderResponse = apiInstance.createNumberOrder(orderRequest);
            return new Gson().toJson(orderResponse);
        } catch (ApiException e) {
            System.err.println("Exception when calling NumberOrdersApi#createNumberOrder");
            e.printStackTrace();
            return e.getResponseBody();
        }
        catch (Exception e) {
            System.err.println("Exception when calling NumberOrdersApi#createNumberOrder");
            e.printStackTrace();
            return String.format(
                    "{ \"error\": \"Problem searching phone numbers\", \"message\":\"%s\"}", e.getLocalizedMessage());
        }
    }

    private static String reserveNumbers(ListAvailablePhoneNumbersResponse apiResponse) {
        NumberReservationsApi apiInstance = new NumberReservationsApi();

        List<ReservedPhoneNumber> numberList = new ArrayList<ReservedPhoneNumber>();
        apiResponse.getData().forEach(availablePhoneNumber -> {
            ReservedPhoneNumber phoneNumber = new ReservedPhoneNumber()
                    .phoneNumber(availablePhoneNumber.getPhoneNumber());
            numberList.add(phoneNumber);
        });
        NumberReservation numberReservationRequest = new NumberReservation()
                .phoneNumbers(numberList);
        try {
            CreateNumberReservationsResponse numberReservations = apiInstance.createNumberReservations(numberReservationRequest);
            return new Gson().toJson(numberReservations);
        } catch (Exception e) {
            System.err.println("Exception when calling NumberReservationsApi#createNumberReservations");
            e.printStackTrace();
            return "{ \"error\": \"Problem reserving phone numbers\"}";
        }
    }


    public static void main(String[] args) {
        assert TELNYX_APP_PORT != null;
        // Instantiate the client
        defaultClient.setAccessToken(TELNYX_API_KEY);
        port(Integer.parseInt(TELNYX_APP_PORT));
        get("/", (req, res) -> "Hello World");

        post("/SendMessage", (req, res) -> {
            String json = req.body();
            MessageSendRequest messageSendRequest = new Gson().fromJson(json, MessageSendRequest.class);
            String result = sendMessage(messageSendRequest);
            res.type("application/json");
            return result;
        });

        post("/Callbacks/Messaging/Outbound", (req, res) -> {
            String json = req.body();
            try {
                Dlr dlr = new Gson().fromJson(json, Dlr.class);
                String id = dlr.getData().getPayload().getId();
                String eventType = dlr.getData().getEventType();
                System.out.printf("Message id: %s Status: %s\n", id, eventType);
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            res.status(200);

            return "";//Just needs an ACK
        });

        get("/availableNumbers", (req, res)-> {
            res.type("application/json");
            SearchNumbersResponse availableNumbers = searchNumbers(
                    req.queryParams("countryCode"),
                    req.queryParams("state"),
                    req.queryParams("city")
            );
            if (Boolean.parseBoolean(req.queryParams("reserve")) && availableNumbers.valid) {
                String reservation = reserveNumbers(availableNumbers.getApiResponse());
                return reservation;
            }
            else {
                return availableNumbers.getJson();
            }
        });

        post("/availableNumbers", (req, res) -> {
            String json = req.body();
            PhoneNumberOrderRequest orderRequest = new Gson().fromJson(json, PhoneNumberOrderRequest.class);
            String result = orderNumber(orderRequest.phoneNumber);
            res.type("application/json");
            return result;
        });

    }


}
