package screens;

import auth.HqAuth;
import org.commcare.util.cli.QueryScreen;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;

/**
 * Created by willpride on 8/7/16.
 */
public class FormplayerQueryScreen extends QueryScreen {

    HqAuth auth;

    public FormplayerQueryScreen(HqAuth auth){
        super();
        this.auth = auth;
    }

    public String makeQueryRequest() {
        URL url = getBaseUrl();
        Hashtable<String, String> params = getQueryParams();
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url.toString());
        for(String key: params.keySet()){
            builder.queryParam(key, params.get(key));
        }
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response =
                restTemplate.exchange(builder.toUriString(),
                        HttpMethod.GET,
                        new HttpEntity<String>(auth.getAuthHeaders()), String.class);
        return response.getBody();
    }

    @Override
    public InputStream makeQueryRequestReturnStream() {
        String responseString = makeQueryRequest();
        return new ByteArrayInputStream(responseString.getBytes(StandardCharsets.UTF_8));
    }

}