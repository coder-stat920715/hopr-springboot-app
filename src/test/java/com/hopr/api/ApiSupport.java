package com.hopr.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Locale;

/** Small MockMvc helper shared by the API integration tests. */
final class ApiSupport {

    static final String RAVI_HOME = "{\"label\":\"Miyapur\",\"lat\":17.4968,\"lng\":78.3614}";
    static final String ARJUN_HOME = "{\"label\":\"Kondapur\",\"lat\":17.4600,\"lng\":78.3560}";
    static final String CAPGEMINI = "{\"label\":\"Capgemini\",\"lat\":17.4170,\"lng\":78.3430}";

    private final MockMvc mvc;
    private final ObjectMapper mapper;

    ApiSupport(MockMvc mvc, ObjectMapper mapper) {
        this.mvc = mvc;
        this.mapper = mapper;
    }

    String login(String phone) throws Exception {
        return TestAuth.bearer(TestAuth.login(mvc, mapper, phone));
    }

    ResultActions call(HttpMethod method, String path, String auth, String body) throws Exception {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.request(method, path);
        if (auth != null) {
            b.header("Authorization", auth);
        }
        if (body != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(b);
    }

    ResultActions get(String path, String auth) throws Exception {
        return call(HttpMethod.GET, path, auth, null);
    }

    ResultActions post(String path, String auth, String body) throws Exception {
        return call(HttpMethod.POST, path, auth, body);
    }

    ResultActions put(String path, String auth, String body) throws Exception {
        return call(HttpMethod.PUT, path, auth, body);
    }

    ResultActions patch(String path, String auth, String body) throws Exception {
        return call(HttpMethod.PATCH, path, auth, body);
    }

    ResultActions delete(String path, String auth) throws Exception {
        return call(HttpMethod.DELETE, path, auth, null);
    }

    JsonNode read(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    long id(ResultActions r) throws Exception {
        return read(r).get("id").asLong();
    }

    static String rideJson(long vehicleId, String date, String time, int seats) {
        return String.format(Locale.ROOT, "{\"direction\":\"HOME_TO_OFFICE\",\"vehicleId\":%d,\"start\":%s,\"end\":%s,"
                + "\"date\":\"%s\",\"departureTime\":\"%s\",\"seats\":%d}", vehicleId, RAVI_HOME, CAPGEMINI, date, time, seats);
    }

    static String bookingJson(long rideId) {
        return String.format(Locale.ROOT, "{\"rideId\":%d,\"pickup\":%s,\"drop\":%s}", rideId, ARJUN_HOME, CAPGEMINI);
    }

    static String respondJson(String action) {
        return "{\"action\":\"" + action + "\"}";
    }
}
